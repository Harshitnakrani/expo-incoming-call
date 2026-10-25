package expo.modules.incomingcall

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

class IncomingCallActivity : Activity() {
  companion object {
    private const val TAG = "IncomingCallActivity"
  }

  private var wakeLock: PowerManager.WakeLock? = null
  private val callData = HashMap<String, Any?>()

  override fun onCreate(savedInstanceState: Bundle?) {
    // ── 1. Samsung One UI & Android 14 lockscreen flags (MUST run before super.onCreate) ──
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      setShowWhenLocked(true)
      setTurnScreenOn(true)
    }
    @Suppress("DEPRECATION")
    window.addFlags(
      WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
      WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
      WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
      WindowManager.LayoutParams.FLAG_FULLSCREEN
    )

    super.onCreate(savedInstanceState)
    IncomingCallManager.activeActivity = WeakReference(this)
    Log.d(TAG, "IncomingCallActivity started")

    // ── 2. Hardware WakeLock ──
    try {
      val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
      @Suppress("DEPRECATION")
      wakeLock = pm?.newWakeLock(
        PowerManager.FULL_WAKE_LOCK or
        PowerManager.ACQUIRE_CAUSES_WAKEUP or
        PowerManager.ON_AFTER_RELEASE,
        "expo-incoming-call:activity_wake"
      )
      wakeLock?.acquire(35_000)
    } catch (e: Exception) {
      Log.w(TAG, "WakeLock error: ${e.message}")
    }

    // ── 3. Extract Intent extras ──
    intent?.extras?.let { bundle ->
      for (key in bundle.keySet()) {
        callData[key] = bundle.get(key)
      }
    }

    val callerName = intent?.getStringExtra("callerName") ?: "Incoming Call"
    val callType = intent?.getStringExtra("callType") ?: "INCOMING VOICE CALL"
    val subtitle = intent?.getStringExtra("subtitle")
      ?: applicationInfo.loadLabel(packageManager).toString()
    val timeoutSeconds = intent?.getLongExtra("timeoutSeconds", 30L) ?: 30L
    val accentColor = intent?.getStringExtra("accentColor") ?: "#2563EB"

    // ── 4. Build Full-Screen UI ──
    buildUI(callerName, callType, subtitle, accentColor)

    // ── 5. Start looping ringtone & vibration ──
    IncomingCallManager.startAudioAndVibration(this, timeoutSeconds)
  }

  private fun buildUI(callerName: String, callType: String, subtitle: String, accentColor: String) {
    val dp = { v: Int ->
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    }

    val root = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
      setBackgroundColor(Color.parseColor("#0F172A"))
      setPadding(dp(24), dp(80), dp(24), dp(60))
    }

    // Call Type Header (e.g. "INCOMING VOICE CALL")
    val callTypeView = TextView(this).apply {
      text = callType.uppercase()
      setTextColor(Color.parseColor("#94A3B8"))
      textSize = 14f
      letterSpacing = 0.08f
      gravity = Gravity.CENTER
    }
    root.addView(callTypeView, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(28) })

    // Avatar Circle with initial letter
    val avatar = TextView(this).apply {
      text = callerName.firstOrNull()?.uppercase() ?: "C"
      textSize = 42f
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
      val shape = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        val color = try { Color.parseColor(accentColor) } catch (_: Exception) { Color.parseColor("#2563EB") }
        setColor(color)
      }
      background = shape
    }
    root.addView(avatar, LinearLayout.LayoutParams(dp(100), dp(100)).apply {
      bottomMargin = dp(24)
    })

    // Caller Name
    val nameView = TextView(this).apply {
      text = callerName
      setTextColor(Color.WHITE)
      textSize = 28f
      gravity = Gravity.CENTER
      setTypeface(typeface, Typeface.BOLD)
    }
    root.addView(nameView, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = dp(8) })

    // App Subtitle / Brand
    val subtitleView = TextView(this).apply {
      text = subtitle
      setTextColor(Color.parseColor("#64748B"))
      textSize = 15f
      gravity = Gravity.CENTER
    }
    root.addView(subtitleView, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    // Vertical Flexible Spacer
    val spacer = View(this)
    root.addView(spacer, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
    ))

    // Row containing Decline and Accept buttons
    val buttonsRow = LinearLayout(this).apply {
      orientation = LinearLayout.HORIZONTAL
      gravity = Gravity.CENTER
    }

    // Decline Button (Red)
    val declineBtn = createCircleButton(
      bgColor = "#EF4444",
      label = "Decline",
      iconText = "✕"
    ) { decline() }
    buttonsRow.addView(declineBtn, LinearLayout.LayoutParams(
      dp(80), LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { marginEnd = dp(64) })

    // Accept Button (Green)
    val acceptBtn = createCircleButton(
      bgColor = "#22C55E",
      label = "Accept",
      iconText = "✓"
    ) { accept() }
    buttonsRow.addView(acceptBtn, LinearLayout.LayoutParams(
      dp(80), LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    root.addView(buttonsRow, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    setContentView(root)
  }

  private fun createCircleButton(
    bgColor: String,
    label: String,
    iconText: String,
    onClick: () -> Unit
  ): LinearLayout {
    val dp = { v: Int ->
      TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
    }

    val container = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      gravity = Gravity.CENTER_HORIZONTAL
    }

    val circle = TextView(this).apply {
      text = iconText
      textSize = 30f
      setTextColor(Color.WHITE)
      gravity = Gravity.CENTER
      val shape = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(Color.parseColor(bgColor))
      }
      background = shape
      setOnClickListener { onClick() }
    }
    container.addView(circle, LinearLayout.LayoutParams(dp(72), dp(72)).apply {
      bottomMargin = dp(10)
    })

    val labelView = TextView(this).apply {
      text = label
      setTextColor(Color.WHITE)
      textSize = 14f
      gravity = Gravity.CENTER
    }
    container.addView(labelView, LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.WRAP_CONTENT,
      LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    return container
  }

  private fun accept() {
    Log.d(TAG, "Call accepted by user")
    callData["isAccepted"] = true
    IncomingCallManager.onCallAnswered(this, callData)

    // Unlock Keyguard so MainActivity is visible immediately without requiring PIN/Password
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
      try {
        val km = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        km?.requestDismissKeyguard(this, null)
      } catch (_: Exception) {}
    }

    // Launch host app with call payload and autoAccept=true
    val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
      flags = Intent.FLAG_ACTIVITY_NEW_TASK or
              Intent.FLAG_ACTIVITY_CLEAR_TOP or
              Intent.FLAG_ACTIVITY_SINGLE_TOP
      putExtra("isIncomingCall", true)
      putExtra("autoAccept", true)
      for ((k, v) in callData) {
        if (v is String) putExtra(k, v)
        else if (v is Boolean) putExtra(k, v)
        else if (v is Int) putExtra(k, v)
        else if (v is Double) putExtra(k, v)
      }
    }

    if (launchIntent != null) {
      startActivity(launchIntent)
    }

    releaseWakeLock()
    finish()
  }

  private fun decline() {
    Log.d(TAG, "Call declined by user")
    callData["isAccepted"] = false
    IncomingCallManager.onCallDeclined(this, callData)
    releaseWakeLock()
    finish()
  }

  private fun releaseWakeLock() {
    try {
      wakeLock?.let { if (it.isHeld) it.release() }
    } catch (_: Exception) {}
    wakeLock = null
  }

  override fun onDestroy() {
    super.onDestroy()
    releaseWakeLock()
    if (IncomingCallManager.activeActivity?.get() == this) {
      IncomingCallManager.activeActivity = null
    }
  }
}
