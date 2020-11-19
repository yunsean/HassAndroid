package cn.com.thinkwatch.ihass2.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.inputmethod.EditorInfo
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.LocationChanged
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.PoiInfo
import com.baidu.mapapi.search.geocode.*
import com.baidu.mapapi.search.poi.*
import com.dylan.common.rx.RxBus2
import com.dylan.common.utils.Utility
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.dip2px
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import kotlinx.android.synthetic.main.activity_hass_search.*
import kotlinx.android.synthetic.main.listitem_hass_poi.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.sdk25.coroutines.onClick

class PoiSearchActivity : BaseActivity(), OnGetPoiSearchResultListener, OnGetGeoCoderResultListener {

    private var mCity: String? = null
    private var mKeyword: String = ""
    private var mPoiSearch: PoiSearch? = null
    private var geocoder: GeoCoder? = null
    private lateinit var geoCodeOption: ReverseGeoCodeOption
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_search)
        setTitle("位置选取", true)

        mPoiSearch = PoiSearch.newInstance()
        mPoiSearch?.setOnGetPoiSearchResultListener(this)
        geocoder = GeoCoder.newInstance()
        geoCodeOption = ReverseGeoCodeOption()
        geocoder?.setOnGetGeoCodeResultListener(this)
        ui()
        disposable = RxBus2.getDefault().register(LocationChanged::class.java, {
            app.location?.let {
                geoCodeOption.location(LatLng(it.latitude, it.longitude))
                geocoder?.reverseGeoCode(geoCodeOption)
            }
        }, disposable)
    }
    override fun onDestroy() {
        mPoiSearch?.destroy()
        super.onDestroy()
    }

    private var poiResult = mutableListOf<PoiInfo>()
    private lateinit var adapter: RecyclerAdapter<PoiInfo>
    private fun ui() {
        this.rootView?.onClick { finish() }
        this.keyword.setOnEditorActionListener { textView, i, keyEvent ->
            if (i == EditorInfo.IME_ACTION_SEARCH) {
                val keyword = this@PoiSearchActivity.keyword.text.toString().trim()
                if (!keyword.isBlank()) doSearch(keyword)
                Utility.hideSoftKeyboard(act)
            }
            false
        }
        this.adapter = RecyclerAdapter<PoiInfo>(R.layout.listitem_hass_poi, poiResult) {
            view, index, poi ->
            view.name.text = poi.name
            view.detail.text = poi.address
            view.onClick {
                setResult(Activity.RESULT_OK, Intent()
                        .putExtra("latitude", poi.location.latitude)
                        .putExtra("longitude", poi.location.longitude))
                finish()
            }
        }
        this.recyclerView.adapter = this.adapter
        this.recyclerView.layoutManager = LinearLayoutManager(this)
        this.recyclerView.addItemDecoration(RecyclerViewDivider()
                .setSize(1)
                .setColor(0xffeeeeee.toInt())
                .setMargin(dip2px(10f), 0))
        this.gifView.setGifImage(R.drawable.location)
        this.pullable?.setOnRefreshListener {
            pageIndex = 0
            data()
        }
        this.pullable?.setOnLoadMoreListener {
            data()
        }
        this.pullable?.isRefreshEnabled = true
        app.location?.let {
            loading.visibility = View.GONE
            RequestPermissionResultDispatch.requestPermissions(this, 101, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
        }
    }

    @RequestPermissionResult(requestCode = 101)
    private fun afterLocation(result: Boolean) {
        if (!result) return finish()
        app.location?.let {
            geoCodeOption.location(LatLng(it.latitude, it.longitude))
            geocoder?.reverseGeoCode(geoCodeOption)
        }
    }

    private fun doSearch(keyword: String) {
        this.keyword.setText(keyword)
        if (keyword.isBlank()) return
        mKeyword = keyword
        pullable?.isRefreshing = true
    }
    private var pageIndex = 0
    private fun data() {
        if (pageIndex == 0) {
            poiResult.clear()
            adapter.notifyDataSetChanged()
        }
        mPoiSearch?.searchInCity(PoiCitySearchOption()
                .city(mCity)
                .cityLimit(false)
                .pageCapacity(20)
                .pageNum(pageIndex++)
                .keyword(mKeyword))
    }

    override fun onGetPoiResult(p0: PoiResult?) {
        pullable?.isRefreshing = false
        pullable?.isLoadingMore = false
        pullable?.isLoadMoreEnabled = false
        p0?.let {
            it.allPoi?.let { poiResult.addAll(it) }
            adapter.notifyDataSetChanged()
            pullable?.isLoadMoreEnabled = it.totalPageNum > it.currentPageNum
        }
    }
    override fun onGetPoiIndoorResult(p0: PoiIndoorResult?) = Unit
    override fun onGetPoiDetailResult(p0: PoiDetailResult?) = Unit
    override fun onGetPoiDetailResult(p0: PoiDetailSearchResult?) = Unit

    override fun onGetGeoCodeResult(p0: GeoCodeResult?) = Unit
    override fun onGetReverseGeoCodeResult(p0: ReverseGeoCodeResult?) {
        if (p0 == null) return
        mCity = p0.addressDetail.city
        setTitle("位置选取", true, mCity)
        loading.visibility = View.GONE
    }
}

