package cn.com.thinkwatch.ihass2.model

import cn.com.thinkwatch.ihass2.db.LocalStorage
import org.xutils.db.annotation.Column
import org.xutils.db.annotation.Table

@Table(name = "HASS_CONTACTS")
data class Contact(@Column(name = "ID", isId = true, autoGen = true) var id: Long = 0,
                   @Column(name = "NAME") val name: String = "",
                   @Column(name = "SPELL") val spell: String = "",
                   @Column(name = "SIMILAR") val similar: String = "",
                   @Column(name = "PHONES") var phones: String = "") {
    constructor(name: String, spell: String, similar: String, phones: List<String>) :
            this(0, name, spell, similar, phones.joinToString("*")) {
    }
    val phoneList: List<String> by lazy { phones.split("*").toList() }
}