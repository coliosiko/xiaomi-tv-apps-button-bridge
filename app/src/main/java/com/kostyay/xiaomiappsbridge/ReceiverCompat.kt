package com.kostyay.xiaomiappsbridge

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.os.Build

// The flagless overload is guarded to pre-33 and protected by a signature permission.
@SuppressLint("UnspecifiedRegisterReceiverFlag")
internal fun Context.registerBridgeReceiver(receiver: BroadcastReceiver) {
    val filter = IntentFilter(BridgeService.ACTION_STATUS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    } else {
        // The signature permission keeps older Android versions private too.
        registerReceiver(receiver, filter, "$packageName.permission.BRIDGE_STATUS", null)
    }
}
