package com.roy.gallery.pro.views

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.TextView
import com.roy.gallery.pro.R
import com.roy.gallery.pro.extensions.audioManager
import com.roy.gallery.pro.helpers.CLICK_MAX_DISTANCE
import com.roy.gallery.pro.helpers.CLICK_MAX_DURATION
import com.roy.gallery.pro.helpers.DRAG_THRESHOLD
import com.roy.commons.ext.onGlobalLayout
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

// allow horizontal swipes through the layout, else it can cause glitches at zoomed in images
class MediaSideScroll(context: Context, attrs: AttributeSet) : RelativeLayout(context, attrs) {
    private val slideInfoFadeDelay = 1000L
    private var mTouchDownX = 0f
    private var mTouchDownY = 0f
    private var mTouchDownTime = 0L
    private var mTouchDownValue = -1
    private var mTempBrightness = 0
    private var mLastTouchY = 0f
    private var mViewHeight = 0
    private var mIsBrightnessScroll = false
    private var mPassTouches = false
    private var dragThreshold = DRAG_THRESHOLD * context.resources.displayMetrics.density

    private var mSlideInfoText = ""
    private var mSlideInfoFadeHandler = Handler(Looper.getMainLooper())
    private var mParentView: ViewGroup? = null
    private var activity: Activity? = null

    private lateinit var slideInfoView: TextView
    private lateinit var callback: (Float, Float) -> Unit

    fun initialize(
        activity: Activity,
        slideInfoView: TextView,
        isBrightness: Boolean,
        parentView: ViewGroup?,
        callback: (x: Float, y: Float) -> Unit,
    ) {
        this.activity = activity
        this.slideInfoView = slideInfoView
        this.callback = callback
        mParentView = parentView
        mIsBrightnessScroll = isBrightness
        mSlideInfoText =
            activity.getString(if (isBrightness) R.string.brightness else R.string.volume)
        onGlobalLayout {
            mViewHeight = height
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (mPassTouches) {
            if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
                mPassTouches = false
            }
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mPassTouches && activity == null) {
            return false
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                mTouchDownX = event.x
                mTouchDownY = event.y
                mLastTouchY = event.y
                mTouchDownTime = System.currentTimeMillis()
                if (mIsBrightnessScroll) {
                    if (mTouchDownValue == -1) {
                        mTouchDownValue = getCurrentBrightness()
                    }
                } else {
                    mTouchDownValue = getCurrentVolume()
                }
            }

            MotionEvent.ACTION_MOVE -> {
                val diffX = mTouchDownX - event.x
                val diffY = mTouchDownY - event.y

                if (abs(diffY) > dragThreshold && abs(diffY) > abs(diffX)) {
                    var percent = ((diffY / mViewHeight) * 100).toInt() * 3
                    percent = min(100, max(-100, percent))

                    if ((percent == 100 && event.y > mLastTouchY) || (percent == -100 && event.y < mLastTouchY)) {
                        mTouchDownY = event.y
                        mTouchDownValue =
                            if (mIsBrightnessScroll) mTempBrightness else getCurrentVolume()
                    }

                    percentChanged(percent)
                } else if (abs(diffX) > dragThreshold || abs(diffY) > dragThreshold) {
                    if (!mPassTouches) {
                        event.action = MotionEvent.ACTION_DOWN
                        event.setLocation(event.rawX, event.y)
                        mParentView?.dispatchTouchEvent(event)
                    }
                    mPassTouches = true
                    mParentView?.dispatchTouchEvent(event)
                    return false
                }
                mLastTouchY = event.y
            }

            MotionEvent.ACTION_UP -> {
                val diffX = mTouchDownX - event.x
                val diffY = mTouchDownY - event.y
                if (abs(diffX) < CLICK_MAX_DISTANCE && abs(diffY) < CLICK_MAX_DISTANCE && System.currentTimeMillis() - mTouchDownTime < CLICK_MAX_DURATION) {
                    callback(event.rawX, event.rawY)
                }

                if (mIsBrightnessScroll) {
                    mTouchDownValue = mTempBrightness
                }
            }
        }
        return true
    }

    private fun getCurrentVolume() =
        activity!!.audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    private fun getCurrentBrightness(): Int {
        return try {
            Settings.System.getInt(activity!!.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Settings.SettingNotFoundException) {
            70
        }
    }

    private fun percentChanged(percent: Int) {
        if (mIsBrightnessScroll) {
            brightnessPercentChanged(percent)
        } else {
            volumePercentChanged(percent)
        }
    }

    private fun volumePercentChanged(percent: Int) {
        val stream = AudioManager.STREAM_MUSIC
        val maxVolume = activity!!.audioManager.getStreamMaxVolume(stream)
        val percentPerPoint = 100 / maxVolume
        val addPoints = percent / percentPerPoint
        val newVolume = min(maxVolume, max(0, mTouchDownValue + addPoints))
        activity!!.audioManager.setStreamVolume(stream, newVolume, 0)

        val absolutePercent = ((newVolume / maxVolume.toFloat()) * 100).toInt()
        showValue(absolutePercent)

        mSlideInfoFadeHandler.removeCallbacksAndMessages(null)
        mSlideInfoFadeHandler.postDelayed({
            slideInfoView.animate().alpha(0f)
        }, slideInfoFadeDelay)
    }

    private fun brightnessPercentChanged(percent: Int) {
        val maxBrightness = 255f
        var newBrightness = (mTouchDownValue + 2.55 * percent).toFloat()
        newBrightness = min(maxBrightness, max(0f, newBrightness))
        mTempBrightness = newBrightness.toInt()

        val absolutePercent = ((newBrightness / maxBrightness) * 100).toInt()
        showValue(absolutePercent)

        val attributes = activity!!.window.attributes
        attributes.screenBrightness = absolutePercent / 100f
        activity!!.window.attributes = attributes

        mSlideInfoFadeHandler.removeCallbacksAndMessages(null)
        mSlideInfoFadeHandler.postDelayed({
            slideInfoView.animate().alpha(0f)
        }, slideInfoFadeDelay)
    }

    @SuppressLint("SetTextI18n")
    private fun showValue(percent: Int) {
        slideInfoView.apply {
            text = "$mSlideInfoText:\n$percent%"
            alpha = 1f
        }
    }
}
