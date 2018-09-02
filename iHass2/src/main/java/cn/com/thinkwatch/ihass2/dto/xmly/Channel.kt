package cn.com.thinkwatch.ihass2.dto.xmly

data class Channel(val id: Int = 0,
                   val name: String = "",
                   val coverSmall: String = "",
                   val coverLarge: String = "",
                   val playUrl: PlayUrl = PlayUrl(),
                   val playCount: Int = 0,
                   val programName: String = "")