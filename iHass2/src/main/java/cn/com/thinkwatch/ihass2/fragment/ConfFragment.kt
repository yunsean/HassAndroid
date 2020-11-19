package cn.com.thinkwatch.ihass2.fragment

import android.os.Bundle
import android.view.View
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseFragment


class ConfFragment : BaseFragment() {
    override val layoutResId: Int = R.layout.fragment_hass_conf
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        childFragmentManager.beginTransaction()
                .replace(R.id.fragment, HttpFragment())
                .commit()
    }
}

