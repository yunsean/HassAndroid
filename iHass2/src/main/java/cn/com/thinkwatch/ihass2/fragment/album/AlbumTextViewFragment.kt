package cn.com.thinkwatch.ihass2.fragment.album

import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment
import kotlinx.android.synthetic.main.fragment_album_textview.*

class AlbumTextViewFragment : BaseFragment() {

    override val layoutResId: Int = R.layout.fragment_album_textview
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.name.text = arguments?.getString("name")
        this.size.text = arguments?.getString("size")
    }
}