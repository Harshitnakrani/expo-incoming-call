package expo.modules.incomingcall

import android.content.Context
import android.util.Log
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoIncomingCallModule : Module() {
  companion object {
    private const val TAG = "ExpoIncomingCallModule"
  }

  override fun definition() = ModuleDefinition {
    Name("ExpoIncomingCall")

    Events(
      "onCallAnswered",
      "onCallDeclined",
      "onCallTimeout"
    )

    OnCreate {
      IncomingCallManager.onAnsweredCallback = { data ->
        try {
          sendEvent("onCallAnswered", data)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to send onCallAnswered event: ${e.message}")
        }
      }

      IncomingCallManager.onDeclinedCallback = { data ->
        try {
          sendEvent("onCallDeclined", data)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to send onCallDeclined event: ${e.message}")
        }
      }

      IncomingCallManager.onTimeoutCallback = { data ->
        try {
          sendEvent("onCallTimeout", data)
        } catch (e: Exception) {
          Log.w(TAG, "Failed to send onCallTimeout event: ${e.message}")
        }
      }
    }

    OnDestroy {
      IncomingCallManager.onAnsweredCallback = null
      IncomingCallManager.onDeclinedCallback = null
      IncomingCallManager.onTimeoutCallback = null
    }

    AsyncFunction("displayIncomingCall") { options: Map<String, Any?> ->
      val context = appContext.reactContext ?: appContext.currentActivity ?: run {
        Log.e(TAG, "Context is null; cannot display incoming call")
        return@AsyncFunction false
      }
      return@AsyncFunction IncomingCallManager.displayCall(context, options)
    }

    AsyncFunction("dismissCall") {
      val context = appContext.reactContext ?: appContext.currentActivity ?: run {
        return@AsyncFunction false
      }
      return@AsyncFunction IncomingCallManager.dismissCall(context)
    }

    Function("isCallActive") {
      return@Function IncomingCallManager.activeActivity?.get() != null
    }

    Function("getPendingAnsweredCall") {
      return@Function IncomingCallManager.pendingAnsweredCall
    }

    Function("clearPendingAnsweredCall") {
      IncomingCallManager.pendingAnsweredCall = null
      return@Function true
    }

    Function("canUseFullScreenIntent") {
      val context = appContext.reactContext ?: appContext.currentActivity ?: return@Function true
      return@Function IncomingCallManager.canUseFullScreenIntent(context)
    }

    Function("openFullScreenIntentSettings") {
      val context = appContext.reactContext ?: appContext.currentActivity ?: return@Function false
      return@Function IncomingCallManager.openFullScreenIntentSettings(context)
    }
  }
}
