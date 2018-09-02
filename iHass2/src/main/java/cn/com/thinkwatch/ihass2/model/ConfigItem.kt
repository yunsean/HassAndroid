package cn.com.thinkwatch.ihass2.model

import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_CONFIGS")
data class ConfigItem (@Column(name = "KEY", isId = true, autoGen = false) var key: String = "",
                       @Column(name = "VALUE") var value: String = "")