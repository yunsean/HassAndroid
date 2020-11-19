package cn.com.thinkwatch.ihass2.dto.neteasy

data class Song(val name: String = "",
                val id: String = "",
                val alias: List<String> = listOf(),
                val artists: List<Artist> = listOf(),
                val album: Album? = null)