#!/usr/bin/env python3
"""Generate a visual HTML test report from Maven Surefire XML files."""

from __future__ import annotations

import html
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SUREFIRE_DIR = ROOT / "target" / "surefire-reports"
OUT_DIR = ROOT / "target" / "test-report"
OUT_FILE = OUT_DIR / "index.html"


def to_int(value: str | None) -> int:
    try:
        return int(float(value or "0"))
    except ValueError:
        return 0


def parse_reports() -> tuple[dict, list[dict]]:
    totals = {"tests": 0, "failures": 0, "errors": 0, "skipped": 0}
    suites: list[dict] = []

    for xml_file in sorted(SUREFIRE_DIR.glob("TEST-*.xml")):
        root = ET.parse(xml_file).getroot()
        suite = {
            "name": root.attrib.get("name", xml_file.stem),
            "tests": to_int(root.attrib.get("tests")),
            "failures": to_int(root.attrib.get("failures")),
            "errors": to_int(root.attrib.get("errors")),
            "skipped": to_int(root.attrib.get("skipped")),
            "time": root.attrib.get("time", "0"),
        }
        suites.append(suite)
        for key in totals:
            totals[key] += suite[key]

    return totals, suites


def pct(part: int, total: int) -> float:
    return 0.0 if total == 0 else round((part / total) * 100, 1)


def build_html(totals: dict, suites: list[dict]) -> str:
    passed = max(totals["tests"] - totals["failures"] - totals["errors"] - totals["skipped"], 0)
    total = totals["tests"]

    bars = [
        ("Passed", passed, "#16a34a"),
        ("Failures", totals["failures"], "#dc2626"),
        ("Errors", totals["errors"], "#ea580c"),
        ("Skipped", totals["skipped"], "#64748b"),
    ]

    bar_html = []
    for label, value, color in bars:
        width = pct(value, total)
        bar_html.append(
            f"""
            <div class="bar-row">
              <div class="bar-label">{html.escape(label)} ({value})</div>
              <div class="bar-track"><div class="bar-fill" style="width:{width}%;background:{color};"></div></div>
              <div class="bar-pct">{width}%</div>
            </div>
            """
        )

    suite_rows = []
    for suite in suites:
        suite_passed = max(suite["tests"] - suite["failures"] - suite["errors"] - suite["skipped"], 0)
        suite_rows.append(
            f"""
            <tr>
              <td>{html.escape(suite["name"])}</td>
              <td>{suite["tests"]}</td>
              <td>{suite_passed}</td>
              <td>{suite["failures"]}</td>
              <td>{suite["errors"]}</td>
              <td>{suite["skipped"]}</td>
              <td>{html.escape(str(suite["time"]))}s</td>
            </tr>
            """
        )

    return f"""<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"/>
  <meta name="viewport" content="width=device-width, initial-scale=1"/>
  <title>Backend Test Report</title>
  <style>
    body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; margin: 24px; color: #0f172a; }}
    h1 {{ margin: 0 0 6px 0; }}
    .muted {{ color: #475569; margin-bottom: 20px; }}
    .grid {{ display: grid; grid-template-columns: repeat(4, minmax(130px, 1fr)); gap: 12px; margin-bottom: 20px; }}
    .card {{ border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; background: #f8fafc; }}
    .card .k {{ color: #475569; font-size: 12px; }}
    .card .v {{ font-size: 24px; font-weight: 700; }}
    .bars {{ border: 1px solid #e2e8f0; border-radius: 10px; padding: 12px; margin-bottom: 20px; }}
    .bar-row {{ display: grid; grid-template-columns: 150px 1fr 70px; align-items: center; gap: 10px; margin: 10px 0; }}
    .bar-track {{ width: 100%; height: 14px; background: #e2e8f0; border-radius: 999px; overflow: hidden; }}
    .bar-fill {{ height: 100%; }}
    table {{ border-collapse: collapse; width: 100%; font-size: 14px; }}
    th, td {{ border: 1px solid #e2e8f0; padding: 8px 10px; text-align: left; }}
    th {{ background: #f1f5f9; }}
  </style>
</head>
<body>
  <h1>Backend Test Report</h1>
  <div class="muted">Generated from Maven Surefire XML in <code>target/surefire-reports</code>.</div>

  <div class="grid">
    <div class="card"><div class="k">Total Tests</div><div class="v">{total}</div></div>
    <div class="card"><div class="k">Passed</div><div class="v">{passed}</div></div>
    <div class="card"><div class="k">Failures</div><div class="v">{totals["failures"]}</div></div>
    <div class="card"><div class="k">Errors</div><div class="v">{totals["errors"]}</div></div>
  </div>

  <div class="bars">
    <h3>Result Distribution</h3>
    {''.join(bar_html)}
  </div>

  <h3>Suite Breakdown</h3>
  <table>
    <thead>
      <tr>
        <th>Suite</th>
        <th>Tests</th>
        <th>Passed</th>
        <th>Failures</th>
        <th>Errors</th>
        <th>Skipped</th>
        <th>Time</th>
      </tr>
    </thead>
    <tbody>
      {''.join(suite_rows) if suite_rows else '<tr><td colspan="7">No surefire XML files found.</td></tr>'}
    </tbody>
  </table>
</body>
</html>"""


def main() -> None:
    totals, suites = parse_reports()
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    OUT_FILE.write_text(build_html(totals, suites), encoding="utf-8")
    print(f"Generated report: {OUT_FILE}")


if __name__ == "__main__":
    main()
