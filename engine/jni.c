#ifdef __ANDROID__
#include <jni.h>
#include <stdlib.h>

extern void* TsumoCall(const char* action, int actionLen, const char* payload, int payloadLen, int* outLen);

JNIEXPORT jbyteArray JNICALL Java_com_tsumo_app_engine_ArchiveEngine_nativeCall(
    JNIEnv* env,
    jobject thiz,
    jbyteArray actionArr,
    jbyteArray payloadArr) {

    jsize actionLen = (*env)->GetArrayLength(env, actionArr);
    jbyte* actionBytes = (*env)->GetByteArrayElements(env, actionArr, NULL);

    jsize payloadLen = (*env)->GetArrayLength(env, payloadArr);
    jbyte* payloadBytes = (*env)->GetByteArrayElements(env, payloadArr, NULL);

    int outLen = 0;
    void* outPtr = TsumoCall(
        (const char*)actionBytes, (int)actionLen,
        (const char*)payloadBytes, (int)payloadLen,
        &outLen
    );

    (*env)->ReleaseByteArrayElements(env, actionArr, actionBytes, JNI_ABORT);
    (*env)->ReleaseByteArrayElements(env, payloadArr, payloadBytes, JNI_ABORT);

    jbyteArray result = (*env)->NewByteArray(env, outLen);
    if (outPtr != NULL && outLen > 0) {
        (*env)->SetByteArrayRegion(env, result, 0, outLen, (const jbyte*)outPtr);
    }
    if (outPtr != NULL) {
        free(outPtr);
    }
    return result;
}
#endif
