#!/usr/bin/env python3
"""Print failed instrumented tests as GitHub annotations and a step summary (so CI failures are readable)."""
import glob, os, sys, xml.etree.ElementTree as ET
root = sys.argv[1] if len(sys.argv) > 1 else "app/build"
lines = []
for path in glob.glob(os.path.join(root, "**", "TEST-*.xml"), recursive=True):
    try:
        suite = ET.parse(path).getroot()
    except Exception as e:
        lines.append(("?", f"cannot read {path}: {e}")); continue
    for case in suite.iter("testcase"):
        for kind in ("failure", "error"):
            for node in case.findall(kind):
                msg = (node.get("message") or node.text or "").strip().replace("\r", " ")
                first = " | ".join(l.strip() for l in (node.text or msg).splitlines()[:6])
                lines.append((f"{case.get('classname','')}.{case.get('name','')}", first[:900]))
if not lines:
    print("::warning::Tidak ada berkas hasil uji yang gagal ditemukan (kemungkinan crash/instrumentasi berhenti).")
for name, msg in lines[:12]:
    print(f"::error title=Uji gagal: {name}::{msg.replace('%','%25').replace(chr(10),'%0A')}")
summary = os.environ.get("GITHUB_STEP_SUMMARY")
if summary and lines:
    with open(summary, "a") as f:
        f.write("## Uji emulator yang gagal\n")
        for name, msg in lines: f.write(f"- `{name}`: {msg}\n")
