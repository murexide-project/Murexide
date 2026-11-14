package com.juhao.murexide.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/**
 * 语音录制封装：把 MediaRecorder 的创建/配置/启动/停止固定到唯一的工作线程上，
 * 避免在不同线程操作 MediaRecorder 导致的 IllegalStateException。
 */
class VoiceRecorder(private val context: Context) {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "voice-recorder").apply { priority = Thread.MAX_PRIORITY }
    }

    private var recorder: MediaRecorder? = null
    private var file: File? = null

    @Volatile
    private var startMillis: Long = 0L

    @Volatile
    private var recording = false

    val isRecording: Boolean
        get() = recording

    /**
     * 开始录音（阻塞直到 MediaRecorder 启动完成）。
     * @return 启动是否成功
     */
    fun start(): Boolean {
        val success = executor.submit(Callable<Boolean> {
            try {
                val output = File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
                val r = createRecorder()
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                r.setAudioSamplingRate(44100)
                r.setAudioEncodingBitRate(96000)
                r.setAudioChannels(1)
                r.setOutputFile(output.absolutePath)
                r.prepare()
                r.start()
                recorder = r
                file = output
                startMillis = System.currentTimeMillis()
                recording = true
                true
            } catch (e: Exception) {
                Log.w(TAG, "启动录音失败", e)
                runCatching { recorder?.release() }
                recorder = null
                file?.delete()
                file = null
                recording = false
                false
            }
        }).get()
        return success
    }

    /**
     * 停止录音并返回录音文件与时长（秒）。
     * 录音太短或停止失败时返回 null 并清理文件。
     */
    fun stop(): Pair<File, Int>? {
        return executor.submit(Callable<Pair<File, Int>?> {
            val r = recorder
            val output = file
            var stopped = false
            try {
                if (r != null) {
                    r.stop()
                    stopped = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "停止录音失败", e)
            } finally {
                runCatching { r?.release() }
                recorder = null
                recording = false
            }
            val seconds = ((System.currentTimeMillis() - startMillis) / 1000L).toInt().coerceAtLeast(1)
            if (stopped && output != null && output.exists() && output.length() > 0L) {
                file = null
                output to seconds
            } else {
                output?.delete()
                file = null
                null
            }
        }).get()
    }

    /** 放弃录音并删除文件。 */
    fun cancel() {
        executor.submit {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            recorder = null
            recording = false
            file?.delete()
            file = null
        }.get()
    }

    /** 当前已录制的秒数。 */
    fun elapsedSeconds(): Int {
        if (!recording) return 0
        return ((System.currentTimeMillis() - startMillis) / 1000L).toInt().coerceAtLeast(0)
    }

    /** 停止并释放录音器，删除未使用的文件。 */
    fun release() {
        try {
            executor.submit {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                recorder = null
                recording = false
                file?.delete()
                file = null
            }.get()
        } catch (_: Exception) {
        }
        executor.shutdown()
    }

    private fun createRecorder(): MediaRecorder {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
    }

    companion object {
        private const val TAG = "VoiceRecorder"
    }
}