package cn.com.thinkwatch.ihass2.ui

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.view.View.MeasureSpec
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.app
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.bus.EntityChanged
import cn.com.thinkwatch.ihass2.bus.LocationChanged
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.model.MDIFont
import cn.com.thinkwatch.ihass2.utils.ZonedDateAsTime
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.geocode.*
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.SimpleTarget
import com.bumptech.glide.request.transition.Transition
import com.dylan.common.data.GpsUtil
import com.dylan.common.rx.RxBus2
import com.dylan.common.sketch.Actions
import com.dylan.uiparts.activity.RequestPermissionResult
import com.dylan.uiparts.activity.RequestPermissionResultDispatch
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.OnSettingDialogListener
import com.yunsean.dynkotlins.extensions.showDialog
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_map.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import kotlinx.android.synthetic.main.griditem_location_marker.view.*
import kotlinx.android.synthetic.main.layout_home_overlay.view.*
import kotlinx.android.synthetic.main.location_marker.view.*
import kotlinx.android.synthetic.main.location_overlay.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.dip
import org.jetbrains.anko.sdk25.coroutines.onClick

class MapActivity : BaseActivity() {

    private data class Tracker(val entityId: String,
                               val name: String,
                               val icon: String?,
                               val isHome: Boolean,
                               val latLng: LatLng,
                               val geoLatLng: LatLng,
                               var address: String?,
                               val isGps: Boolean,
                               val lastUpdated: String?,
                               val picture: String?,
                               var drawable: Drawable? = null,
                               var neighbors: MutableList<Tracker>? = null)

