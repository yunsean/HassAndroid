package cn.com.thinkwatch.ihass2.model

import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.enums.AlarmSoundType
import cn.com.thinkwatch.ihass2.enums.AlarmVibrateType
import cn.com.thinkwatch.ihass2.enums.ConditionType
import com.google.gson.annotations.Expose
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table
import java.util.*

@Table(name = "HASS_OBSERVED")
data class Observed(@Expose @Column(name = "NAME") var name: String = "",
                    @Expose @Column(name = "ENTITY_ID") var entityId: String = "",
                    @Expose @Column(name = "CONDITION") val condition: ConditionType = ConditionType.any,
                    @Expose @Column(name = "STATE") var state: String? = null,
                    @Expose @Column(name = "BEGIN_TIME") var beginTime: Date? = null,
                    @Expose @Column(name = "END_TIME") var endTime: Date? = null,
                    @Expose @Column(name = "SOUND") val sound: AlarmSoundType = AlarmSoundType.quiet,
                    @Expose @Column(name = "IMAGE") var image: Int = R.mipmap.ic_launcher,
                    @Expose @Column(name = "VIBRATE") var vibrate: AlarmVibrateType = AlarmVibrateType.quiet,
                    @Expose @Column(name = "INSISTENT") var insistent: Boolean = true,
                    @Expose @Column(name = "DISABLED") var disabled: Boolean = false,
                    @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0L,
                    var entityName: String = "") {
    fun timeIsIn(time: Date): Boolean {
        if (beginTime == null && endTime == null) return true
        val now = time.hours * 3600 + time.minutes * 60 + time.seconds
        if (beginTime != null && ((beginTime!!.hours * 3600 + beginTime!!.minutes * 60 + beginTime!!.seconds) > now)) return false
        if (endTime != null && ((endTime!!.hours * 3600 + endTime!!.minutes * 60 + endTime!!.seconds) < now)) return false
        return true
    }
}