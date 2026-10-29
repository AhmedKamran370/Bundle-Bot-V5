package com.bundlebot

import android.content.Context
import android.media.RingtoneManager
import android.media.Ringtone
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator

object Alarm {
    private var ringtone: Ringtone? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())

    fun init(c: Context) {
        ringtone = RingtoneManager.getRingtone(c, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM))
        vibrator = c.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun start(c: Context) {
        try { ringtone?.play() } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500), 0))
            } else {
                @Suppress("DEPRECATION") vibrator?.vibrate(longArrayOf(0, 500, 300, 500), 0)
            }
        } catch (_: Exception) {}
        
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stop(c) }, 5000)
    }

    fun stop(c: Context) {
        try { ringtone?.stop() } catch (_: Exception) {}
        try { vibrator?.cancel() } catch (_: Exception) {}
    }
}