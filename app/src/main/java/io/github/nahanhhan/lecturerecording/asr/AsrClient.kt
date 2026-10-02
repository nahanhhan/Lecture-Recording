package io.github.nahanhhan.lecturerecording.asr

import android.content.*
import android.os.*
import io.github.nahanhhan.lecturerecording.logging.AppLog
import kotlinx.coroutines.*
import java.util.UUID

class AsrClient(private val context: Context) {
    private var remote: Messenger? = null
    private var binding: CompletableDeferred<Messenger>? = null
    private var bound = false
    private var answer: CompletableDeferred<String>? = null
    private var token = ""
    private val reply = Messenger(object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.data.getString("token") != token) return
            val error = message.data.getString("error")
            if (error != null) answer?.completeExceptionally(IllegalStateException(error))
            else answer?.complete(message.data.getString("text") ?: "")
        }
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            AppLog.i("AsrClient", "识别进程已连接")
            remote = Messenger(service); binding?.complete(remote!!)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            AppLog.e("AsrClient", "识别进程中断，录音继续保存")
            remote = null; answer?.completeExceptionally(IllegalStateException("识别进程中断，录音继续保存"))
        }
        override fun onBindingDied(name: ComponentName?) {
            onServiceDisconnected(name); close()
        }
        override fun onNullBinding(name: ComponentName?) {
            AppLog.e("AsrClient", "无法连接识别进程")
            binding?.completeExceptionally(IllegalStateException("无法连接识别进程"))
        }
    }
    private suspend fun connect(): Messenger = withContext(Dispatchers.Main) {
        remote?.let { return@withContext it }
        if (bound) { runCatching { context.unbindService(connection) }; bound = false }
        val pending = CompletableDeferred<Messenger>()
        binding = pending
        bound = context.bindService(Intent(context, AsrService::class.java), connection, Context.BIND_AUTO_CREATE)
        check(bound) { "无法启动识别进程" }
        withTimeout(30_000) { pending.await() }
    }
    suspend fun recognize(path: String, model: String): String {
        val service = connect()
        AppLog.d("AsrClient", "发送识别请求 path=$path model=$model")
        val pending = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            token = UUID.randomUUID().toString(); answer = pending
            service.send(Message.obtain(null, 1).apply {
                data = Bundle().apply { putString("token", token); putString("path", path); putString("model", model) }
                replyTo = reply
            })
        }
        return try {
            val text = withTimeout(120_000) { pending.await() }
            AppLog.d("AsrClient", "收到识别结果 字符=${text.length}")
            text
        } finally { withContext(NonCancellable + Dispatchers.Main) { answer = null } }
    }
    fun close() {
        AppLog.d("AsrClient", "断开识别进程")
        if (bound) runCatching { context.unbindService(connection) }
        bound = false; remote = null
    }
}
