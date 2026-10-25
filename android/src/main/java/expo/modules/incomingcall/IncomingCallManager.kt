package expo.modules.incomingcall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import java.lang.ref.WeakReference

object IncomingCallManager {
  private const val TAG = "IncomingCallManager"
  const val DEFAULT_CHANNEL_ID = "incoming_calls"
  const val DEFAULT_CHANNEL_NAME = "Incoming Calls"

  var activeActivity: WeakReference<IncomingCallActivity>? = null
  var currentCallData: MutableMap<String, Any?>? = null
  var pendingAnsweredCall: MutableMap<String, Any?>? = null

  private var mediaPlayer: MediaPlayer? = null
  private var ringtone: Ringtone? = null
  private var vibrator: Vibrator? = null
  private val handler = Handler(Looper.getMainLooper())
  private var timeoutRunnable: Runnable? = null

  // Callback listeners for the Expo module
  var onAnsweredCallback: ((Map<String, Any?>) -> Unit)? = null
  var onDeclinedCallback: ((Map<String, Any?>) -> Unit)? = null
  var onTimeoutCallback: ((Map<String, Any?>) -> Unit)? = null

  @Synchronized
  fun displayCall(context: Context, data: Map<String, Any?>): Boolean {
    try {
      val callUuid = data["uuid"]?.toString() ?: data["callId"]?.toString() ?: data["callSessionId"]?.toString() ?: ""
      val callerName = data["callerName"]?.toString() ?: "Incoming Call"
      val callType = data["callType"]?.toString() ?: "Incoming Audio Call"
      val subtitle = data["subtitle"]?.toString() ?: context.applicationInfo.loadLabel(context.packageManager).toString()
      val timeoutSeconds = (data["timeoutSeconds"] as? Number)?.toLong() ?: 30L

      currentCallData = HashMap(data).apply {
        put("uuid", callUuid)
        put("callerName", callerName)
        put("callType", callType)
        put("subtitle", subtitle)
      }

      // 1. Instant Hardware WakeLock to turn physical display ON immediately
      try {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        @Suppress("DEPRECATION")
        val wl = pm?.newWakeLock(
          PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
          "expo-incoming-call:wake"
        )
        wl?.acquire(timeoutSeconds * 1000)
      } catch (e: Exception) {
        Log.w(TAG, "WakeLock error: ${e.message}")
      }

      // 2. Prepare intent targeting IncomingCallActivity
      val callIntent = Intent(context, IncomingCallActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra("uuid", callUuid)
        putExtra("callerName", callerName)
        putExtra("callType", callType)
        putExtra("subtitle", subtitle)
        putExtra("timeoutSeconds", timeoutSeconds)
        for ((k, v) in data) {
          if (v is String) putExtra(k, v)
          else if (v is Boolean) putExtra(k, v)
          else if (v is Int) putExtra(k, v)
          else if (v is Double) putExtra(k, v)
        }
      }

      val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
      } else {
        PendingIntent.FLAG_UPDATE_CURRENT
      }

      var optionsBundle: Bundle? = null
      if (Build.VERSION.SDK_INT >= 34) {
        try {
          val actOptions = android.app.ActivityOptions.makeBasic()
          try {
            val method = actOptions.javaClass.getMethod("setPendingIntentCreatorBackgroundActivityStartMode", Int::class.javaPrimitiveType)
            method.invoke(actOptions, 1)
          } catch (_: NoSuchMethodException) {
            val method = actOptions.javaClass.getMethod("setPendingIntentBackgroundActivityStartMode", Int::class.javaPrimitiveType)
            method.invoke(actOptions, 1)
          }
          optionsBundle = actOptions.toBundle()
        } catch (_: Exception) {}
      }

      val fullScreenPendingIntent = if (optionsBundle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        PendingIntent.getActivity(context, 20001, callIntent, pendingIntentFlags, optionsBundle)
      } else {
        PendingIntent.getActivity(context, 20001, callIntent, pendingIntentFlags)
      }

      // 3. Ensure Notification Channel with MAX importance & ringtone sound exists
      val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
      val channelId = (data["channelId"] as? String) ?: DEFAULT_CHANNEL_ID
      val channelName = (data["channelName"] as? String) ?: DEFAULT_CHANNEL_NAME

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        var channel = notificationManager.getNotificationChannel(channelId)
        if (channel == null) {
          val ringUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ?: Settings.System.DEFAULT_RINGTONE_URI
          channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
            setShowBadge(true)
            enableVibration(true)
            enableLights(true)
            setBypassDnd(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            val audioAttrs = AudioAttributes.Builder()
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
              .build()
            setSound(ringUri, audioAttrs)
          }
          notificationManager.createNotificationChannel(channel)
        }
      }

