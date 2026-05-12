import http from "k6/http";
import { check, sleep } from "k6";
import { Counter, Trend } from "k6/metrics";
import { buildHtmlReport } from "./report-html.js";

const BASE_URL = __ENV.BASE_URL || "http://localhost:8080";

/** Unique order id per iteration so hits are real creates, not idempotent replays. */
function orderId() {
  return `k6-${__VU}-${__ITER}-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
}

const skus = (__ENV.SKUS || "A100,B200,C300,D400,E500").split(",").map((s) => s.trim());

const reserveLatency = new Trend("reserve_latency_ms", true);
const status429 = new Counter("http_429_rate_limit");
const status503 = new Counter("http_503_circuit_open");

const target = Number(__ENV.K6_TARGET_VUS || 100);
const ramp = __ENV.K6_RAMP || "1m";
const hold = __ENV.K6_HOLD || "3m";

export const options = {
  scenarios: {
    reserve: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: ramp, target },
        { duration: hold, target },
        { duration: ramp, target: 0 },
      ],
      gracefulRampDown: "30s",
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.99"],
    http_req_duration: ["p(95)<5000"],
  },
};

export function setup() {
  if (__ENV.SKIP_SETUP === "1") {
    return;
  }
  const url = `${BASE_URL}/actuator/health`;
  const res = http.get(url);
  if (res.status === 200) {
    return;
  }
  // status 0 = no HTTP response (TCP/TLS/DNS failure). body is often null.
  const transportErr = res.error ? String(res.error) : "";
  const bodySnippet = res.body ? String(res.body).slice(0, 300) : "";
  const hint =
    res.status === 0
      ? [
          transportErr || "No HTTP status (request did not complete).",
          "Typical causes: API not running, wrong BASE_URL, or Docker cannot reach host localhost.",
          "From k6 in Docker on Mac/Windows, use BASE_URL=http://host.docker.internal:<port>",
          "Linux Docker to host: BASE_URL=http://172.17.0.1:<port> or host-gateway.",
        ].join("\n  ")
      : `HTTP ${res.status} ${bodySnippet}`;
  throw new Error(`Health check failed GET ${url}\n  ${hint}`);
}

function pickSku() {
  return skus[Math.floor(Math.random() * skus.length)];
}

export default function () {
  const body = JSON.stringify({
    orderId: orderId(),
    items: [{ sku: pickSku(), quantity: Number(__ENV.ITEM_QTY || 1) }],
  });

  const res = http.post(`${BASE_URL}/reservations`, body, {
    headers: { "Content-Type": "application/json" },
    tags: { name: "POST /reservations" },
  });

  reserveLatency.add(res.timings.duration);

  if (res.status === 429) {
    status429.add(1);
  }
  if (res.status === 503) {
    status503.add(1);
  }

  const ok =
    res.status === 201 ||
    res.status === 200 ||
    res.status === 409 ||
    res.status === 429 ||
    res.status === 503;

  check(res, {
    "created / replay / conflict / rate-limit / circuit": (r) => ok,
  });

  sleep(Number(__ENV.THINK_MS || 0) / 1000);
}

function metricValues(m, name) {
  const metric = m[name];
  if (!metric || !metric.values) {
    return {};
  }
  return metric.values;
}

function extractReportPayload(data) {
  const m = data.metrics || {};
  const d = metricValues(m, "http_req_duration");
  const reqs = metricValues(m, "http_reqs");
  const c429 = metricValues(m, "http_429_rate_limit");
  const c503 = metricValues(m, "http_503_circuit_open");
  const failed = metricValues(m, "http_req_failed");
  const checks = metricValues(m, "checks");
  return {
    scenario: { baseUrl: BASE_URL, target: target, ramp: ramp, hold: hold },
    httpReqs: reqs.count != null ? reqs.count : 0,
    duration: {
      avg: d.avg,
      min: d.min,
      med: d.med,
      max: d.max,
      p90: d["p(90)"],
      p95: d["p(95)"],
    },
    httpFailedRate: failed.rate != null ? failed.rate : null,
    r429: c429.count != null ? c429.count : 0,
    r503: c503.count != null ? c503.count : 0,
    checkPasses: checks.passes,
    checkFails: checks.fails,
  };
}

export function handleSummary(data) {
  const ts = new Date().toISOString().replace(/[:.]/g, "-");
  const payload = extractReportPayload(data);
  const html = buildHtmlReport(payload);
  const slimJson = JSON.stringify(payload, null, 2);

  const out = {
    stdout: textSummary(data) + "\n  HTML report: reports/k6-report-latest.html\n",
    "reports/k6-report-latest.html": html,
    "reports/summary-latest.json": slimJson,
    [`reports/k6-report-${ts}.html`]: html,
    [`reports/summary-${ts}.json`]: slimJson,
  };

  if (__ENV.K6_EXPORT_FULL_SUMMARY === "1") {
    out[`reports/full-summary-${ts}.json`] = JSON.stringify(data, null, 2);
  }

  return out;
}

function textSummary(data) {
  const lines = [];
  lines.push("");
  lines.push("Reserve load test summary");
  lines.push(`  BASE_URL=${BASE_URL}`);
  lines.push(`  peak VUs=${target}, ramp=${ramp}, hold=${hold}`);
  lines.push("");
  const m = data.metrics;
  if (m.http_reqs?.values?.count != null) {
    lines.push(`  http_reqs: ${m.http_reqs.values.count}`);
  }
  if (m.http_req_failed?.values?.rate != null) {
    lines.push(`  http_req_failed rate: ${(m.http_req_failed.values.rate * 100).toFixed(2)}%`);
  }
  if (m.http_req_duration?.values) {
    const v = m.http_req_duration.values;
    lines.push(`  http_req_duration avg: ${v.avg?.toFixed?.(1) ?? "?"} ms`);
    lines.push(`  http_req_duration p(95): ${v["p(95)"]?.toFixed?.(1) ?? "?"} ms`);
  }
  if (m.http_429_rate_limit?.values?.count != null) {
    lines.push(`  429 (rate limit): ${m.http_429_rate_limit.values.count}`);
  }
  if (m.http_503_circuit_open?.values?.count != null) {
    lines.push(`  503 (circuit open): ${m.http_503_circuit_open.values.count}`);
  }
  lines.push("");
  return lines.join("\n");
}
