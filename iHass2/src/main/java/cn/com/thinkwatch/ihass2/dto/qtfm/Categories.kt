package cn.com.thinkwatch.ihass2.dto.qtfm

data class Categories(val channel_categories: List<Group> = listOf(),
                      val regions_map: List<Group> = listOf(),
                      val regions: List<Group> = listOf())