      // 4. Build notification with FullScreenIntent
      val notificationBuilder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(context.applicationInfo.icon)
        .setContentTitle(callerName)
        .setContentText(callType)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setOngoing(true)
        .setAutoCancel(false)
        .setFullScreenIntent(fullScreenPendingIntent, true)
        .setContentIntent(fullScreenPendingIntent)

      val notification = notificationBuilder.build()
      notificationManager.notify(20001, notification)

      // 5. Also launch Activity directly for guaranteed immediate foreground takeover
      try {
        context.startActivity(callIntent)
      } catch (e: Exception) {
        Log.w(TAG, "Direct startActivity fallback: ${e.message}")
      }

      return true
    } catch (e: Exception) {
      Log.e(TAG, "Error displaying incoming call: ${e.message}", e)
      return false
    }
  }

  @Synchronized
  fun startAudioAndVibration(context: Context, timeoutSeconds: Long = 30L) {
    stopAudioAndVibration()

    // 1. Vibration
    try {
      val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vm?.defaultVibrator
      } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
      }
      vibrator = v
      val pattern = longArrayOf(0, 1000, 800, 1000, 800, 1000, 800, 1000, 800, 1000)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        v?.vibrate(VibrationEffect.createWaveform(pattern, 0))
      } else {
        @Suppress("DEPRECATION")
        v?.vibrate(pattern, 0)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Vibration failed: ${e.message}")
    }

    // 2. Ringtone
    try {
      val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
      if (audioManager != null && (audioManager.ringerMode == AudioManager.RINGER_MODE_SILENT)) {
        Log.d(TAG, "Device in silent mode; skip ringtone audio")
        return
      }

      val ringtoneUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ?: Settings.System.DEFAULT_RINGTONE_URI

      try {
        val r = RingtoneManager.getRingtone(context.applicationContext, ringtoneUri)
        if (r != null) {
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            r.isLooping = true
          }
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            r.audioAttributes = AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          }
          r.play()
          ringtone = r
        }
      } catch (rErr: Exception) {
        Log.w(TAG, "RingtoneManager fallback to MediaPlayer: ${rErr.message}")
      }

      if (ringtone == null) {
        val player = MediaPlayer().apply {
          setDataSource(context.applicationContext, ringtoneUri)
          if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            setAudioAttributes(
              AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            )
          } else {
            @Suppress("DEPRECATION")
            setAudioStreamType(AudioManager.STREAM_RING)
          }
          isLooping = true
          prepare()
          start()
        }
        mediaPlayer = player
      }
    } catch (e: Exception) {
      Log.w(TAG, "Ringtone audio failed: ${e.message}")
    }

    // Schedule auto-timeout
    timeoutRunnable = Runnable {
      Log.d(TAG, "Incoming call timeout reached ($timeoutSeconds s)")
      onCallTimeout()
    }
    handler.postDelayed(timeoutRunnable!!, timeoutSeconds * 1000)
  }

  @Synchronized
  fun stopAudioAndVibration() {
    timeoutRunnable?.let { handler.removeCallbacks(it) }
    timeoutRunnable = null

    try { vibrator?.cancel() } catch (_: Exception) {}
    vibrator = null

    try {
      ringtone?.let { if (it.isPlaying) it.stop() }
    } catch (_: Exception) {}
    ringtone = null

    try {
      mediaPlayer?.let {
        if (it.isPlaying) it.stop()
        it.release()
      }
    } catch (_: Exception) {}
    mediaPlayer = null
  }

  fun onCallAnswered(context: Context, data: Map<String, Any?>) {
    stopAudioAndVibration()
    dismissNotification(context)
    val callCopy = HashMap(data)
    pendingAnsweredCall = callCopy
    onAnsweredCallback?.invoke(callCopy)
  }

  fun onCallDeclined(context: Context, data: Map<String, Any?>) {
    stopAudioAndVibration()
    dismissNotification(context)
    val callCopy = HashMap(data)
    currentCallData = null
    onDeclinedCallback?.invoke(callCopy)
  }

  fun onCallTimeout() {
    val callCopy = currentCallData?.let { HashMap(it) } ?: emptyMap()
    stopAudioAndVibration()
    activeActivity?.get()?.let {
      dismissNotification(it)
      it.finish()
    }
    activeActivity = null
    currentCallData = null
    onTimeoutCallback?.invoke(callCopy)
  }

  fun dismissCall(context: Context): Boolean {
    stopAudioAndVibration()
    dismissNotification(context)
    activeActivity?.get()?.finish()
    activeActivity = null
    currentCallData = null
    return true
  }

  private fun dismissNotification(context: Context) {
    try {
      val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      nm?.cancel(20001)
    } catch (_: Exception) {}
  }
}
