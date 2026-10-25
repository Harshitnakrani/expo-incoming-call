package expo.modules.incomingcall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class IncomingCallReceiver : BroadcastReceiver() {
  companion object {
    private const val TAG = "IncomingCallReceiver"
    const val ACTION_CALL_DECLINE = "expo.modules.incomingcall.ACTION_CALL_DECLINE"
    const val ACTION_CALL_DISMISS = "expo.modules.incomingcall.ACTION_CALL_DISMISS"
  }

  override fun onReceive(context: Context, intent: Intent) {
    Log.d(TAG, "Received broadcast action: ${intent.action}")
    when (intent.action) {
      ACTION_CALL_DECLINE, ACTION_CALL_DISMISS -> {
        IncomingCallManager.dismissCall(context)
      }
    }
  }
}
