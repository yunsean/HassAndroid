package cn.com.thinkwatch.ihass2.model.service

data class Service(var name: String = "",
                   val description: String = "",
                   val fields: Fields? = null)