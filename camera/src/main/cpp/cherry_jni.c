#include <jni.h>
#include <stdint.h>
#include "cherry_denoiser.h"
JNIEXPORT jlong JNICALL Java_id_tege_video_camera_AudioProcessor_create(JNIEnv *env,jobject self,jint channels,jint db) {
    return (jlong)(intptr_t)cherry_denoiser_create(channels,db);
}
JNIEXPORT jboolean JNICALL Java_id_tege_video_camera_AudioProcessor_processNative(JNIEnv *env,jobject self,jlong ptr,jobject buffer,jint channels) {
    void *data=(*env)->GetDirectBufferAddress(env,buffer);
    jlong capacity=(*env)->GetDirectBufferCapacity(env,buffer);
    if(channels<1 || channels>2 || capacity<480*channels*2) return JNI_FALSE;
    return cherry_denoiser_process((void*)(intptr_t)ptr,data,480)?JNI_TRUE:JNI_FALSE;
}
JNIEXPORT void JNICALL Java_id_tege_video_camera_AudioProcessor_destroy(JNIEnv *env,jobject self,jlong ptr) {
    cherry_denoiser_destroy((void*)(intptr_t)ptr);
}
