package com.kiroku.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kiroku.app.inbox.WebInbox
import com.kiroku.app.ui.KirokuNavHost
import com.kiroku.app.ui.theme.KirokuTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as KirokuApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Only handle the launch intent once (not again after rotation / process restore).
        if (savedInstanceState == null) handleShare(intent)

        setContent {
            KirokuTheme {
                KirokuNavHost(container)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Pick up links saved from the browser as soon as the app is opened.
        if (container.inbox.isConfigured) WebInbox.checkSoon(this)
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app: send what changed here to the web page.
        if (container.inbox.isConfigured) WebInbox.checkSoon(this)
    }

    /** App already running (singleTask): a new share arrives here. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleShare(intent)
    }

    private fun handleShare(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
        val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)
        if (text.isBlank() && subject.isNullOrBlank()) return
        container.shares.receive(IncomingShare(text = text, subject = subject))
    }
}
