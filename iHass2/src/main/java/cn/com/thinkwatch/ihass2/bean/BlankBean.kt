package cn.com.thinkwatch.ihass2.bean

import android.content.Context
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.model.JsonEntity

class BlankBean(): BaseBean(JsonEntity()) {
    override fun layoutResId(): Int = R.layout.tile_blank
    override fun bindToView(itemView: View, context: Context) {

    }
}