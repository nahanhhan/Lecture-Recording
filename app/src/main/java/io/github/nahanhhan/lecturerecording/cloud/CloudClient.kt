package io.github.nahanhhan.lecturerecording.cloud

import io.github.nahanhhan.lecturerecording.data.CloudSettings
import io.github.nahanhhan.lecture.core.protocolJson
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class CloudClient(private val settings: CloudSettings) {
    private val client = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS).callTimeout(240, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    suspend fun complete(body: JsonObject): JsonObject {
        val base = settings.baseUrl.trimEnd('/')
        val uri = java.net.URI(base)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.query == null && uri.fragment == null) { "接口基础地址必须是 HTTPS 地址" }
        require(settings.key.isNotBlank() && settings.model.isNotBlank()) { "请先配置模型名称和 API Key" }
        val request = Request.Builder().url("$base/chat/completions")
            .header("Authorization", "Bearer ${settings.key}")
            .post(body.toString().toRequestBody("application/json".toMediaType())).build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWithException(IllegalStateException("网络请求失败，请检查连接后重试"))
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.use {
                            check(it.isSuccessful) { when (it.code) {
                                401, 403 -> "接口鉴权失败，请检查 API Key"
                                429 -> "接口限流，请稍后重试"
                                else -> "接口请求失败（HTTP ${it.code}），请检查服务兼容性"
                            } }
                            val stream = requireNotNull(it.body).byteStream()
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val count = stream.read(buffer); if (count < 0) break
                                check(output.size() + count <= 2_000_000) { "接口响应过长" }
                                output.write(buffer, 0, count)
                            }
                            val bytes = output.toByteArray()
                            val json = protocolJson.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
                            if (continuation.isActive) continuation.resume(json)
                        }
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }
}
