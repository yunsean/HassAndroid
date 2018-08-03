package cn.com.thinkwatch.ihass2.adapter

import cn.com.thinkwatch.ihass2.bean.BaseBean
import cn.com.thinkwatch.ihass2.model.JsonEntity

data class PanelGroup(val group: JsonEntity? = null,
                      val entities: MutableList<BaseBean> = mutableListOf())