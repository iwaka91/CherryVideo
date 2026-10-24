#pragma once
#include <stdint.h>
void *cherry_denoiser_create(int channels, int attenuation_db);
int cherry_denoiser_process(void *state, int16_t *pcm, int frames);
void cherry_denoiser_destroy(void *state);
