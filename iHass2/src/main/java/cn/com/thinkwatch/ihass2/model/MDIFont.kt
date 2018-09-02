package cn.com.thinkwatch.ihass2.model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.support.v4.content.res.ResourcesCompat
import android.text.Layout
import android.text.TextPaint
import android.widget.TextView
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.R
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import java.io.InputStreamReader


class MDIFont private constructor() {
    val icons = mutableMapOf<String, String>()
    private data class MDIItem(val name: String, val hex: String)
    init {
        try {
            val stream = HassApplication.application.resources.openRawResource(R.raw.materialdesignicons)
            val reader = JsonReader(InputStreamReader(stream))
            reader.beginArray()
            var name = ""
            var hex = ""
            var node = ""
            var token: JsonToken?
            while (reader.hasNext()) {
                reader.beginObject()
                do {
                    node = reader.nextName()
                    if (node == "name") name = reader.nextString()
                    else if (node == "hex") hex = reader.nextString()
                    token = reader.peek()
                } while (token != null && token != JsonToken.END_OBJECT)
                reader.endObject()
                icons.put(name, String(charArrayOf((hex.toIntOrNull(16) ?: 0).toChar())))
            }
            reader.endArray()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    fun setIcon(textView: TextView?, code: String?) {
        var code = code
        if (textView == null) return
        if (code == null) code = ""
        if (code.startsWith("mdi:")) {
            code = code.substring(4)
            var mdiUnicode: String? = icons[code]
            if (mdiUnicode == null) mdiUnicode = icons["emoticon-dead"]
            textView.typeface = ResourcesCompat.getFont(textView.context, R.font.mdi)
            textView.text = mdiUnicode
        } else {
            textView.typeface = ResourcesCompat.getFont(textView.context, R.font.dincond)
            textView.text = code
        }
    }
    fun drawIcon(context: Context, text: String?, color: Int, size: Int = 160, height: Int = size): Bitmap {
        var text = text
        val icon = Bitmap.createBitmap(height * 2, height, Bitmap.Config.ARGB_8888)
        if (text == null) text = ""
        icon.eraseColor(Color.TRANSPARENT)
        val paint = TextPaint()
        paint.flags = Paint.ANTI_ALIAS_FLAG
        paint.color = color
        paint.textSize = size.toFloat()
        val canvas = Canvas(icon)
        if (text.startsWith("mdi:")) {
            text = icons[if (text.length > 4) text.substring(4) else text]
            if (text == null) text = icons["emoticon-dead"]
            paint.typeface = ResourcesCompat.getFont(context, R.font.mdi)
        } else {
            paint.typeface = ResourcesCompat.getFont(context, R.font.dincond)
        }
        val width = Layout.getDesiredWidth(text, paint).toInt()
        val yPos = canvas.height / 2 - (paint.descent() + paint.ascent()) / 2
        val xPos = ((canvas.width - width) / 2).toFloat()
        canvas.drawText(text, xPos, yPos, paint)
        return icon
    }

    private object MDIFont {
        val holder = MDIFont()
    }
    companion object {
        fun get() = MDIFont.holder
    }
}