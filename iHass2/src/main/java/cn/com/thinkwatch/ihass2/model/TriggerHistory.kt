package cn.com.thinkwatch.ihass2.model

import cn.com.thinkwatch.ihass2.enums.TriggerType
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table
import java.util.*

@Table(name = "HASS_TRIGGER_HISTORY")
data class TriggerHistory (@Column(name = "TRIGGER_ID") var triggerId: Long = 0L,
                           @Column(name = "TYPE") var type: TriggerType = TriggerType.nfc,
                           @Column(name = "NAME") var name: String = "",
                           @Column(name = "SERVICE_ID") var serviceId: String = "",
                           @Column(name = "TRIGGER_TIME") var triggerTime: Date = Date(),
                           @Column(name = "ID", isId = true, autoGen = true) var id: Long = 0L)