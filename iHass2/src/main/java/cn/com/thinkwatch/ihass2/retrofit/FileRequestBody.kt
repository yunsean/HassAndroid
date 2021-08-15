package cn.com.thinkwatch.ihass2.retrofit

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.*
import java.io.IOException


class FileRequestBody(private val requestBody: RequestBody,
                         private val callback: (total: Long, progress: Long, percent: Int)-> Unit) : RequestBody() {
    @Throws(IOException::class)
    override fun contentLength(): Long {
        return requestBody.contentLength()
    }
    override fun contentType(): MediaType? {
        return requestBody.contentType()
    }
    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val countingSink = CountingSink(sink)
        val bufferedSink = Okio.buffer(countingSink)
        requestBody.writeTo(bufferedSink)
        bufferedSink.flush()
    }
    private inner class CountingSink(delegate: Sink) : ForwardingSink(delegate) {
        private var bytesWritten: Long = 0
        private var lastPercent = 0
        @Throws(IOException::class)
        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            bytesWritten += byteCount
            val percent = (bytesWritten * 100 / contentLength()).toInt()
            if (percent == lastPercent) return
            lastPercent = percent
            callback.invoke(contentLength(), bytesWritten, percent)
        }
    }
}