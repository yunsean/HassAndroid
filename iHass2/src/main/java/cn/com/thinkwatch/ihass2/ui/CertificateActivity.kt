package cn.com.thinkwatch.ihass2.ui

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.dylan.uiparts.activity.ActivityResult
import com.yunsean.dynkotlins.extensions.*
import kotlinx.android.synthetic.main.activity_hass_certificate.*
import kotlinx.android.synthetic.main.dialog_hass_prompt.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate


class CertificateActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_certificate)
        setTitle("Https双向认证", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        ui()
        data()
    }

    override fun doRight() {
        if (clientFile.isBlank() && trustFile.isBlank()) {
            savePref(arrayOf("clientFile", "clientPwd", "trustFile", "trustPwd", "trustType"), arrayOf("", "", "", "", ""))
            toastex("未设置双向验证证书信息！")
            return finish()
        }
        if (clientFile.isBlank()) return showError("请选择客户端证书文件！")
        if (trustFile.isBlank()) return showError("请选择CA证书文件！")
        try {
            File(clientFile).apply {
                val fos = openFileOutput(this.name, Context.MODE_PRIVATE)
                val fis = FileInputStream(this)
                copy(fis, fos)
                fis.close()
                fos.close()
                clientFile = this.name
            }
            File(trustFile).apply {
                val fos = openFileOutput(this.name, Context.MODE_PRIVATE)
                val fis = FileInputStream(this)
                copy(fis, fos)
                fis.close()
                fos.close()
                trustFile = this.name
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            return showError("导入证书失败：${ex.localizedMessage}")
        }
        savePref(arrayOf("clientFile", "clientPwd", "trustFile", "trustPwd", "trustType"),
                arrayOf(clientFile, clientPwd, trustFile, trustPwd, trustType))
        toastex("设置双向验证证书成功！")
        finish()
    }
    private fun copy(fis: FileInputStream, fos: FileOutputStream) {
        val buffer = ByteArray(1024)
        var read = fis.read(buffer)
        while (read != -1) {
            fos.write(buffer, 0, read)
            read = fis.read(buffer)
        }
    }

    private fun data() {
        val certs = readPref(arrayOf("clientFile", "clientPwd", "trustFile", "trustPwd", "trustType"))
        clientFile = certs[0] ?: ""
        clientPwd = certs[1] ?: ""
        trustFile = certs[2] ?: ""
        trustPwd = certs[3] ?: ""
        trustType = certs[4] ?: ""
        if (clientFile.isNotBlank()) {
            this.client.setText(checkBks(clientFile, clientPwd, false))
        }
        if (trustFile.isNotBlank() && trustType == "BKS") {
            this.trust.setText(checkBks(trustFile, trustPwd, false))
        } else if (trustFile.isNotBlank()) {
            this.trust.setText(checkX509(trustFile, trustPwd, false))
        }
    }
    private fun ui() {
        this.clientPanel.onClick {
            Intent(act, ChoiceFileActivity::class.java)
                    .putExtra("extensionNames", arrayListOf("bks"))
                    .start(act, 100)
        }
        this.trustPanel.onClick {
            Intent(act, ChoiceFileActivity::class.java)
                    .putExtra("extensionNames", arrayListOf("bks", "crt", "cer", "pem"))
                    .start(act, 101)
        }
        this.removeAll.onClick {
            clientFile = ""
            clientPwd = ""
            trustFile = ""
            trustPwd = ""
            trustType = ""
            client.setText("请选择...")
            trust.setText("请选择...")
        }
    }

    @ActivityResult(requestCode = 100)
    private fun afterClient(data: Intent?) {
        val path = data?.getStringExtra("path")
        if (path.isNullOrBlank()) return
        showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.title.text = "证书密码"
                contentView.content.text = "请输入证书密码："
                contentView.input.gravity = Gravity.CENTER
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val password = contentView.input.text()
                    val info = checkBks(path!!, password)
                    if (info != null) {
                        act.client.setText(info)
                        clientFile = path
                        clientPwd = password
                    } else {
                        showError("无效证书！")
                    }
                }
            }
        })
    }
    @ActivityResult(requestCode = 101)
    private fun afterTrust(data: Intent?) {
        val path = data?.getStringExtra("path")
        if (path.isNullOrBlank()) return
        showDialog(R.layout.dialog_hass_prompt, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.title.text = "证书密码"
                contentView.content.text = "请输入证书密码："
                contentView.input.gravity = Gravity.CENTER
            }
        }, intArrayOf(R.id.ok, R.id.cancel), object: OnDialogItemClickedListener {
            override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                dialog.dismiss()
                if (clickedView.id == R.id.ok) {
                    val password = contentView.input.text()
                    var info = checkBks(path!!, password)
                    if (info == null) {
                        info = checkX509(path, password)
                        trustType = "X.509"
                    } else {
                        trustType = "BKS"
                    }
                    if (info != null) {
                        act.trust.setText(info)
                        trustFile = path
                        trustPwd = password
                    } else {
                        showError("无效证书！")
                    }
                }
            }
        })
    }

    private fun checkBks(file: String, pwd: String, external: Boolean = true): String? {
        var fis: FileInputStream? = null
        try {
            val keyStore = KeyStore.getInstance("BKS")
            fis = if (external) FileInputStream(file) else openFileInput(file)
            keyStore.load(fis, pwd.toCharArray())
            return keyStore.provider?.info ?: keyStore.type
        } catch (ex: Exception) {
            ex.printStackTrace()
            return null
        } finally {
            fis?.close()
        }
    }
    private fun checkX509(file: String, pwd: String, external: Boolean = true): String? {
        var fis: FileInputStream? = null
        try {
            val cf = CertificateFactory.getInstance("X.509")
            fis = if (external) FileInputStream(file) else openFileInput(file)
            val ca = cf.generateCertificate(fis) as X509Certificate
            return ca.subjectDN?.name ?: ca.type
        } catch (ex: Exception) {
            ex.printStackTrace()
            toastex(ex.localizedMessage)
            return null
        } finally {
            fis?.close()
        }
    }

    private var clientFile = ""
    private var clientPwd = ""
    private var trustFile = ""
    private var trustType = ""
    private var trustPwd = ""
}

