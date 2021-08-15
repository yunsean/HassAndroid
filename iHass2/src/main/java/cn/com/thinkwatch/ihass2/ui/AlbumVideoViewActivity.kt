package cn.com.thinkwatch.ihass2.ui

import android.content.res.Configuration
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.support.v4.content.ContextCompat
import android.view.SurfaceHolder
import android.view.View
import android.widget.FrameLayout
import cn.com.thinkwatch.ihass2.R
import cn.com.thinkwatch.ihass2.base.BaseActivity
import cn.com.thinkwatch.ihass2.db.db
import cn.com.thinkwatch.ihass2.utils.cfg
import com.dylan.common.sketch.Observers
import com.yunsean.dynkotlins.extensions.ktime
import com.yunsean.dynkotlins.extensions.logws
import com.yunsean.dynkotlins.extensions.nextOnMain
import com.yunsean.dynkotlins.extensions.withNext
import io.reactivex.Observable
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.activity_album_videoview.*
import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar
import org.jetbrains.anko.act
import org.jetbrains.anko.imageResource
import java.util.concurrent.TimeUnit


class AlbumVideoViewActivity : BaseActivity() {

    private lateinit var url: String
    private val mediaPlayer = MediaPlayer()
    private var timerDisposable: Disposable? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentViewNoTitlebar(R.layout.activity_album_videoview)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.black)
        window.statusBarColor = ContextCompat.getColor(this, R.color.black)
        isSwipeEnabled = true

        url = intent.getStringExtra("url") ?: return finish()
        ui()
    }
    override fun onPause() {
        timerDisposable?.dispose()
        mediaPlayer.pause()
        super.onPause()
    }
    override fun onResume() {
        super.onResume()
        timer()
    }
    override fun onDestroy() {
        mediaPlayer.stop()
        mediaPlayer.release()
        super.onDestroy()
    }

    private fun ui() {
        Observers.observeLayout(act.frameLayout) {
            if (imageWidth > 0 && imageHeight > 0) {
                resize(imageWidth, imageHeight)
            }
            true
        }
        mediaPlayer.setScreenOnWhilePlaying(true)
        mediaPlayer.setOnErrorListener { mp, what, extra ->
            logws("setOnErrorListener($what, $extra)")
            act.loading.visibility = View.GONE
            act.error.visibility = View.VISIBLE
            false
        }
        mediaPlayer.setOnPreparedListener {
            logws("setOnPreparedListener()")
            act.loading.visibility = View.GONE
            act.error.visibility = View.GONE
            mediaPlayer.start()
            act.play.imageResource = R.drawable.ic_baseline_pause_36dp
        }
        mediaPlayer.setOnVideoSizeChangedListener { _, width, height ->
            logws("setOnVideoSizeChangedListener($width, $height)")
            resize(width, height)
        }
        mediaPlayer.setOnBufferingUpdateListener { mp, percent ->
            logws("setOnBufferingUpdateListener($percent)")
            if (percent == 100) {
                act.loading.visibility = View.GONE
                act.error.visibility = View.GONE
            } else {
                act.loading.visibility = View.VISIBLE
                act.error.visibility = View.GONE
                act.percent.text = "$percent%"
            }
        }
        mediaPlayer.setOnCompletionListener {
            logws("setOnCompletionListener()")
            act.play.imageResource = R.drawable.ic_play_arrow_white_36dp
            act.loading.visibility = View.GONE
            act.error.visibility = View.GONE
            act.doPlay.visibility = View.VISIBLE
        }
        act.surfaceView.holder.addCallback(object: SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder?) = data(holder)
            override fun surfaceChanged(holder: SurfaceHolder?, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder?) = Unit
        })
        act.play.setOnClickListener {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.pause()
                act.play.imageResource = R.drawable.ic_play_arrow_white_36dp
            } else {
                mediaPlayer.start()
                act.play.imageResource = R.drawable.ic_baseline_pause_36dp
            }
        }
        act.doPlay.setOnClickListener {
            mediaPlayer.start()
            act.play.imageResource = R.drawable.ic_baseline_pause_36dp
            act.doPlay.visibility = View.GONE
        }
        act.back.setOnClickListener {
            finish()
        }
        act.seekBar.setOnProgressChangeListener(object: DiscreteSeekBar.OnProgressChangeListener {
            override fun onProgressChanged(seekBar: DiscreteSeekBar?, value: Int, fromUser: Boolean) = Unit
            override fun onStartTrackingTouch(seekBar: DiscreteSeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: DiscreteSeekBar?) {
                if (seekBar == null || !mediaPlayer.isPlaying) return
                val position = seekBar.progress * mediaPlayer.duration / 100
                mediaPlayer.seekTo(position)
            }
        })
    }
    private fun data(holder: SurfaceHolder?) {
        db.async {
            val uri = Uri.parse(url)
            val headers = mutableMapOf<String, String>()
            val token = cfg.haToken
            val pwd = cfg.haPassword
            if (token.isNotBlank()) headers["Authorization"] = token
            if (pwd.isNotBlank()) headers["x-ha-access"] = pwd
            mediaPlayer.setDataSource(applicationContext, uri, headers)
            mediaPlayer.prepare()
        }.nextOnMain {
            logws("data()")
            holder?.setFixedSize(mediaPlayer.videoWidth, mediaPlayer.videoHeight)
            mediaPlayer.setDisplay(holder)
        }
    }
    private var imageWidth = 0
    private var imageHeight = 0
    private fun resize(imageWidth: Int, imageHeight: Int) {
        val viewWidth = act.frameLayout.width
        val viewHeight = act.frameLayout.height
        val lp = act.surfaceView.layoutParams as FrameLayout.LayoutParams
        if (viewWidth * imageHeight / imageWidth < viewHeight) {
            lp.width = FrameLayout.LayoutParams.MATCH_PARENT
            lp.height = viewWidth * imageHeight / imageWidth
        } else {
            lp.height = FrameLayout.LayoutParams.MATCH_PARENT
            lp.width = viewHeight * imageWidth / imageHeight
        }
        act.surfaceView.requestLayout()
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
    }
    private fun timer() {
        Observable.interval(500, TimeUnit.MILLISECONDS)
                .withNext {
                    if (mediaPlayer.isPlaying) {
                        act.progress.text = if (mediaPlayer.duration > 0) "${formatTime(mediaPlayer.currentPosition)} / ${formatTime(mediaPlayer.duration)}" else ""
                        act.seekBar.progress = if (mediaPlayer.duration > 0) mediaPlayer.currentPosition * 100 / mediaPlayer.duration else 0
                    }
                }
                .subscribeOnMain {
                    timerDisposable = it
                }
    }
    private fun formatTime(milliseconds: Int): String {
        var seconds = milliseconds / 1000
        val hours = seconds / 3600
        seconds %= 3600
        val minutes = seconds / 60
        seconds %= 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }
}

