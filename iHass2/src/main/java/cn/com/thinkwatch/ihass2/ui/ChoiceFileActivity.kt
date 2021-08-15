package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.hz.android.fileselector.FileSelectorView
import kotlinx.android.synthetic.main.activity_hass_choice_file.*
import org.jetbrains.anko.dip
import java.io.File


class ChoiceFileActivity : BaseActivity() {

    private var extesionNames = listOf("json")
    private var forFolder = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_choice_file)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        forFolder = intent.getBooleanExtra("forFolder", false)
        val title = intent.getStringExtra("title") ?: "选择配置文件"
        if (forFolder) setTitle(title, true, "确定")
        else setTitle(title, true)
        try { intent.getStringArrayListExtra("extensionNames") } catch (_: Exception) { null }?.let {
            extesionNames = it
        }
        ui()
    }

    private var choosedFild: File? = null
    override fun doRight() {
        choosedFild?.let {
            if (forFolder && !it.isDirectory) return
            setResult(Activity.RESULT_OK, Intent().putExtra("path", it.absolutePath))
            finish()
        } ?: showError("请选择路径！")
    }

    private fun ui() {
        var currentDirectory = intent.getStringExtra("currentDirectory") ?: Environment.getExternalStorageDirectory().absolutePath
        if (!File(currentDirectory).exists()) currentDirectory = Environment.getExternalStorageDirectory().absolutePath
        fileSelectorView.setCurrentDirectory(File(currentDirectory))
        fileSelectorView.setTextSize(16f)
        fileSelectorView.setTextColor(0xff555555.toInt())
        fileSelectorView.setIconSize(dip(28))
        fileSelectorView.setFileFilter { (!forFolder && extesionNames.contains(it.extension)) || it.isDirectory }
        fileSelectorView.setOnFileSelectedListener(object : FileSelectorView.OnFileSelectedListener {
            override fun onSelected(selectedFile: File?) {
                if (selectedFile == null) return
                setResult(Activity.RESULT_OK, Intent().putExtra("path", selectedFile.absolutePath))
                finish()
            }
            override fun onFilePathChanged(file: File?) {
                choosedFild = file
            }
        })
        fileSelectorView.setFileSortComparator(FileSelectorView.FileAscSortComparator())
    }
}

