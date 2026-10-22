package com.kiroku.app.ui.inbox

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kiroku.app.AppContainer
import com.kiroku.app.AppPrefs
import com.kiroku.app.inbox.WebInbox
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class WebInboxViewModel(
    private val inbox: WebInbox,
    private val prefs: AppPrefs,
    private val appContext: Context,
) : ViewModel() {

    var projectUrl by mutableStateOf(prefs.inboxUrl)
    var anonKey by mutableStateOf(prefs.inboxAnonKey)

    var configured by mutableStateOf(inbox.isConfigured)
        private set
    var busy by mutableStateOf(false)
        private set
    var lastCheck by mutableStateOf(prefs.inboxLastCheck)
        private set
    var lastError by mutableStateOf(prefs.inboxLastError)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var mirror by mutableStateOf(prefs.inboxMirror)
        private set

    /** "Show all my links on the web page". */
    fun changeMirror(on: Boolean) {
        if (busy) return
        mirror = on
        busy = true
        viewModelScope.launch {
            try {
                inbox.setMirror(on)
                message = if (on) "Your links are now on the web page" else "Your links were removed from the web page"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Saved, but couldn't reach your inbox yet: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
                lastCheck = prefs.inboxLastCheck
                lastError = prefs.inboxLastError
            }
        }
    }

    fun consumeMessage() {
        message = null
    }

    /** Saves the project details and tests them with a first check. */
    fun connect() {
        if (busy || projectUrl.isBlank() || anonKey.isBlank()) return
        inbox.configure(projectUrl, anonKey)
        projectUrl = prefs.inboxUrl
        runCheck(onSuccess = {
            WebInbox.schedule(appContext)
            "Connected. Now connect a browser."
        })
    }

    fun checkNow() = runCheck(onSuccess = { null })

    private fun runCheck(onSuccess: () -> String?) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                val result = inbox.checkNow()
                configured = true
                message = onSuccess() ?: when {
                    result.added == 0 && result.updated == 0 && result.alreadySaved == 0 -> "Synced. Nothing new from the web"
                    else -> listOfNotNull(
                        result.added.takeIf { it > 0 }?.let { if (it == 1) "1 link added" else "$it links added" },
                        result.updated.takeIf { it > 0 }?.let { if (it == 1) "1 link updated" else "$it links updated" },
                        result.alreadySaved.takeIf { it > 0 }?.let { "$it already saved" },
                    ).joinToString(", ") + " from the web"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                message = "Couldn't reach your inbox: ${e.message ?: e.javaClass.simpleName}"
            } finally {
                busy = false
                lastCheck = prefs.inboxLastCheck
                lastError = prefs.inboxLastError
            }
        }
    }

    fun setupLink(): String = inbox.setupLink()

    fun resetKey() {
        inbox.resetKey()
        message = "Browsers disconnected. Send a new setup link to the ones you still use."
    }

    fun turnOff() {
        inbox.turnOff()
        WebInbox.cancel(appContext)
        configured = false
        projectUrl = ""
        anonKey = ""
        lastCheck = 0L
        lastError = ""
        message = "Web inbox turned off"
    }

    companion object {
        fun factory(container: AppContainer, context: Context) = viewModelFactory {
            initializer { WebInboxViewModel(container.inbox, container.prefs, context.applicationContext) }
        }
    }
}
