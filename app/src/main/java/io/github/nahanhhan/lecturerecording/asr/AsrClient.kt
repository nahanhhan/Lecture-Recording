package io.github.nahanhhan.lecturerecording.asr

import android.content.*
import android.os.*
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
            remote = Messenger(service); binding?.complete(remote!!)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            remote = null; answer?.completeExceptionally(IllegalStateException("识别进程中断，录音继续保存"))
        }
        override fun onBindingDied(name: ComponentName?) {
            onServiceDisconnected(name); close()
        }
        override fun onNullBinding(name: ComponentName?) {
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
        val pending = CompletableDeferred<String>()
        withContext(Dispatchers.Main) {
            token = UUID.randomUUID().toString(); answer = pending
            service.send(Message.obtain(null, 1).apply {
                data = Bundle().apply { putString("token", token); putString("path", path); putString("model", model) }
                replyTo = reply
            })
        }
        return try { withTimeout(120_000) { pending.await() } }
        finally { withContext(NonCancellable + Dispatchers.Main) { answer = null } }
    }
    fun close() {
        if (bound) runCatching { context.unbindService(connection) }
        bound = false; remote = null
    }
}
