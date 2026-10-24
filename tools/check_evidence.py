"""Independent presentation timing, A/V alignment, duration and moving decoded pixels."""
import json
import pathlib
import subprocess
import sys


def timeline(times, streams, duration):
    assert len(times) >= 12 and all(b > a for a, b in zip(times, times[1:])), "Missing/non-monotonic video frames"
    assert 0 <= times[0] <= 0.5, f"Video starts at {times[0]} seconds"
    video = next(s for s in streams if s["codec_type"] == "video")
    assert video["width"] > 0 and video["height"] > 0
    ends = [times[-1]]
    for audio in (s for s in streams if s["codec_type"] == "audio"):
        start = float(audio.get("start_time", 0))
        end = start + float(audio["duration"])
        assert abs(start-times[0]) <= 0.5, "Audio/video start mismatch"
        assert abs(end-times[-1]) <= 1.0, "Audio/video duration mismatch"
        ends.append(end)
    assert 0 < duration <= max(ends)+1.0, "Container duration disagrees with frames"
    return video


def self_test():
    streams = [{"codec_type": "video", "width": 1920, "height": 1080},
               {"codec_type": "audio", "start_time": "0", "duration": "8.234667"}]
    times = [i/30 for i in range(256)]
    timeline(times, streams, 8.534)
    cases = [([t+15070.3593 for t in times], streams, 15078.890822),
             (times, streams, 15078.890822),
             (times, [streams[0], {**streams[1], "start_time": "2"}], 10.3)]
    for case in cases:
        try:
            timeline(*case)
        except AssertionError:
            continue
        raise AssertionError("The Xiaomi clock regression must fail qualification")
    print("Timeline regression: valid clip passes; 3 clock/A-V/container defects rejected")


def inspect(video):
    info = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-show_streams", "-show_format", "-of", "json", str(video)]))
    frames = json.loads(subprocess.check_output([
        "ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries",
        "frame=best_effort_timestamp_time", "-show_frames", "-of", "json", str(video)]))
    times = [float(f["best_effort_timestamp_time"]) for f in frames["frames"]]
    duration = float(info["format"]["duration"])
    stream = timeline(times, info["streams"], duration)
    raw = subprocess.check_output([
        "ffmpeg", "-v", "error", "-i", str(video), "-map", "0:v:0", "-vf", "scale=64:36,format=gray",
        "-fps_mode", "passthrough", "-f", "rawvideo", "-pix_fmt", "gray", "-"])
    pixels = [raw[i:i+2304] for i in range(0, len(raw), 2304)]
    assert len(pixels) >= 12 and all(len(p) == 2304 for p in pixels)
    differences = [sum(abs(a-b) for a,b in zip(pixels[0],p))/2304 for p in pixels[1:]]
    assert max(differences) > 1.0, f"Decoded video shows no meaningful motion: {video}"
    return {"file": str(video), "width": stream["width"], "height": stream["height"],
            "frames": len(times), "actual_fps": (len(times)-1)/(times[-1]-times[0]),
            "first_video_pts": times[0], "container_duration": duration,
            "max_gap_ms": 1000*max(b-a for a,b in zip(times,times[1:])),
            "max_pixel_change": max(differences),
            "scope": "Emulator functional evidence; no Xiaomi performance claim"}


if __name__ == "__main__":
    if sys.argv[1] == "--self-test":
        self_test()
    else:
        root = pathlib.Path(sys.argv[1])
        videos = sorted(root.rglob("*.mp4"))
        assert len(videos) >= 4, "Missing recording evidence"
        reports = [inspect(video) for video in videos]
        (root/"ffprobe-report.json").write_text(json.dumps(reports, indent=2))
        print(json.dumps(reports, indent=2))
