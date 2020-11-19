package cn.com.thinkwatch.ihass2.utils

import android.app.Notification
import android.app.NotificationManager
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.NotificationCompat

import com.dylan.common.data.StrUtil

import cn.com.thinkwatch.ihass2.R
import com.yunsean.dynkotlins.extensions.logis
import com.yunsean.dynkotlins.extensions.readPref
import com.yunsean.dynkotlins.extensions.savePref

class MessageReceiver(private val context: Context) {
    private val smsObserver: SmsObserver
    private var latestSmsId: Long = 0
    private val SMS_INBOX = Uri.parse("content://sms/")

    init {
        val timecode = System.currentTimeMillis().toString()
        context.savePref("MessageObserverUid", timecode)
        this.smsObserver = SmsObserver(context, timecode, smsHandler)
        this.context.contentResolver.registerContentObserver(SMS_INBOX, true, smsObserver)
        this.latestSmsId = latestSmsId()
        getSmsFromPhone()
    }

    private fun latestSmsId(): Long {
        var cursor: Cursor? = null
        try {
            val cr = context.contentResolver
            val projection = arrayOf("_id", "address", "person", "body")
            val where = " _id > $latestSmsId"
            cursor = cr.query(SMS_INBOX, projection, where, null, "date desc")
            if (null == cursor) return 0
            return if (cursor.moveToNext()) cursor.getLong(cursor.getColumnIndex("_id")) else 0
        } catch (ex: Exception) {
            ex.printStackTrace()
            return 0
        } finally {
            try { cursor?.close() } catch (_: Exception) { }
        }
    }

    private @Synchronized fun getSmsFromPhone() {
        var cursor: Cursor? = null
        try {
            val cr = context.contentResolver
            val projection = arrayOf("_id", "address", "person", "body")
            val where = " _id > $latestSmsId"
            cursor = cr.query(SMS_INBOX, projection, where, null, "date")
            if (null == cursor) return
            while (cursor.moveToNext()) {
                latestSmsId = cursor.getLong(cursor.getColumnIndex("_id"))
                val number = cursor.getString(cursor.getColumnIndex("address"))
                var name = cursor.getString(cursor.getColumnIndex("person"))
                var body = cursor.getString(cursor.getColumnIndex("body"))
                if (StrUtil.isBlank(name)) name = number
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val builder = NotificationCompat.Builder(context, "notifyApp")
                do {
                    val text: String
                    if (body.length > 70) {
                        val length = if ((body.length % 70) > 0) body.length % 70 else 70
                        text = body.substring(body.length - length)
                        body = body.substring(0, body.length - length)
                    } else {
                        text = body
                        body = ""
                    }
                    builder.setSmallIcon(R.mipmap.ic_launcher)
                    builder.setTicker(System.currentTimeMillis().toString())
                    builder.setLargeIcon(BitmapFactory.decodeResource(context.resources, R.mipmap.ic_launcher))
                    builder.setContentTitle(name)
                    builder.setContentText(text)
                    builder.setAutoCancel(true)
                    notificationManager.notify(9999, builder.build())
                    try { Thread.sleep(1000) } catch (_: Exception) {}
                    notificationManager.cancel(9999)
                } while (body.isNotEmpty())
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
        } finally {
            try { cursor?.close() } catch (ex: Exception) { }
        }
    }

    internal inner class SmsObserver(val context: Context,
                                     val messageObserverUid: String,
                                     handler: Handler) : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            val observerUid = context.readPref("MessageObserverUid")
            if (!messageObserverUid.equals(observerUid)) {
                context.contentResolver.unregisterContentObserver(this)
            } else {
                getSmsFromPhone()
            }
        }
    }
    companion object {
        private val smsHandler = Handler()
    }
}
