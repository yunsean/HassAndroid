package cn.com.thinkwatch.ihass2.fragment.xmly

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.widget.GridLayoutManager
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.view.animation.Transformation
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.network.external.xmlyApi
import cn.com.thinkwatch.ihass2.base.BaseFragment
import cn.com.thinkwatch.ihass2.bus.broadcast.XmlyFilterChange
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Animations
import com.dylan.common.sketch.Observers
import com.dylan.uiparts.annimation.MarginAnimation
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.fragment_hass_broadcast.*
import kotlinx.android.synthetic.main.fragment_hass_broadcast.view.*
import kotlinx.android.synthetic.main.listitem_hass_broadcast_filter.view.*
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.support.v4.ctx
import org.jetbrains.anko.support.v4.dip


class MainFragment : BaseFragment() {

    override val layoutResId: Int = R.layout.fragment_hass_broadcast
    private var entityId: String = ""
    private var dataDisposable: CompositeDisposable? = null
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        entityId = arguments?.getString("entityId") ?: ""

        ui()
        tabbar()
    }
    override fun onDestroy() {
        dataDisposable?.dispose()
        super.onDestroy()
    }

    private fun ui() {
        this.local.setText(ctx.readPref("xmly.provinceName", "北京", "DEFAULT"))
        this.category.setText(ctx.readPref("xmly.categoryName", "音乐台", "DEFAULT"))
        this.localDropdown.onClick {
            viewPager.currentItem = 1
            switchPanel(provincePanel, categoryPanel, localDropdown, categoryDropdown)
            if (loadProvince.visibility != View.GONE) {
                xmlyApi.getProvinceList()
                        .withNext {
                            loadProvince.visibility = View.GONE
                            provinceList.adapter = RecyclerAdapter(R.layout.listitem_hass_broadcast_filter, it.result) {
                                view, index, province ->
                                view.name.text = province.provinceName
                                view.onClick {
                                    switchPanel(provincePanel, categoryPanel, localDropdown, categoryDropdown, true)
                                    ctx.savePref(arrayOf("xmly.provinceCode", "xmly.provinceName"), arrayOf(province.provinceCode, province.provinceName))
                                    RxBus2.getDefault().post(XmlyFilterChange(XmlyFilterChange.Companion.FilterMode.province, province.provinceCode))
                                    fragment.local.text = province.provinceName
                                }
                            }
                            provinceList.layoutManager = GridLayoutManager(ctx, ctx.screenWidth() / dip(80))
                        }
                        .error {
                            it.toastex()
                            switchPanel(provincePanel, categoryPanel, localDropdown, categoryDropdown, true)
                        }
                        .subscribeOnMain {
                            if (dataDisposable == null) dataDisposable = CompositeDisposable(it)
                            else dataDisposable?.add(it)
                        }
            }
        }
        this.favorited.onClick {
            viewPager.currentItem = 0
        }
        this.localSegment.onClick {
            viewPager.currentItem = 1
        }
        this.categoryDropdown.onClick {
            viewPager.currentItem = 3
            switchPanel(categoryPanel, provincePanel, categoryDropdown, localDropdown)
            if (loadCategory.visibility != View.GONE) {
                xmlyApi.getCategoryList()
                        .withNext {
                            loadCategory.visibility = View.GONE
                            categoryList.adapter = RecyclerAdapter(R.layout.listitem_hass_broadcast_filter, it.data?.categories) {
                                view, index, category ->
                                view.name.text = category.name
                                view.onClick {
                                    switchPanel(categoryPanel, provincePanel, categoryDropdown, localDropdown, true)
                                    ctx.savePref(arrayOf("xmly.categoryCode", "xmly.categoryName"), arrayOf(category.id, category.name))
                                    RxBus2.getDefault().post(XmlyFilterChange(XmlyFilterChange.Companion.FilterMode.category, category.id))
                                    fragment.category.text = category.name
                                }
                            }
                            categoryList.layoutManager = GridLayoutManager(ctx, ctx.screenWidth() / dip(80))
                        }
                        .error {
                            it.toastex()
                            switchPanel(categoryPanel, provincePanel, categoryDropdown, localDropdown, true)
                        }
                        .subscribeOnMain {
                            if (dataDisposable == null) dataDisposable = CompositeDisposable(it)
                            else dataDisposable?.add(it)
                        }
            }
        }
        this.categorySegment.onClick {
            viewPager.currentItem = 3
        }
        this.country.onClick {
            viewPager.currentItem = 2
        }
        this.network.onClick {
            viewPager.currentItem = 4
        }
        this.categoryPanel.visibility = View.GONE
        this.provincePanel.visibility = View.GONE
    }
    private fun switchPanel(show: View, hide: View, showArrow: ImageView, hideArrow: ImageView, forceHide: Boolean = false) {
        if (show.visibility == View.VISIBLE || forceHide) {
            Animations.MarginAnimation(show, MarginAnimation.Margin.Bottom, 0, viewPager.height)
                    .duration(300)
                    .animationListener { show.visibility = View.GONE }
                    .start()
            Animations.RotateAnimation(showArrow, 180f, 0f, RotateAnimation.RELATIVE_TO_SELF, .5f, RotateAnimation.RELATIVE_TO_SELF, .5f)
                    .duration(300)
                    .fillAfter(true)
                    .start()
        } else {
            if (hide.visibility != View.GONE) {
                Animations.MarginAnimation(hide, MarginAnimation.Margin.Bottom, 0, viewPager.height)
                        .duration(300)
                        .animationListener { hide.visibility = View.GONE }
                        .start()
                Animations.RotateAnimation(hideArrow, 180f, 0f, RotateAnimation.RELATIVE_TO_SELF, .5f, RotateAnimation.RELATIVE_TO_SELF, .5f)
                        .duration(300)
                        .fillAfter(true)
                        .start()
            }
            show.visibility = View.VISIBLE
            Animations.MarginAnimation(show, MarginAnimation.Margin.Bottom, viewPager.height, 0)
                    .duration(300)
                    .start()
            Animations.RotateAnimation(showArrow, 0f, 180f, RotateAnimation.RELATIVE_TO_SELF, .5f, RotateAnimation.RELATIVE_TO_SELF, .5f)
                    .duration(300)
                    .fillAfter(true)
                    .start()
            show.onClick { switchPanel(show, hide, showArrow, hideArrow, true) }
        }
    }
    private fun tabbar() {
        Observers.observeLayout(viewPager) { v ->
            val lp = flags.layoutParams as ViewGroup.MarginLayoutParams
            lineWidth = v.width / 5
            marginOffset = 0
            lp.leftMargin = marginOffset
            lp.width = lineWidth
            updateTab()
            false
        }
        viewPager.adapter = object : FragmentPagerAdapter(childFragmentManager) {
            override fun getPageTitle(position: Int): CharSequence = titleName.get(position)
            override fun getCount(): Int = titleName.size
            override fun getItem(position: Int): Fragment = BaseFragment.newInstance(if (position == 0) FavoriteFragment::class.java else ChannelFragment::class.java,
                    Intent().putExtra("type", titleView.get(position)).putExtra("entityId", entityId))
        }
        viewPager.addOnPageChangeListener(object: ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) { }
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) { }
            override fun onPageSelected(position: Int) {
                updateTab()
            }
        })
        viewPager.setCanScroll(true)
    }

    private val titleName = arrayOf("收藏", "地方台", "国家台", "特色台", "网络台")
    private val titleView = intArrayOf(R.id.favorited, R.id.local, R.id.country, R.id.category, R.id.network)
    private var marginOffset = 0
    private var lineWidth = 0
    private fun updateTab() {
        titleView.forEachIndexed { index, it ->
            fragment.findViewById<TextView>(it).isActivated = viewPager.currentItem == index
        }
        val lp = flags.layoutParams as ViewGroup.MarginLayoutParams
        val fromX = lp.leftMargin
        val toX = lineWidth * viewPager.currentItem + marginOffset
        val anim = object : Animation() {
            override fun applyTransformation(interpolatedTime: Float, t: Transformation) {
                lp.leftMargin = (fromX + (toX - fromX) * interpolatedTime).toInt()
                flags.requestLayout()
            }
            override fun willChangeBounds(): Boolean = true
        }
        anim.duration = 300
        flags.startAnimation(anim)
    }
}