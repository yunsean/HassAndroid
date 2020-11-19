package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.dto.AutomationResponse
import cn.com.thinkwatch.ihass2.dto.TemplateRequest
import cn.com.thinkwatch.ihass2.network.BaseApi
import cn.com.thinkwatch.ihass2.network.http.HttpRestApi
import cn.com.thinkwatch.ihass2.utils.Gsons
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.sketch.Dialogs
import com.dylan.common.utils.Utility
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_hass_template_edit.*
import okhttp3.MediaType
import okhttp3.RequestBody
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.find
import org.jetbrains.anko.sdk25.coroutines.onClick
import retrofit2.HttpException
import java.lang.Exception
import java.util.*

class TemplateEditActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_template_edit)
        setTitle("模板参数编辑", true, "确定")
        setAutoHideSoftInput(AutoHideSoftInputMode.Never)

        val template = intent.getStringExtra("template")
        act.template.setText(template)

        ui()
    }
    override fun doRight() {
        val template = act.template.text()
        setResult(Activity.RESULT_OK, Intent().putExtra("template", template))
        finish()
    }

    private data class Action(val content: String,
                              val selectionStart: Int,
                              val selectionEnd: Int)
    private val undos: Stack<Action> = Stack()
    private val redos: Stack<Action> = Stack()
    private fun record() {
        undos.push(Action(template.text.toString(), template.selectionStart, template.selectionEnd))
        redos.clear()
    }
    private fun ui() {
        act.left.onClick {
            if (template.selectionStart > 0) template.setSelection(template.selectionStart - 1)
        }
        act.right.onClick {
            if (template.selectionStart < template.text.length) template.setSelection(template.selectionStart + 1)
        }
        act.select_start_left.onClick {
            if (template.selectionStart > 0) template.setSelection(template.selectionStart - 1, template.selectionEnd)
        }
        act.select_start_right.onClick {
            if (template.selectionStart < template.selectionEnd) template.setSelection(template.selectionStart + 1, template.selectionEnd)
        }
        act.select_end_left.onClick {
            if (template.selectionEnd > template.selectionStart) template.setSelection(template.selectionStart, template.selectionEnd - 1)
        }
        act.select_end_right.onClick {
            if (template.selectionEnd < template.text.length) template.setSelection(template.selectionStart, template.selectionEnd + 1)
        }
        act.keyboard.onClick {
            Utility.toggleSoftKeyboard(act)
        }
        act.do_return.onClick {
            record()
            template.let {
                val pos = it.selectionStart + 1
                it.text.insert(it.selectionStart, "\n")
                it.setSelection(pos)
            }
        }
        act.undo.onClick {
            if (undos.empty()) return@onClick
            redos.push(Action(template.text.toString(), template.selectionStart, template.selectionEnd))
            val action = undos.pop()
            template.setText(action.content)
            template.setSelection(action.selectionStart, action.selectionEnd)
        }
        act.redo.onClick {
            if (redos.empty()) return@onClick
            undos.push(Action(template.text.toString(), template.selectionStart, template.selectionEnd))
            val action = redos.pop()
            template.setText(action.content)
            template.setSelection(action.selectionStart, action.selectionEnd)
        }
        act.check.onClick {
            val template = act.template.text()
            val json = AutomationEditActivity.gsonBuilder.toJson(TemplateRequest(template))
            val body = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), json)
            val waiting = Dialogs.showWait(ctx)
            BaseApi.rawApi(cfg.haHostUrl, HttpRestApi::class.java)
                    .testTemplate(cfg.haPassword, cfg.haToken, body)
                    .withNext {result->
                        waiting.dismiss()
                        showDialog(R.layout.dialog_hass_template_result, object: OnSettingDialogListener {
                            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                                contentView.find<TextView>(R.id.result).text = result
                            }
                        }, intArrayOf(R.id.cancel), object: OnDialogItemClickedListener {
                            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                                dialog.dismiss()
                            }
                        })
                    }
                    .error {
                        it.printStackTrace()
                        if (it is HttpException) {
                            val json = it.response().errorBody()?.string()
                            try {
                                val result = Gsons.gson.fromJson<AutomationResponse>(json, AutomationResponse::class.java)
                                showError(result?.message ?: "测试模板参数失败！")
                            } catch (_: Exception) {
                                showError("测试模板失败，请检查表达式是否合法！")
                            }
                        } else {
                            showError(it.message ?: "测试模板参数失败！")
                        }
                        waiting.dismiss()
                    }
                    .subscribeOnMain {
                        if (disposable == null) disposable = CompositeDisposable(it)
                        else disposable?.add(it)
                    }
        }
        act.insert.onClick {
            showDialog(R.layout.dialog_hass_template_scripts, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    allScript.forEach {script->
                        contentView.find<View>(script.id).onClick {
                            dialog.dismiss()
                            if (script.id != R.id.cancel) record()
                            script.func(act.template)
                        }
                    }
                }
            }, null, null)
        }
    }

    private data class Script(val id: Int,
                               val func: (textview: EditText)-> Unit)
    private val allScript = listOf(Script(R.id.cancel) {
    }, Script(R.id.value) {
        val pos = it.selectionStart + 3
        if (it.selectionStart == it.selectionEnd) {
            it.text.insert(it.selectionStart, "{{  }}")
        } else {
            val content = it.text.subSequence(it.selectionStart, it.selectionEnd)
            it.text.replace(it.selectionStart, it.selectionEnd, "{{ $content }}")
        }
        it.setSelection(pos)
    }, Script(R.id.if_end) {
        val pos = it.selectionStart + 7
        it.text.insert(it.selectionStart, "{%- if  -%}\n{%- endif -%}")
        it.setSelection(pos)
    }, Script(R.id.if_else) {
        val pos = it.selectionStart + 7
        it.text.insert(it.selectionStart, "{%- if  -%}\n{%- else -%}\n{%- endif -%}")
        it.setSelection(pos)
    }, Script(R.id.else_if) {
        val pos = it.selectionStart + 9
        it.text.insert(it.selectionStart, "\n{%- elif -%}")
        it.setSelection(pos)
    }, Script(R.id.to_for) {
        val pos = it.selectionStart + 16
        if (it.selectionStart == it.selectionEnd) {
            it.text.insert(it.selectionStart, "{%- for item in  %}\n{%- endfor %}")
        } else {
            val content = it.text.subSequence(it.selectionStart, it.selectionEnd)
            it.text.insert(it.selectionStart, "{%- for item in $content %}\n{%- endfor %}")
        }
        it.setSelection(pos)
    }, Script(R.id.to_set) {
        val pos = it.selectionStart + 7
        it.text.insert(it.selectionStart, "{% set  =  %}")
        it.setSelection(pos)
    }, Script(R.id.gt) {
        val pos = it.selectionStart + 3
        it.text.insert(it.selectionStart, " > ")
        it.setSelection(pos)
    }, Script(R.id.lt) {
        val pos = it.selectionStart + 3
        it.text.insert(it.selectionStart, " < ")
        it.setSelection(pos)
    }, Script(R.id.gte) {
        val pos = it.selectionStart + 4
        it.text.insert(it.selectionStart, " >= ")
        it.setSelection(pos)
    }, Script(R.id.lte) {
        val pos = it.selectionStart + 4
        it.text.insert(it.selectionStart, " <= ")
        it.setSelection(pos)
    }, Script(R.id.eq) {
        val pos = it.selectionStart + 4
        it.text.insert(it.selectionStart, " == ")
        it.setSelection(pos)
    }, Script(R.id.entity) {
        startActivityForResult(Intent(ctx, EntityListActivity::class.java).putExtra("singleOnly", true), 100)
    }, Script(R.id.state) {
        startActivityForResult(Intent(ctx, EntityListActivity::class.java).putExtra("singleOnly", true), 101)
    }, Script(R.id.attribute) {
        startActivityForResult(Intent(ctx, AttributeListActivity::class.java), 102)
    }, Script(R.id.is_state) {
        startActivityForResult(Intent(ctx, EntityListActivity::class.java).putExtra("singleOnly", true), 103)
    }, Script(R.id.is_attr) {
        startActivityForResult(Intent(ctx, AttributeListActivity::class.java), 104)
    }, Script(R.id.now) {
        val pos = it.selectionStart + 6
        it.text.insert(it.selectionStart, " now()")
        it.setSelection(pos)
    }, Script(R.id.year) {
        val pos = it.selectionStart + 11
        it.text.insert(it.selectionStart, " now().year")
        it.setSelection(pos)
    }, Script(R.id.month) {
        val pos = it.selectionStart + 12
        it.text.insert(it.selectionStart, " now().month")
        it.setSelection(pos)
    }, Script(R.id.day) {
        val pos = it.selectionStart + 10
        it.text.insert(it.selectionStart, " now().day")
        it.setSelection(pos)
    }, Script(R.id.hour) {
        val pos = it.selectionStart + 11
        it.text.insert(it.selectionStart, " now().hour")
        it.setSelection(pos)
    }, Script(R.id.minute) {
        val pos = it.selectionStart + 13
        it.text.insert(it.selectionStart, " now().minute")
        it.setSelection(pos)
    }, Script(R.id.second) {
        val pos = it.selectionStart + 13
        it.text.insert(it.selectionStart, " now().second")
        it.setSelection(pos)
    }, Script(R.id.to_int) {
        val pos = it.selectionStart + 4
        it.text.insert(it.selectionStart, "|int")
        it.setSelection(pos)
    }, Script(R.id.to_float) {
        val pos = it.selectionStart + 6
        it.text.insert(it.selectionStart, "|float")
        it.setSelection(pos)
    }, Script(R.id.to_round) {
        showDialog(R.layout.dialog_hass_prompt, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.find<TextView>(R.id.title).text = "四舍五入"
                contentView.find<TextView>(R.id.content).text = "输入精度："
                contentView.find<TextView>(R.id.input).text = "2"
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val value = contentView.find<TextView>(R.id.input).text().toIntOrNull() ?: 2
                    val text = "|round(${value})"
                    val pos = it.selectionStart + text.length
                    it.text.insert(it.selectionStart, text)
                    it.setSelection(pos)
                }
            }
        })
    }, Script(R.id.to_filesizeformat) {
        val pos = it.selectionStart + 15
        it.text.insert(it.selectionStart, "|filesizeformat")
        it.setSelection(pos)
    }, Script(R.id.to_string) {
        val pos = it.selectionStart + 7
        it.text.insert(it.selectionStart, "|string")
        it.setSelection(pos)
    }, Script(R.id.to_lower) {
        val pos = it.selectionStart + 6
        it.text.insert(it.selectionStart, "|lower")
        it.setSelection(pos)
    }, Script(R.id.to_upper) {
        val pos = it.selectionStart + 6
        it.text.insert(it.selectionStart, "|upper")
        it.setSelection(pos)
    }, Script(R.id.to_trim) {
        val pos = it.selectionStart + 5
        it.text.insert(it.selectionStart, "|trim")
        it.setSelection(pos)
    }, Script(R.id.regex_search) {
        showDialog(R.layout.dialog_hass_prompt, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.find<TextView>(R.id.title).text = "正则匹配"
                contentView.find<TextView>(R.id.content).text = "输入正则表达式："
                contentView.find<TextView>(R.id.input).text = ".*"
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val value = contentView.find<TextView>(R.id.input).text()
                    val text = "|regex_search('${value}')"
                    val pos = it.selectionStart + text.length
                    it.text.insert(it.selectionStart, text)
                    it.setSelection(pos)
                }
            }
        })
    }, Script(R.id.to_replace) {
        showDialog(R.layout.dialog_hass_replace, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val oldText = contentView.find<TextView>(R.id.oldText).text()
                    val newText = contentView.find<TextView>(R.id.newText).text()
                    val text = "|replace('$oldText', '$newText')"
                    val pos = it.selectionStart + text.length
                    it.text.insert(it.selectionStart, text)
                    it.setSelection(pos)
                }
            }
        })
    }, Script(R.id.regex_match) {
        showDialog(R.layout.dialog_hass_prompt, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.find<TextView>(R.id.title).text = "正则匹配"
                contentView.find<TextView>(R.id.content).text = "输入正则表达式："
                contentView.find<TextView>(R.id.input).text = ".*"
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val value = contentView.find<TextView>(R.id.input).text()
                    val text = "|regex_match('${value}')"
                    val pos = it.selectionStart + text.length
                    it.text.insert(it.selectionStart, text)
                    it.setSelection(pos)
                }
            }
        })
    }, Script(R.id.regex_replace) {
        showDialog(R.layout.dialog_hass_replace, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val oldText = contentView.find<TextView>(R.id.oldText).text()
                    val newText = contentView.find<TextView>(R.id.newText).text()
                    val text = "|regex_replace('$oldText', '$newText')"
                    val pos = it.selectionStart + text.length
                    it.text.insert(it.selectionStart, text)
                    it.setSelection(pos)
                }
            }
        })
    }, Script(R.id.regex_findall_index) {
        showDialog(R.layout.dialog_hass_replace, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val oldText = contentView.find<TextView>(R.id.oldText).text()
                    val newText = contentView.find<TextView>(R.id.newText).text()
                    val text = "|regex_findall_index('$oldText', '$newText')"
                    val pos = it.selectionStart + text.length
                    it.text.insert(it.selectionStart, text)
                    it.setSelection(pos)
                }
            }
        })
    })

    @ActivityResult(requestCode = 100)
    private fun afterEntity(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        entityIds.get(0)?.let { act.template.apply { text.insert(selectionStart, it) } }
    }
    @ActivityResult(requestCode = 101)
    private fun afterEntityState(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        entityIds.get(0)?.let { act.template.apply { text.insert(selectionStart, "states('$it')") } }
    }
    @ActivityResult(requestCode = 102)
    private fun afterAttribibute(data: Intent?) {
        val entityId = data?.getStringExtra("entityId")
        val attribute = data?.getStringExtra("attribute")
        if (entityId.isNullOrBlank() || attribute.isNullOrBlank()) return
        act.template.apply { text.insert(selectionStart, "state_attr('$entityId', '$attribute')") }
    }
    @ActivityResult(requestCode = 103)
    private fun afterIsState(data: Intent?) {
        val entityIds = data?.getStringArrayExtra("entityIds")
        if (entityIds == null || entityIds.size < 1) return
        db.getEntity(entityIds.get(0))?.let {entity->
            showDialog(R.layout.dialog_hass_prompt, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.find<TextView>(R.id.title).text = "对象状态"
                    contentView.find<TextView>(R.id.content).text = "${entity.friendlyName}："
                    contentView.find<TextView>(R.id.input).text = entity.state
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    dialog.dismiss()
                    if (clickedView.id == R.id.ok) {
                        val value = contentView.find<TextView>(R.id.input).text
                        act.template.apply { text.insert(selectionStart, "is_state('${entity.entityId}', '$value')") }
                    }
                }
            })
        }
    }
    @ActivityResult(requestCode = 104)
    private fun afterIsEntityAttr(data: Intent?) {
        val entityId = data?.getStringExtra("entityId")
        val attribute = data?.getStringExtra("attribute")
        val value = data?.getStringExtra("value")
        if (entityId.isNullOrBlank() || attribute.isNullOrBlank()) return
        showDialog(R.layout.dialog_hass_prompt, object : OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.find<TextView>(R.id.title).text = "属性值"
                contentView.find<TextView>(R.id.content).text = "${entityId}.${attribute}："
                contentView.find<TextView>(R.id.input).text = value
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val value = contentView.find<TextView>(R.id.input).text
                    act.template.apply { text.insert(selectionStart, "is_state_attr('$entityId', '$attribute', '$value')") }
                }
            }
        })
    }
}

