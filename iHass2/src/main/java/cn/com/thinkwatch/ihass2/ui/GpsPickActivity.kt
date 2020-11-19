package cn.com.thinkwatch.ihass2.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.LocationChanged
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.geocode.*
import com.dylan.common.rx.RxBus2
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.yunsean.dynkotlins.extensions.activity
import kotlinx.android.synthetic.main.activity_hass_gps_pick.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.sdk25.coroutines.onClick


class GpsPickActivity : BaseActivity() {

    private var latlng: LatLng? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_gps_pick)
        setTitle("位置选取", true, "确定")

        geocoder = GeoCoder.newInstance()
        geoCodeOption = ReverseGeoCodeOption()
        geocoder?.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
            override fun onGetGeoCodeResult(p0: GeoCodeResult?) { }
            override fun onGetReverseGeoCodeResult(p0: ReverseGeoCodeResult?) {
                p0?.let { address.text = "${it.address}，${radius.progress}米范围内" }
            }
        })

        bmapView.onCreate(this, savedInstanceState)
        initMap()
        ui()

        if (intent.hasExtra("latitude") && intent.hasExtra("longitude")) {
            latlng = LatLng(intent.getDoubleExtra("latitude", .0), intent.getDoubleExtra("longitude", .0))
            bmapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latlng, 15f))
            showMarker()
        }
        radius.progress = intent.getIntExtra("radius", 500)
    }
    override fun onPause() {
        bmapView.onPause()
        super.onPause()
    }
    override fun onResume() {
        bmapView.onResume()
        super.onResume()
    }
    override fun onDestroy() {
        geocoder?.destroy()
        bmapView.onDestroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        bmapView.onSaveInstanceState(outState)
    }
    override fun doRight() {
        if (latlng == null) return showError("请选择地点！")
        val data = Intent()
                .putExtra("latitude", latlng?.latitude)
                .putExtra("longitude", latlng?.longitude)
                .putExtra("radius", radius.progress)
        setResult(Activity.RESULT_OK, data)
        finish()
    }

    private fun ui() {
        this.radius.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener{
            override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) {
            }
            override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) {
            }
            override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                showMarker()
            }
        })
        this.keyword.onClick {
            activity(PoiSearchActivity::class.java, 102)
        }
    }
    private var geocoder: GeoCoder? = null
    private lateinit var geoCodeOption: ReverseGeoCodeOption
    private fun initMap() {
        bmapView.map.apply {
            mapType = BaiduMap.MAP_TYPE_NORMAL
            setOnMapClickListener(object: BaiduMap.OnMapClickListener {
                override fun onMapClick(p0: LatLng?) {
                    latlng = p0
                    showMarker()
                }
                override fun onMapPoiClick(p0: MapPoi?): Boolean = true
            })
        }
        RequestPermissionResultDispatch.requestPermissions(this, 101, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
        disposable = RxBus2.getDefault().register(LocationChanged::class.java, {
            setLocation()
        }, disposable)
    }
    @RequestPermissionResult(requestCode = 101)
    private fun afterLocation() {
        if (app.location != null) setLocation()
        app.requestLocation()
    }

    @ActivityResult(requestCode = 102)
    private fun afterPoi(data: Intent?) {
        if (data == null || !data.hasExtra("latitude") || !data.hasExtra("longitude")) return
        latlng = LatLng(data.getDoubleExtra("latitude", .0), data.getDoubleExtra("longitude", .0))
        showMarker()
        bmapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latlng, 15f))
    }

    private fun setLocation() {
        if ( latlng != null ) return
        app.location?.let {
            latlng = LatLng(it.latitude, it.longitude)
            bmapView.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(latlng, 15f))
            showMarker()
        }
    }
    private fun showMarker() {
        if (latlng == null) return
        bmapView.map.apply {
            setMyLocationEnabled(true)
            val locData = MyLocationData.Builder()
                    .accuracy(radius.progress.toFloat())
                    .latitude(latlng!!.latitude)
                    .longitude(latlng!!.longitude).build()
            setMyLocationData(locData)
            val currentMarker = BitmapDescriptorFactory
                    .fromResource(cn.com.thinkwatch.ihass2.R.drawable.icon_geo)
            val config = MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, currentMarker)
            setMyLocationConfiguration(config)
        }
        address.text = "(${latlng?.latitude},${latlng?.longitude})，${radius.progress}米范围内"
        geoCodeOption.location(latlng)
        geocoder?.reverseGeoCode(geoCodeOption)
    }
}