    private var homeLatlng: LatLng? = null
    private var homeLatlngs = mutableMapOf<LatLng, String>()
    private lateinit var trackers: List<Tracker>
    private var showing: List<Tracker>? = null
    private var current: Tracker? = null
    private var entityId: String = ""
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_map)
        bmapView.onCreate(this, savedInstanceState)
        val entityId = intent.getStringExtra("entityId")
        if (entityId.isNullOrBlank()) return finish()
        initData(entityId)
        initMap()
        disposable = RxBus2.getDefault().register(EntityChanged::class.java, { event->
            if (showing?.find { it.entityId == event.entityId } != null) {
                initData(this.entityId)
            }
        }, disposable)
    }
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val entityId = intent?.getStringExtra("entityId")
        if (entityId != null && entityId.isNotBlank()) {
            initData(entityId)
        }
    }
    private fun initMap() {
        bmapView.map.apply {
            mapType = BaiduMap.MAP_TYPE_NORMAL
            current?.latLng?.apply { animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(this, 18f)) }
            setOnMarkerClickListener {
                val entityId = it.extraInfo?.getString("entityId")
                showing?.find { it.entityId.equals(entityId) }.let {
                    if (it == null) return@let
                    if (it.neighbors?.size ?: 0 > 1) showList(it)
                    else { current = it; showInfo() }
                }
                false
            }
            setOnMapLoadedCallback {
                updateMarker()
            }
            setOnMapStatusChangeListener(object: BaiduMap.OnMapStatusChangeListener {
                private var originZoom = 18f
                override fun onMapStatusChange(p0: MapStatus?) { }
                override fun onMapStatusChangeFinish(p0: MapStatus?) {
                    if (Math.abs(originZoom - (p0?.zoom ?: 0f)) > 0.3) {
                        originZoom = p0?.zoom ?: 0f
                        updateMarker()
                    }
                }
                override fun onMapStatusChangeStart(p0: MapStatus?) { }
                override fun onMapStatusChangeStart(p0: MapStatus?, p1: Int) { }
            })
            setOnMapClickListener(object: BaiduMap.OnMapClickListener {
                override fun onMapClick(p0: LatLng?) {
                    bmapView.map.hideInfoWindow()
                }
                override fun onMapPoiClick(p0: MapPoi?): Boolean = false
            })
        }
        RequestPermissionResultDispatch.requestPermissions(this, 101, arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.READ_PHONE_STATE))
        disposable = RxBus2.getDefault().register(LocationChanged::class.java, {
            updateMarker()
        }, disposable)
    }
    private fun initData(entityId: String) {
        this.entityId = entityId
        db.listEntity("zone.%").forEach {
            if (it.attributes?.latitude != null && it.attributes?.longitude != null) {
                val home = GpsUtil.WGS2GCJ(GpsUtil.LatLng(it.attributes?.latitude?.toDouble() ?: .0, it.attributes?.longitude?.toDouble() ?: .0))
                homeLatlngs.put(LatLng(home.latitude, home.longitude), it.friendlyName)
            }
        }
        db.getEntity("zone.home")?.attributes?.let {
            if (it.latitude != null && it.longitude != null) {
                val point = GpsUtil.WGS2GCJ(GpsUtil.LatLng(it.latitude?.toDouble() ?: .0, it.longitude?.toDouble() ?: .0))
                homeLatlng = LatLng(point.latitude, point.longitude)
            }
        }
        trackers = db.listEntity("device_tracker.%")
                .filter { it.attributes?.latitude != null && it.attributes?.longitude != null }
                .map {
                    val point = GpsUtil.WGS2GCJ(GpsUtil.LatLng(it.attributes?.latitude?.toDouble() ?: .0, it.attributes?.longitude?.toDouble() ?: .0))
                    val latLng = LatLng(point.latitude, point.longitude)
                    val tracker = Tracker(it.entityId,
                            it.friendlyName,
                            if (it.showIcon.isNullOrBlank()) it.iconState else it.showIcon,
                            it.isActivated,
                            latLng,
                            LatLng(it.attributes?.latitude?.toDouble() ?: .0, it.attributes?.longitude?.toDouble() ?: .0),
                            null,
                            "gps".equals(it.attributes?.sourceType),
                            it.lastUpdated,
                            it.attributes?.entityPicture)
                    if (!it.attributes?.entityPicture.isNullOrBlank()) {
                        Glide.with(act).load(it.attributes?.entityPicture)
                                .into(object: SimpleTarget<Drawable>() {
                                    override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                                        tracker.drawable = resource
                                        updateMarker()
                                    }
                                })
                    }
                    tracker
                }
        current = trackers.find { it.entityId.equals(entityId) }
    }
    override fun doRight() {
        try {
            current?.geoLatLng?.let { Actions.map(this, it.longitude, it.latitude) }
        } catch (_: Exception) {
            showError("未找到可用的导航软件！")
        }
    }
    private fun getBitmap(view: View, width: Int = 0, height: Int = 0): Bitmap {
        view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight())
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888);
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
    private fun showList(tracker: Tracker) {
        showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
            override fun onSettingDialog(dialog: Dialog, contentView: View) {
                contentView.recyclerView.layoutManager = LinearLayoutManager(act)
                val adaptar = RecyclerAdapter(R.layout.griditem_location_marker, tracker.neighbors) {
                    view, index, item ->
                    view.trackerCircle.isActivated = item.isHome
                    if (item.drawable != null) {
                        view.trackerPicture.setImageDrawable(item.drawable)
                        view.trackerPicture.visibility = View.VISIBLE
                        view.trackerIcon.visibility = View.GONE
                    } else {
                        MDIFont.get().setIcon(view.trackerIcon, item.icon)
                        view.trackerPicture.visibility = View.GONE
                        view.trackerIcon.visibility = View.VISIBLE
                    }
                    view.trackerName.text = item.name
                    view.trackerName.isActivated = item.isHome
                    view.onClick {
                        current = item
                        showInfo()
                        dialog.dismiss()
                    }
                }
                val footer = layoutInflater.inflate(R.layout.layout_cancel, contentView.recyclerView, false)
                footer.onClick { dialog.dismiss() }
                contentView.recyclerView.adapter = RecyclerAdapterWrapper(adaptar)
                        .addFootView(footer)
                contentView.recyclerView.addItemDecoration(RecyclerViewDivider()
                        .setColor(0xffeeeeee.toInt())
                        .setSize(1))
            }
        }, null, null)
    }
    private var toHomeLine: Overlay? = null
    private var toYouLine: Overlay? = null
    private fun showInfo() {
        current?.let {
            setTitle("${it.name}的位置", true, "导航")
            toHomeLine?.remove()
            toYouLine?.remove()

            val layout = layoutInflater.inflate(R.layout.location_overlay, null, false)
            layout.title.setText(it.name)
            layout.title.isActivated = it.isGps
            layout.updateAt.visibility = if (it.lastUpdated == null) View.GONE else View.VISIBLE
            it.lastUpdated?.let { layout.updateAt.setText("更新：${ZonedDateAsTime().render(it)}") }
            layout.address.visibility = if (it.address.isNullOrBlank()) View.GONE else View.VISIBLE
            layout.address.setText("位于：${it.address}")
            layout.distance.visibility = if (app.location == null) View.GONE else View.VISIBLE
            layout.toHome.visibility = View.GONE
            app.location?.apply {
                layout.distance.setText(String.format("离你：%.1f km", getDistance(it.latLng, LatLng(latitude, longitude))))
                toYouLine = drawLine(it.latLng, LatLng(latitude, longitude), 0xffff0000.toInt())
            }
            homeLatlng?.apply {
                layout.toHome.setText(String.format("离家：%.1f km", getDistance(it.latLng, this)))
                layout.toHome.visibility = View.VISIBLE
                toHomeLine = drawLine(it.latLng, this, 0xff0000ff.toInt())
            }
            val popupWindow = InfoWindow(layout, it.latLng, -dip(36))
            bmapView.map.showInfoWindow(popupWindow)

            if (it.address == null) {
                val geocoder = GeoCoder.newInstance()
                val op = ReverseGeoCodeOption()
                op.location(it.latLng)
                geocoder.setOnGetGeoCodeResultListener(object : OnGetGeoCoderResultListener {
                    override fun onGetGeoCodeResult(p0: GeoCodeResult?) { }
                    override fun onGetReverseGeoCodeResult(p0: ReverseGeoCodeResult?) {
                        p0?.apply {
                            it.address = address
                            showInfo()
                        }
                    }
                })
                geocoder.reverseGeoCode(op)
            }
        }
    }
    private fun distance(left: LatLng, right: LatLng): Double {
        return Math.abs(left.longitude - right.longitude) + Math.abs(left.latitude - right.latitude)
    }
    private fun updateMarker() {
        val map = bmapView.map
        val items = map.projection?.let {
            val topLeft = map.projection.fromScreenLocation(Point(0, 0))
            val bottomRight = map.projection.fromScreenLocation(Point(bmapView.width, bmapView.height))
            val threshold = distance(topLeft, bottomRight) / dip(15)
            val items = mutableListOf<Tracker>()
            trackers.forEach {
                pending->
                val neighbor = items.find { distance(pending.latLng, it.latLng) < threshold }
                if (neighbor != null) {
                    if (neighbor.neighbors == null) neighbor.neighbors = mutableListOf(neighbor)
                    neighbor.neighbors?.add(pending)
                } else {
                    items.add(pending)
                    pending.neighbors = null
                }
            }
            items.sortWith(Comparator { lhs, rhs-> (lhs.neighbors?.size ?: 0) - (rhs.neighbors?.size ?: 0) })
            items
        } ?: trackers
        this.showing = items

        map.clear()
        homeLatlngs.forEach {
            val layout = layoutInflater.inflate(R.layout.layout_home_overlay, null, false)
            layout.zoneName.text = it.value
            val bitmap = BitmapDescriptorFactory
                    .fromBitmap(getBitmap(layout))
            val option = MarkerOptions()
                    .position(it.key)
                    .icon(bitmap)
            map.addOverlay(option)
        }
        app.location?.let {
            val bitmap = BitmapDescriptorFactory
                    .fromResource(cn.com.thinkwatch.ihass2.R.drawable.icon_geo)
            val option = MarkerOptions()
                    .position(LatLng(it.latitude, it.longitude))
                    .icon(bitmap)
            map.addOverlay(option)
        }
        items.forEach {
            val layout = layoutInflater.inflate(R.layout.location_marker, null, false)
            if (it.drawable == null) {
                MDIFont.get().setIcon(layout.icon, it.icon)
                layout.picture.visibility = View.GONE
            } else {
                layout.picture.setImageDrawable(it.drawable)
                layout.icon.visibility = View.GONE
            }
            layout.isActivated = it.isHome
            layout.neighbor.visibility = if (it.neighbors?.size ?: 0 > 0) View.VISIBLE else View.GONE
            layout.neighbor.text = if (it.neighbors?.size ?: 0 > 9) "9" else it.neighbors?.size.toString()
            val bitmap = BitmapDescriptorFactory
                    .fromBitmap(getBitmap(layout))
            val option = MarkerOptions()
                    .position(it.latLng)
                    .icon(bitmap)
            val extraInfo = Bundle()
            extraInfo.putString("entityId", it.entityId)
            map.addOverlay(option).extraInfo = extraInfo
        }
        showInfo()
    }
    fun getDistance(start: LatLng, end: LatLng?): Double {
        if (end == null) return 0.0
        val lon1 = Math.PI / 180 * start.longitude
        val lon2 = Math.PI / 180 * end.longitude
        val lat1 = Math.PI / 180 * start.latitude
        val lat2 = Math.PI / 180 * end.latitude
        val R = 6371.0 // 地球半径
        return Math.acos(Math.sin(lat1) * Math.sin(lat2) + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon2 - lon1)) * R
    }
    fun drawLine(start: LatLng, end: LatLng?, color: Int): Overlay? {
        if (end == null) return null
        val points = ArrayList<LatLng>()
        points.add(start)
        points.add(end)
        val ooPolyline = PolylineOptions().width(2).color(color).points(points)
        return bmapView.map.addOverlay(ooPolyline)
    }

    @RequestPermissionResult(requestCode = 101)
    private fun afterLocation() {
        if (app.location != null) updateMarker()
        app.requestLocation()
    }

    override fun onPause() {
        bmapView.onPause()
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        bmapView.onResume()
    }
    override fun onDestroy() {
        bmapView.onDestroy()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        bmapView.onSaveInstanceState(outState)
    }
}

