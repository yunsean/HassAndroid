package cn.com.thinkwatch.ihass2.utils

import android.support.v4.app.*
import android.support.v4.view.ViewPager

object BackHandlerHelper {

    interface FragmentBackHandler {
        fun onBackPressed(): Boolean
    }

    fun handleBackPress(fragment: Fragment): Boolean = handleBackPress(fragment.childFragmentManager)
    fun handleBackPress(fragmentActivity: FragmentActivity): Boolean = handleBackPress(fragmentActivity.supportFragmentManager)
    fun handleBackPress(viewPager: ViewPager?): Boolean {
        if (viewPager == null) return false
        val adapter = viewPager.adapter ?: return false
        val currentItem = viewPager.currentItem
        val fragment: Fragment?
        fragment = if (adapter is FragmentPagerAdapter) {
            adapter.getItem(currentItem)
        } else if (adapter is FragmentStatePagerAdapter) {
            adapter.getItem(currentItem)
        } else {
            null
        }
        return isFragmentBackHandled(fragment)
    }

    private fun isFragmentBackHandled(fragment: Fragment?): Boolean {
        return (fragment != null && fragment.isVisible
                && fragment.userVisibleHint
                && fragment is FragmentBackHandler
                && (fragment as FragmentBackHandler).onBackPressed())
    }
    private fun handleBackPress(fragmentManager: FragmentManager): Boolean {
        val fragments = fragmentManager.fragments ?: return false
        for (i in fragments.indices.reversed()) {
            val child = fragments[i]
            if (isFragmentBackHandled(child)) {
                return true
            }
        }
        if (fragmentManager.backStackEntryCount > 0) {
            fragmentManager.popBackStack()
            return true
        }
        return false
    }
}