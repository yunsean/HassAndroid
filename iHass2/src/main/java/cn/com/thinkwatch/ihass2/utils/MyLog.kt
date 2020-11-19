package cn.com.thinkwatch.ihass2.utils

import android.os.Environment
import android.util.Log
import cn.com.thinkwatch.ihass2.HassApplication
import java.io.*
import java.net.UnknownHostException
import java.text.SimpleDateFormat
import java.util.*

object MyLog {
    val CACHE_DIR_NAME = "debuglog"
    var isDebugModel = true// 是否输出日志
    var isSaveDebugInfo = true// 是否保存调试日志
    var isSaveCrashInfo = true// 是否保存报错日志
    val file: String
        get() {
            var sdDir: File? = null
            if (Environment.getExternalStorageState() == android.os.Environment.MEDIA_MOUNTED) {
                sdDir = Environment.getExternalStorageDirectory()
            }
            val cacheDir = File(sdDir.toString() + File.separator + CACHE_DIR_NAME)
            if (!cacheDir.exists()) {
                cacheDir.mkdir()
            }
            val filePath = File(cacheDir.toString() + File.separator + date() + ".txt")
            return filePath.toString()
        }

    fun v(tag: String, msg: String) {
        if (isDebugModel) Log.v(tag, "--> $msg")
        if (isSaveDebugInfo) {
            object : Thread() {
                override fun run() {
                    write(time() + tag + " --> " + msg + "\n")
                }
            }.start()
        }
    }

    fun d(tag: String, msg: String) {
        if (isDebugModel) Log.d(tag, "--> $msg")
        if (isSaveDebugInfo) {
            object : Thread() {
                override fun run() {
                    write(time() + tag + " --> " + msg + "\n")
                }
            }.start()
        }
    }

    fun i(tag: String, msg: String) {
        if (isDebugModel) Log.i(tag, "--> $msg")
        if (isSaveDebugInfo) {
            object : Thread() {
                override fun run() {
                    write(time() + tag + " --> " + msg + "\n")
                }
            }.start()
        }
    }

    fun w(tag: String, msg: String) {
        if (isDebugModel) Log.w(tag, "--> $msg")
    }

    @Synchronized fun e(tag: String, msg: String) {
        if (isDebugModel) Log.e(tag, "--> $msg")
        if (isSaveDebugInfo) {
            write(time() + "(${HassApplication.application.isMainProcess()})" + tag + " --> " + msg + "\n")
        }
    }

    fun e(tag: String, tr: Throwable) {
        if (isSaveCrashInfo) {
            object : Thread() {
                override fun run() {
                    write(time() + tag + " [CRASH] --> " + getStackTraceString(tr) + "\n")
                }
            }.start()
        }
    }

    fun getStackTraceString(tr: Throwable?): String {
        if (tr == null) return ""
        var t = tr
        while (t != null) {
            if (t is UnknownHostException) return ""
            t = t.cause
        }
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        tr.printStackTrace(pw)
        return sw.toString()
    }

    private fun time(): String {
        return "[" + SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(Date(System.currentTimeMillis())) + "] "
    }

    private fun date(): String {
        return SimpleDateFormat("yyyy-MM-dd").format(Date(System.currentTimeMillis()))
    }

    @Synchronized
    fun write(content: String) {
        try {
            val writer = FileWriter(file, true)
            writer.write(content)
            writer.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }
}
