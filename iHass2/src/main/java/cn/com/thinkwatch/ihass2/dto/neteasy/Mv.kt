package cn.com.thinkwatch.ihass2.dto.neteasy

data class Mv(val name: String = "",
              val id: String = "",
              val cover: String = "",
              val artistName: String = "",
              val artists: List<Artist> = listOf())