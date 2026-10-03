#include <jni.h>
#include <cstdio>
#include <cstdint>
#include <new>
#include <vector>
#include "bzlib.h"

namespace {
struct Reader {
    FILE* file = nullptr;
    BZFILE* bz = nullptr;
    bool finished = false;
    std::vector<char> buffer = std::vector<char>(256 * 1024);
    ~Reader() {
        if (bz) { int error = BZ_OK; BZ2_bzReadClose(&error, bz); }
        if (file) fclose(file);
    }
};
void fail(JNIEnv* env, const char* message) {
    env->ThrowNew(env->FindClass("java/io/IOException"), message);
}
Reader* reader(jlong handle) { return reinterpret_cast<Reader*>(static_cast<intptr_t>(handle)); }
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_nahanhhan_lecturerecording_models_NativeBzip2InputStream_openNative(
    JNIEnv* env, jobject, jstring path) {
    const char* filename = env->GetStringUTFChars(path, nullptr);
    if (!filename) return 0;
    Reader* value = nullptr;
    try { value = new Reader(); } catch (const std::bad_alloc&) { }
    if (!value) { env->ReleaseStringUTFChars(path, filename); fail(env, "Not enough memory for model extraction"); return 0; }
    value->file = fopen(filename, "rb");
    env->ReleaseStringUTFChars(path, filename);
    if (!value->file) { delete value; fail(env, "Cannot open model archive"); return 0; }
    setvbuf(value->file, nullptr, _IOFBF, 256 * 1024);
    int error = BZ_OK;
    value->bz = BZ2_bzReadOpen(&error, value->file, 0, 0, nullptr, 0);
    if (error != BZ_OK || !value->bz) { delete value; fail(env, "Cannot initialize model decompression"); return 0; }
    return static_cast<jlong>(reinterpret_cast<intptr_t>(value));
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_nahanhhan_lecturerecording_models_NativeBzip2InputStream_readNative(
    JNIEnv* env, jobject, jlong handle, jbyteArray bytes, jint offset, jint length) {
    auto* value = reader(handle);
    if (!value) { fail(env, "Model archive stream is closed"); return -1; }
    const jsize size = env->GetArrayLength(bytes);
    if (offset < 0 || length < 0 || offset > size - length) { fail(env, "Invalid decompression buffer range"); return -1; }
    if (length == 0) return 0;
    if (value->finished) return -1;
    const int requested = length < static_cast<int>(value->buffer.size()) ? length : static_cast<int>(value->buffer.size());
    int error = BZ_OK;
    const int count = BZ2_bzRead(&error, value->bz, value->buffer.data(), requested);
    if (error != BZ_OK && error != BZ_STREAM_END) { fail(env, "Model archive is truncated or corrupt"); return -1; }
    if (error == BZ_STREAM_END) value->finished = true;
    if (count > 0) env->SetByteArrayRegion(bytes, offset, count, reinterpret_cast<jbyte*>(value->buffer.data()));
    return count > 0 ? count : -1;
}

extern "C" JNIEXPORT jlong JNICALL
Java_io_github_nahanhhan_lecturerecording_models_NativeBzip2InputStream_positionNative(
    JNIEnv*, jobject, jlong handle) {
    auto* value = reader(handle);
    return value ? static_cast<jlong>(ftello(value->file)) : 0;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_nahanhhan_lecturerecording_models_NativeBzip2InputStream_closeNative(
    JNIEnv*, jobject, jlong handle) { delete reader(handle); }
