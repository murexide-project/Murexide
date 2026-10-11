package com.juhao.murexide.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.juhao.murexide.MainActivity

class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SECRET_CODE") {
            val host = intent.data?.host
            if (host == "2468") {
                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            }
        }
    }
}