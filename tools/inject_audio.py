"""Feed an 880 Hz PCM reference through the emulator's documented gRPC microphone.

Uses the emulator_controller.proto bundled with the installed Android SDK:
https://github.com/google/android-emulator-webrtc/blob/master/proto/emulator_controller.proto
No host audio device or PulseAudio driver is used. Test fixture only.
"""
import array
import math
import pathlib
import sys
import time

import grpc
import grpc_tools
from grpc_tools import protoc


def main():
    sdk_lib = pathlib.Path(sys.argv[1]).resolve()
    generated = pathlib.Path(sys.argv[2]).resolve()
    includes = pathlib.Path(grpc_tools.__file__).parent / "_proto"
    result = protoc.main(["protoc", f"-I{sdk_lib}", f"-I{includes}",
                          f"--python_out={generated}", f"--grpc_python_out={generated}",
                          str(sdk_lib / "emulator_controller.proto")])
    assert result == 0, "Cannot compile the installed emulator protocol"
    sys.path.insert(0, str(generated))
    import emulator_controller_pb2 as pb
    import emulator_controller_pb2_grpc as rpc

    channel = grpc.insecure_channel("127.0.0.1:8554")
    grpc.channel_ready_future(channel).result(timeout=20)
    controller = rpc.EmulatorControllerStub(channel)
    controller.setMicrophoneState(pb.MicrophoneState(realAudioEnabled=False), timeout=10)
    fmt = pb.AudioFormat(samplingRate=48000, channels=pb.AudioFormat.Mono,
                         format=pb.AudioFormat.AUD_FMT_S16, mode=pb.AudioFormat.MODE_REAL_TIME)
    # 50 ms contains exactly 44 cycles, so repeating the packet remains continuous.
    pcm = array.array("h", (int(4096*math.sin(2*math.pi*880*n/48000)) for n in range(2400)))
    if sys.byteorder != "little":
        pcm.byteswap()
    samples = pcm.tobytes()

    def packets():
        while True:
            yield pb.AudioPacket(format=fmt, timestamp=time.time_ns()//1000, audio=samples)
            time.sleep(.05)

    future = controller.injectAudio.future(packets())
    print("gRPC virtual microphone: 48 kHz mono S16, continuous 880 Hz reference", flush=True)
    while True:
        try:
            future.result(timeout=1)
            raise RuntimeError("Microphone injection ended unexpectedly")
        except grpc.FutureTimeoutError:
            pass


if __name__ == "__main__":
    main()
