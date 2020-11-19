package cn.com.thinkwatch.ihass2.voice

import android.provider.ContactsContract
import cn.com.thinkwatch.ihass2.HassApplication
import cn.com.thinkwatch.ihass2.db.LocalStorage
import cn.com.thinkwatch.ihass2.model.Contact
import cn.com.thinkwatch.ihass2.utils.HassConfig
import com.dylan.common.sketch.Actions
import com.github.promeg.pinyinhelper.Pinyin
import java.util.regex.Pattern

class ContactHandler: VoiceHandler {
    private val pattern3 by lazy { Pattern.compile(".*(呼叫|打电话给)(.*?)(\\d{3,11})") }
    private val pattern2 by lazy { Pattern.compile(".*(呼叫|打电话给)(.*)") }
    private lateinit var controller: VoiceController
    override fun setup(controller: VoiceController) {
        this.controller = controller
        this.controller.register(".*(呼叫|打电话给)(.*)", this)
    }
    override fun handle(command: String, more: Boolean, blindly: Boolean): Boolean {
        if (!more) {
            return matchAll(command)
        } else {
            this.blindly = blindly
            return matchConfirm(command)
        }
    }
    override fun detailClicked(item: DetailItem) {
        if (item.data is Boolean) {
            if (item.data) return call(latestContact?.phoneList?.get(0) ?: "")
            else return this.controller.finish(FinishAction.reset, "请重新说出你要的操作！")
        } else if (item.data is String) {
            return call(item.data)
        }
    }
    override fun reset() {

    }

    private val patternNumber by lazy { Pattern.compile("(\\d{3,11})") }
    private fun matchConfirm(result: String): Boolean {
        if (latestContact?.phoneList?.size ?: 0 == 1) {
            if (result.contains("不") || result.contains("错")) {
                this.controller.finish(FinishAction.reset, "请重新说出你要的操作！")
            } else if (result.contains("是") || result.contains("对")) {
                call(latestContact?.phoneList?.get(0) ?: "")
            } else {
                this.controller.setInput("我没听懂，请说是或者不是", this)
            }
        } else if (latestContact?.phoneList?.size ?: 0 > 1) {
            val m = patternNumber.matcher(result)
            if (m.find()) {
                val number = m.group(1)
                if (number != null) {
                    val matched = latestContact?.phoneList?.filter { it.contains(number) }
                    if (matched == null || matched.size < 1) this.controller.setInput("我没听懂，请说出要号码的任意几位", this)
                    else if (matched.size == 1) call(matched.get(0))
                    else if (matched.size > 1) this.controller.setInput("找到多个匹配的号码，请说更具体的号码", this)
                }
            } else {
                this.controller.setInput("我没听懂，请说出要号码的任意几位", this)
            }
        }
        return true
    }
    private fun matchAll(result: String): Boolean {
        var m = pattern3.matcher(result)
        if (m.find()) {
            val action = m.group(1)
            val param = m.group(2)
            val number = m.group(3)
            when (action) {
                "呼叫", "打电话给"-> return doContact(param, number)
            }
        }
        m = pattern2.matcher(result)
        if (m.find()) {
            val action = m.group(1)
            val param = m.group(2)
            when (action) {
                "呼叫", "打电话给"-> return doContact(param, null)
            }
        }
        return false
    }
    private fun call(phone: String) {
        latestContact = null
        try {
            Actions.call(HassApplication.application, phone)
            this.controller.finish(FinishAction.close, "正在拨打电话给$phone", 0)
        } catch (ex: Exception) {
            this.controller.finish(FinishAction.close, "拨打电话失败")
            ex.printStackTrace()
        }
    }

    private var allContacts: List<Contact>? = null
    get() {
        if (field == null || rebuilded) field = LocalStorage.instance.listContacts()
        rebuilded = false
        return field
    }
    private var blindly: Boolean = false
    private var latestContact: Contact? = null
    private fun doContact(param: String, number: String?, carryZero: Boolean = false): Boolean {
        if (!HassConfig.INSTANCE.getBoolean(HassConfig.Speech_VoiceContact)) return false
        var param = param
        var number = number
        if (carryZero && !(number?.startsWith("0") ?: false)) {
            this.controller.finish(FinishAction.close, "没有找到匹配的联系人")
            return true
        } else if (carryZero && number != null) {
            param += "零"
            number = number.substring(1)
        }
        if (!carryZero) this.controller.setTips("正在查找联系人...", true, true)
        allContacts?.let { contacts->
            var contact: Contact? = null
            val spell = Pinyin.toPinyin(param, "`")
            val similar = LocalStorage.getSimilar(spell)
            for (it in contacts) {
                if (it.phoneList.size < 1) continue
                if (param == it.name) contact = it
                else if (spell == it.spell) contact = it
                else if (similar == it.similar) contact = it
                if (contact != null) {
                    if (number != null && number.isNotBlank()) {
                        val matched = contact.phoneList.filter { it.contains(number) }
                        if (matched.size == 1) {
                            call(matched.get(0))
                            return true
                        }
                    } else if (contact.phoneList.size == 1) {
                        latestContact = contact
                        this.controller.setInput("你是要拨打${contact.name}的电话${contact.phones.get(0)}吗？", this)
                        this.controller.setDetail(listOf(DetailItem("是", true), DetailItem("否", false)))
                        return true
                    } else if (contact.phoneList.size > 1) {
                        latestContact = contact
                        val tips = if (blindly) {
                            val tips = StringBuilder("找到${contact.name}的${contact.phoneList.size}个电话，你需要拨打哪个尾号？")
                            contact.phoneList.forEach {
                                if (it.length < 10) return@forEach
                                tips.append("尾号").append(it.substring(it.length - 4)).append("，")
                            }
                            tips.toString()
                        } else {
                            "请选择要拨打的电话号码"
                        }
                        this.controller.setInput(tips, this)
                        this.controller.setDetail(contact.phoneList.map { DetailItem(it, it) })
                        return true
                    }
                }
            }
            if (number != null && number.isNotBlank()) {
                return doContact(param, number, true)
            }
        }
        return false
    }

    companion object {
        private var rebuilded = false
        fun rebuildContact(force: Boolean = false, progress: ((Int)-> Boolean)? = null) {
            if (!force && LocalStorage.instance.hasContact()) return
            val allContacts = mutableListOf<Contact>()
            val contentResolver = HassApplication.application.getContentResolver()
            val cursor = contentResolver.query(ContactsContract.Contacts.CONTENT_URI, null, null, null, null)
            val count = cursor.count
            var index = 0
            while (cursor.moveToNext()) {
                val nameFieldColumnIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                val contact = cursor.getString(nameFieldColumnIndex)
                val contactId = cursor.getString(cursor.getColumnIndex(ContactsContract.Contacts._ID));
                val phone = contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,null, ContactsContract.CommonDataKinds.Phone.CONTACT_ID + "=" + contactId, null, null);
                val phones = mutableListOf<String>()
                while (phone.moveToNext()) {
                    phones.add(phone.getString(phone.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
                            .replace("-","").replace(" ",""))
                }
                val spell = Pinyin.toPinyin(contact, "`")
                val similar = LocalStorage.getSimilar(spell)
                allContacts.add(Contact(contact, spell, similar, phones))
                index++
                if (progress != null && progress(index * 100 / count)) break
            }
            LocalStorage.instance.saveContacts(allContacts)
            rebuilded = true
        }
    }
}