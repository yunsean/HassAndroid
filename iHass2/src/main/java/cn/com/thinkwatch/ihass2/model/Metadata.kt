package cn.com.thinkwatch.ihass2.model

import java.lang.annotation.Documented
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy

@Documented
@Target(AnnotationTarget.FIELD)
@Retention(RetentionPolicy.RUNTIME)
annotation class Metadata(val name: String,
                          val order: Int = 0,
                          val display: String = "")
