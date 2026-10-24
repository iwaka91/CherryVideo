"""Independent MP4 evidence. Nominal fps is never treated as proof of actual cadence."""
import json
import pathlib
import subprocess
import sys

root = pathlib.Path(sys.argv[1])
videos = sorted(root.glob("*.mp4"))
assert len(videos) >= 3, "Missing real-device-test recording evidence"
reports = []
for video in videos:
    data = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_streams",
        "-show_entries", "frame=best_effort_timestamp_time", "-show_frames", "-of", "json", str(video)
    ]))
    streams = data["streams"]
    assert len(streams) == 1 and streams[0]["codec_type"] == "video", video
    times = [float(f["best_effort_timestamp_time"]) for f in data["frames"]]
    assert len(times) >= 12 and all(b > a for a, b in zip(times, times[1:])), video
    assert streams[0]["width"] > 0 and streams[0]["height"] > 0
    fps = (len(times) - 1) / (times[-1] - times[0])
    reports.append({"file": video.name, "width": streams[0]["width"], "height": streams[0]["height"],
                    "frames": len(times), "actual_fps": fps,
                    "max_gap_ms": 1000 * max(b-a for a,b in zip(times,times[1:])),
                    "scope": "Emulator functional evidence; no Xiaomi performance claim"})
(root / "ffprobe-report.json").write_text(json.dumps(reports, indent=2))
print(json.dumps(reports, indent=2))
