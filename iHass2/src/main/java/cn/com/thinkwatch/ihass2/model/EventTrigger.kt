package cn.com.thinkwatch.ihass2.model

import cn.com.thinkwatch.ihass2.enums.TriggerType
import com.google.gson.annotations.Expose
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table
import java.util.*

@Table(name = "HASS_TRIGGER")
data class EventTrigger (@Expose @Column(name = "TYPE") var type: TriggerType = TriggerType.nfc,
                         @Expose @Column(name = "PARAMS") var params: String = "",
                         @Expose @Column(name = "NAME") var name: String = "",
                         @Expose @Column(name = "SERVICE_ID") var serviceId: String = "",
                         @Expose @Column(name = "BEGIN_TIME") var beginTime: Date? = null,
                         @Expose @Column(name = "END_TIME") var endTime: Date? = null,
                         @Expose @Column(name = "CONTENT") var content: String = "",
                         @Expose @Column(name = "NOTIFY") var notify: Boolean = false,
                         @Expose @Column(name = "DISABLED") var disabled: Boolean = false,
                         @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0L) {
    private val beginTick by lazy { beginTime?.let { it.hours * 3600 + it.minutes * 60 + it.seconds } }
    private val endTick by lazy { endTime?.let { it.hours * 3600 + it.minutes * 60 + it.seconds } }
    fun timeIsIn(time: Date): Boolean {
        if (beginTime == null && endTime == null) return true
        val now = time.hours * 3600 + time.minutes * 60 + time.seconds
        if (beginTick != null && endTick != null && endTick!! < beginTick!! && now > endTick!! && now < beginTick!!) return false
        else if (beginTick != null && beginTick!! > now) return false
        else if (endTick != null && endTick!! < now) return false
        return true
    }
}