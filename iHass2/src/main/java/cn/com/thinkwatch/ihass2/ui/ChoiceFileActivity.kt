package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import com.hz.android.fileselector.FileSelectorView
import kotlinx.android.synthetic.main.activity_hass_choice_json.*
import org.jetbrains.anko.dip
import java.io.File


class ChoiceFileActivity : BaseActivity() {

    private var extesionNames = listOf("json")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_choice_json)
        setTitle("选择配置文件", true)
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        try { intent.getStringArrayListExtra("extensionNames") } catch (_: Exception) { null }?.let {
            extesionNames = it
        }
        ui()
    }

    private fun ui() {
        fileSelectorView.setCurrentDirectory(File(Environment.getExternalStorageDirectory(), "/"))
        fileSelectorView.setTextSize(16f)
        fileSelectorView.setTextColor(0xff555555.toInt())
        fileSelectorView.setIconSize(dip(28))
        fileSelectorView.setFileFilter { extesionNames.contains(it.extension) || it.isDirectory }
        fileSelectorView.setOnFileSelectedListener(object : FileSelectorView.OnFileSelectedListener {
            override fun onSelected(selectedFile: File?) {
                if (selectedFile == null) return
                setResult(Activity.RESULT_OK, Intent().putExtra("path", selectedFile.absolutePath))
                finish()
            }
            override fun onFilePathChanged(file: File?) { }
        })
        fileSelectorView.setFileSortComparator(FileSelectorView.FileAscSortComparator())
    }
}

