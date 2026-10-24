"""Host regression for the exact SpeexDSP adapter used in the Android build.
Measures stationary noise and verifies stereo independence, signal retention,
and the 480-sample WOLA delay/tail. Does not certify physical mic acoustics.
"""
import argparse, ctypes, json
import numpy as np


def setup(path):
    lib=ctypes.CDLL(path)
    lib.cherry_denoiser_create.argtypes=[ctypes.c_int,ctypes.c_int]
    lib.cherry_denoiser_create.restype=ctypes.c_void_p
    lib.cherry_denoiser_process.argtypes=[ctypes.c_void_p,ctypes.c_void_p,ctypes.c_int]
    lib.cherry_denoiser_process.restype=ctypes.c_int
    lib.cherry_denoiser_destroy.argtypes=[ctypes.c_void_p]
    return lib


def process(lib,pcm,db):
    pcm=np.asarray(pcm,dtype=np.int16)
    state=lib.cherry_denoiser_create(pcm.shape[1],db)
    assert state
    padded=np.pad(pcm,((0,(-len(pcm))%480+480),(0,0)))
    result=[]
    try:
        for start in range(0,len(padded),480):
            frame=np.ascontiguousarray(padded[start:start+480])
            assert lib.cherry_denoiser_process(state,frame.ctypes.data,480)==1
            result.append(frame.copy())
    finally:
        lib.cherry_denoiser_destroy(state)
    # Recorder compensates one frame in PTS and drains one tail frame.
    return np.concatenate(result)[480:480+len(pcm)]


def rms(a):
    return float(np.sqrt(np.mean(np.asarray(a,dtype=np.float64)**2)))


def check(lib):
    rng=np.random.default_rng(2026)
    noise=rng.normal(0,350,(48000*4,2)).astype(np.int16)
    clear=process(lib,noise,-12);strong=process(lib,noise,-28)
    noise_reduction=[20*np.log10(rms(noise[48000:])/rms(out[48000:])) for out in [clear,strong]]
    assert noise_reduction[0]>4 and noise_reduction[1]>noise_reduction[0]+4, noise_reduction
    isolated=noise.copy();isolated[:,1]=0
    assert not process(lib,isolated,-28)[:,1].any(), 'Stereo channels leaked into one another'
    assert not process(lib,np.zeros((48000,2),dtype=np.int16),-28).any()
    # A short, high-SNR harmonic burst after adaptation must survive with its timing.
    t=np.arange(48000)/48000
    burst=(10000*(np.sin(2*np.pi*220*t)+.3*np.sin(2*np.pi*660*t))).astype(np.int16)
    voice=np.pad(burst, (48000*2,48000))[:,None]
    for db in [-12,-28]:
        output=process(lib,voice,db)
        ratio=rms(output[48000*2+480:48000*3-480])/rms(voice[48000*2+480:48000*3-480])
        assert .3<ratio<1.2, ('Signal removed/amplified',db,ratio)
        # Impulse close to the very end proves both latency correction and tail flush.
        impulse=np.zeros((48000,1),dtype=np.int16);impulse[-50,0]=20000
        response=process(lib,impulse,db)
        assert abs(int(np.argmax(abs(response[:,0].astype(np.int32))))-(len(impulse)-50))<=1
    return {'clear_noise_reduction_db':round(noise_reduction[0],2),
            'strong_noise_reduction_db':round(noise_reduction[1],2),
            'stereo_isolation':'passed','signal_retention':'passed','latency_and_tail':'passed',
            'scope':'Synthetic regression; speech listening and physical-device performance still required'}


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('library');args=parser.parse_args()
    print(json.dumps(check(setup(args.library)),indent=2))
