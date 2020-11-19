package cn.com.thinkwatch.ihass2.model

import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table
import java.util.*

@Table(name = "HASS_VOICE_HISTORIES")
data class VoiceHistory (@Column(name = "SPEECH") var speech: Boolean = true,
                         @Column(name = "CONTENT") var content: String = "",
                         @Column(name = "TIME") var time: Date? = null,
                         @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0)