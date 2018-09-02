package cn.com.thinkwatch.ihass2.dto.xmly

data class ChannelResult(val data: List<Channel> = listOf(),
                         val page: Int = 0,
                         val pageSize: Int = 20,
                         val totalSize: Int = 0)