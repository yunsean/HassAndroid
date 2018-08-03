package cn.com.thinkwatch.ihass2.fragment

import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.api.hassApi
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.JsonEntity
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.text
import com.yunsean.dynkotlins.extensions.toastex
import kotlinx.android.synthetic.main.pager_hass_camera_tts.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx


class CameraTtsFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.pager_hass_camera_tts
    private var entityId: String? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        entityId = arguments?.getString("entityId", "")

        ui()
    }

    private fun ui() {
        val entity = db.getEntity(entityId ?: "")
        entity?.let { content.setText(entity.state) }
        this.send.onClick {
            val text = content.text().trim()
            if (text.isBlank()) return@onClick ctx.toastex("请输入文字内容")
            hassApi.setState(app.haPassword, entityId, JsonEntity(state = text))
                    .nextOnMain {
                        ctx.toastex("已发送")
                    }
                    .error {
                        it.toastex()
                    }
        }
    }
}