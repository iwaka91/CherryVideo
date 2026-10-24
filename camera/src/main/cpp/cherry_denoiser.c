/* Cherry adapter for SpeexDSP. Each stereo channel has independent filter state. */
#include "cherry_denoiser.h"
#include "speex/speex_preprocess.h"
#include <stdlib.h>
#define FRAME 480
struct Processor { int channels; SpeexPreprocessState *state[2]; int16_t scratch[FRAME]; };
void cherry_denoiser_destroy(void *ptr) {
    struct Processor *p=ptr;
    if (!p) return;
    for(int c=0;c<p->channels;c++) if(p->state[c]) speex_preprocess_state_destroy(p->state[c]);
    free(p);
}
void *cherry_denoiser_create(int channels,int attenuation_db) {
    if(channels<1 || channels>2 || attenuation_db>0 || attenuation_db < -60) return NULL;
    struct Processor *p=calloc(1,sizeof(*p)); if(!p) return NULL; p->channels=channels;
    for(int c=0;c<channels;c++) {
        p->state[c]=speex_preprocess_state_init(FRAME,48000);
        if(!p->state[c]) { cherry_denoiser_destroy(p); return NULL; }
        int on=1,off=0;
        speex_preprocess_ctl(p->state[c],SPEEX_PREPROCESS_SET_DENOISE,&on);
        speex_preprocess_ctl(p->state[c],SPEEX_PREPROCESS_SET_AGC,&off);
        speex_preprocess_ctl(p->state[c],SPEEX_PREPROCESS_SET_DEREVERB,&off);
        speex_preprocess_ctl(p->state[c],SPEEX_PREPROCESS_SET_NOISE_SUPPRESS,&attenuation_db);
    }
    return p;
}
int cherry_denoiser_process(void *ptr,int16_t *pcm,int frames) {
    struct Processor *p=ptr; if(!p || !pcm || frames!=FRAME) return 0;
    for(int c=0;c<p->channels;c++) {
        for(int i=0;i<FRAME;i++) p->scratch[i]=pcm[i*p->channels+c];
        speex_preprocess_run(p->state[c],p->scratch);
        for(int i=0;i<FRAME;i++) pcm[i*p->channels+c]=p->scratch[i];
    }
    return 1;
}
