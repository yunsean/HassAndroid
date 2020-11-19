package cn.com.thinkwatch.ihass2.ui

import android.app.Activity
import android.app.Dialog
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Vibrator
import android.support.v7.widget.GridLayoutManager
import android.support.v7.widget.LinearLayoutManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.enums.AlarmSoundType
import cn.com.thinkwatch.ihass2.enums.AlarmVibrateType
import cn.com.thinkwatch.ihass2.enums.ConditionType
import cn.com.thinkwatch.ihass2.model.Observed
import com.dylan.dyn3rdparts.pickerview.DateTimePicker
import com.dylan.uiparts.activity.ActivityResult
import com.dylan.uiparts.recyclerview.RecyclerViewDivider
import com.yunsean.dynkotlins.extensions.*
import com.yunsean.dynkotlins.ui.RecyclerAdapter
import com.yunsean.dynkotlins.ui.RecyclerAdapterWrapper
import kotlinx.android.synthetic.main.activity_hass_observed_edit.*
import kotlinx.android.synthetic.main.dialog_list_view.view.*
import org.jetbrains.anko.act
import org.jetbrains.anko.ctx
import org.jetbrains.anko.sdk25.coroutines.onClick
import java.util.*

class ObservedEditActivity : BaseActivity() {

    private var obervableId: Long = 0L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hass_observed_edit)
        setTitle("状态通知", true, "保存")
        setAutoHideSoftInput(AutoHideSoftInputMode.WhenClick)

        obervableId = intent.getLongExtra("obervableId", 0L)
        ui()
        sound.setText(mSoundType.desc)
        vibrate.setText(mVibrateType.desc)
        if (obervableId != 0L) {
            db.getObserved(obervableId)?.let {e->
                name.setText(e.name)
                mEntityId = e.entityId
                entity.setText(e.entityName)
                mCondition = e.condition
                state.visibility = if (mCondition == ConditionType.any) View.GONE else View.VISIBLE
                condition.setText(e.condition.desc)
                state.setText(e.state)
                mBeginTime = e.beginTime
                mEndTime = e.endTime
                beginTime.setText(mBeginTime?.kdateTime("HH:mm:ss") ?: "")
                endTime.setText(mEndTime?.kdateTime("HH:mm:ss") ?: "")
                mImageIndex = if (e.image >= 0 && e.image < ImageResIds.size) e.image else 0
                image.setImageResource(ImageResIds.get(mImageIndex).first)
                mSoundType = e.sound
                sound.setText(e.sound.desc)
                mVibrateType = e.vibrate
                vibrate.setText(e.vibrate.desc)
                insistant.isChecked = e.insistent
            }
        }
    }
    override fun onPause() {
        stop()
        super.onPause()
    }
    override fun doRight() {
        if (mEntityId.isNullOrBlank()) return showError("请选择需要监听的元素！")
        if (mCondition == null) return showError("请选择监听条件！")
        val name = this.name.text()
        if (name.isBlank()) return showError("请输入名称！")
        val state = this.state.text()
        if (mCondition != ConditionType.any && mCondition != ConditionType.inc && mCondition != ConditionType.dec && state.isBlank()) return showError("请输入比对参考值！")
        val insistant = this.insistant.isChecked
        val observed = Observed(name, mEntityId!!, mCondition!!, state, mBeginTime, mEndTime, mSoundType, mImageIndex, mVibrateType, insistant, false, obervableId)
        if (obervableId == 0L) db.addObserved(observed)
        else db.saveObserved(observed)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private var mBeginTime: Date? = null
    private var mEndTime: Date? = null
    private var mEntityId: String? = null
    private var mCondition: ConditionType? = null
    private var mSoundType: AlarmSoundType = AlarmSoundType.quiet
    private var mVibrateType: AlarmVibrateType = AlarmVibrateType.quiet
    private var mImageIndex: Int = R.mipmap.ic_launcher
    private fun ui() {
        this.condition.onClick {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                    val adaptar = RecyclerAdapter(R.layout.listitem_hass_observed_condition, ConditionType.values().asList()) {
                        view, index, item ->
                        view.findViewById<TextView>(R.id.name).setText(item.desc)
                        view.onClick {
                            mCondition = item
                            condition.setText(item.desc)
                            state.hint = item.tips
                            state.visibility = if (item == ConditionType.any) View.GONE else View.VISIBLE
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
        this.sound.onClick {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                    val adaptar = RecyclerAdapter(R.layout.listitem_hass_alarm_sound, AlarmSoundType.values().asList()) {
                        view, index, item ->
                        view.findViewById<TextView>(R.id.name).setText(item.desc)
                        view.findViewById<View>(R.id.play).visibility == if (item.resId == 0) View.GONE else View.VISIBLE
                        view.findViewById<View>(R.id.play).onClick {
                            if (item.resId != 0) play(item.resId)
                            else stop()
                        }
                        view.onClick {
                            mSoundType = item
                            sound.setText(item.desc)
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
            }, null, null, dismiss = object: OnDismissListener {
                override fun onDismiss() {
                    stop()
                }
            })
        }
        this.vibrate.onClick {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = LinearLayoutManager(ctx)
                    val adaptar = RecyclerAdapter(R.layout.listitem_hass_alarm_sound, AlarmVibrateType.values().asList()) {
                        view, index, item ->
                        view.findViewById<TextView>(R.id.name).setText(item.desc)
                        view.findViewById<View>(R.id.play).visibility == if (item.vibrate.size < 1) View.GONE else View.VISIBLE
                        view.findViewById<View>(R.id.play).onClick {
                            if (item.vibrate.size > 0) play(item.vibrate)
                            else stop()
                        }
                        view.onClick {
                            mVibrateType = item
                            vibrate.setText(item.desc)
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
            }, null, null, dismiss = object: OnDismissListener {
                override fun onDismiss() {
                    stop()
                }
            })
        }
        this.imagePanel.onClick {
            showDialog(R.layout.dialog_list_view, object: OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    contentView.recyclerView.layoutManager = GridLayoutManager(ctx, 4)
                    val adaptar = RecyclerAdapter(R.layout.griditem_alarm_image, ImageResIds) {
                        view, index, item ->
                        view.findViewById<ImageView>(R.id.image).setImageResource(item.first)
                        view.onClick {
                            mImageIndex = index
                            image.setImageResource(item.first)
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
        this.beginTime.onClick {
            createDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    mBeginTime?.let { contentView.findViewById<DateTimePicker>(R.id.picker).setDate(it) }
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        contentView.findViewById<TextView>(R.id.title).setText("")
                        mBeginTime = contentView.findViewById<DateTimePicker>(R.id.picker).date.time
                        beginTime.setText(mBeginTime?.kdateTime("HH:mm:ss"))
                    } else {
                        mBeginTime = null
                        beginTime.setText("")
                    }
                    dialog.dismiss()
                }
            }).show()
        }
        this.endTime.onClick {
            createDialog(R.layout.dialog_choice_time, object : OnSettingDialogListener {
                override fun onSettingDialog(dialog: Dialog, contentView: View) {
                    mEndTime?.let { contentView.findViewById<DateTimePicker>(R.id.picker).setDate(it) }
                }
            }, intArrayOf(R.id.ok, R.id.cancel), object : OnDialogItemClickedListener {
                override fun onClick(dialog: Dialog, contentView: View, clickedView: View) {
                    if (clickedView.id == R.id.ok) {
                        contentView.findViewById<TextView>(R.id.title).setText("")
                        mEndTime = contentView.findViewById<DateTimePicker>(R.id.picker).date.time
                        endTime.setText(mEndTime?.kdateTime("HH:mm:ss"))
                    } else {
                        mEndTime = null
                        endTime.setText("")
                    }
                    dialog.dismiss()
                }
            }).show()
        }
        this.entity.onClick {
            Intent(act, EntityListActivity::class.java)
                    .putExtra("singleOnly", true)
                    .start(act, 105)
        }
    }

    private var mPlayer: MediaPlayer? = null
    private var mVibrator: Vibrator? = null
    private fun play(resId: Int) {
        mPlayer?.stop()
        mPlayer = MediaPlayer.create(this, resId)
        mPlayer?.start()
    }
    private fun play(vibrate: LongArray) {
        mVibrator?.cancel()
        mVibrator = getSystemService(Service.VIBRATOR_SERVICE) as Vibrator
        mVibrator?.vibrate(vibrate, -1)
    }
    private fun stop() {
        mPlayer?.stop()
        mVibrator?.cancel()
    }

    @ActivityResult(requestCode = 105)
    private fun entityPicked(data: Intent?) {
        data?.getStringArrayExtra("entityIds")?.let {
            if (it.size < 1) return@let
            mEntityId = it.get(0)
            db.getEntity(mEntityId!!)?.let {
                entity.setText(it.friendlyName)
                state.setText(it.state)
            }
        }
    }

    companion object {
        val ImageResIds = listOf(R.drawable.shell1 to R.drawable.shell1,
                R.drawable.shell2 to R.drawable.shell2,
                R.drawable.shell3 to R.drawable.shell3,
                R.drawable.shell4 to R.drawable.shell4,
                R.drawable.shell5 to R.drawable.shell5,
                R.drawable.shell6 to R.drawable.shell6,
                R.drawable.shell7 to R.drawable.shell7,
                R.drawable.shell8 to R.drawable.shell8,
                R.drawable.shell9 to R.drawable.shell9)
    }
}

