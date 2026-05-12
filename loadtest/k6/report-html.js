/**
 * Builds a self-contained HTML file with Chart.js (CDN) for opening in a browser after a run.
 * @param {object} payload - slim metrics from reserve.js
 * @returns {string}
 */
export function buildHtmlReport(payload) {
  const json = JSON.stringify(payload)
    .replace(/</g, "\\u003c")
    .replace(/>/g, "\\u003e")
    .replace(/\u2028/g, "\\u2028")
    .replace(/\u2029/g, "\\u2029");

  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>k6 reserve load test report</title>
  <style>
    :root { color-scheme: light dark; }
    body { font-family: system-ui, sans-serif; margin: 0; padding: 1.25rem; max-width: 1100px; margin-inline: auto; }
    h1 { font-size: 1.35rem; margin-top: 0; }
    .meta { color: #666; font-size: 0.9rem; margin-bottom: 1.5rem; line-height: 1.5; }
    .grid { display: grid; gap: 1.5rem; }
    @media (min-width: 800px) { .grid { grid-template-columns: 1fr 1fr; } }
    .card { border: 1px solid #ccc; border-radius: 8px; padding: 1rem; background: rgba(128,128,128,0.06); }
    .card h2 { font-size: 1rem; margin: 0 0 0.75rem; }
    canvas { max-height: 280px; }
    .full { grid-column: 1 / -1; }
  </style>
</head>
<body>
  <h1>Reserve API — load test report</h1>
  <p class="meta" id="meta"></p>
  <div class="grid">
    <div class="card full"><h2>HTTP request duration (ms)</h2><canvas id="chartDuration"></canvas></div>
    <div class="card"><h2>Requests vs rate limit / circuit</h2><canvas id="chartCounts"></canvas></div>
    <div class="card"><h2>Checks &amp; request failures</h2><canvas id="chartChecks"></canvas></div>
  </div>
  <script src="https://cdn.jsdelivr.net/npm/chart.js@4.4.1/dist/chart.umd.min.js"></script>
  <script>
  const P = ${json};
  (function () {
    const s = P.scenario || {};
    document.getElementById("meta").textContent =
      "BASE_URL: " + (s.baseUrl || "—") +
      " · peak VUs: " + (s.target ?? "—") +
      " · ramp: " + (s.ramp || "—") +
      " · hold: " + (s.hold || "—") +
      " · total HTTP reqs: " + (P.httpReqs ?? "—");

    const d = P.duration || {};
    const durLabels = ["avg", "med", "p90", "p95", "max"];
    const durVals = [d.avg, d.med, d.p90, d.p95, d.max].map(function (x) {
      return typeof x === "number" && !isNaN(x) ? x : 0;
    });
    new Chart(document.getElementById("chartDuration"), {
      type: "bar",
      data: {
        labels: durLabels,
        datasets: [{ label: "ms", data: durVals, backgroundColor: "rgba(54, 162, 235, 0.6)" }]
      },
      options: {
        responsive: true,
        plugins: { legend: { display: false } },
        scales: { y: { beginAtZero: true } }
      }
    });

    const total = Number(P.httpReqs) || 0;
    const n429 = Number(P.r429) || 0;
    const n503 = Number(P.r503) || 0;
    const other = Math.max(0, total - n429 - n503);
    new Chart(document.getElementById("chartCounts"), {
      type: "doughnut",
      data: {
        labels: ["Other responses", "429 rate limit", "503 circuit open"],
        datasets: [{
          data: [other, n429, n503],
          backgroundColor: [
            "rgba(75, 192, 192, 0.7)",
            "rgba(255, 206, 86, 0.85)",
            "rgba(255, 99, 132, 0.85)"
          ]
        }]
      },
      options: { responsive: true, plugins: { legend: { position: "bottom" } } }
    });

    const cp = P.checkPasses;
    const cf = P.checkFails;
    let labels, data, colors;
    if (typeof cp === "number" && typeof cf === "number" && (cp + cf) > 0) {
      labels = ["Check passes", "Check fails"];
      data = [cp, cf];
      colors = ["rgba(46, 204, 113, 0.75)", "rgba(231, 76, 60, 0.75)"];
    } else if (typeof P.httpFailedRate === "number") {
      const fr = Math.min(1, Math.max(0, P.httpFailedRate));
      labels = ["HTTP req (not failed)", "HTTP req failed (k6)"];
      data = [(1 - fr) * 100, fr * 100];
      colors = ["rgba(46, 204, 113, 0.75)", "rgba(231, 76, 60, 0.75)"];
    } else {
      labels = ["No data"];
      data = [1];
      colors = ["rgba(200,200,200,0.5)"];
    }
    new Chart(document.getElementById("chartChecks"), {
      type: "pie",
      data: { labels: labels, datasets: [{ data: data, backgroundColor: colors }] },
      options: {
        responsive: true,
        plugins: {
          legend: { position: "bottom" },
          tooltip: {
            callbacks: {
              label: function (ctx) {
                if (typeof P.httpFailedRate === "number" && labels[0] === "HTTP req (not failed)") {
                  return ctx.label + ": " + ctx.raw.toFixed(1) + "%";
                }
                return ctx.label + ": " + ctx.raw;
              }
            }
          }
        }
      }
    });
  })();
  </script>
</body>
</html>
`;
}
