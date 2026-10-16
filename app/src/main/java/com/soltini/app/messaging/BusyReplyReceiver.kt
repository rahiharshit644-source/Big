package com.soltini.app.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles the "Bhejo" / "Skip" buttons of a Busy Mode draft notification.
 * This is the ONLY way (besides an explicit voice confirmation) a Busy Mode reply is ever sent.
 */
class BusyReplyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(BusyModeManager.EXTRA_DRAFT_ID) ?: return
        val action = intent.action ?: return
        val mgr = BusyModeManager.getInstance(context)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    BusyModeManager.ACTION_SEND_DRAFT -> mgr.confirmDraft(id)
                    BusyModeManager.ACTION_SKIP_DRAFT -> mgr.skipDraft(id)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
