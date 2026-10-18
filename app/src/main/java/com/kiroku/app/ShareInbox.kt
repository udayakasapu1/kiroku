package com.kiroku.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Text shared into the app from another app's Share menu. */
data class IncomingShare(val text: String, val subject: String?)

/** Holds a share until the library screen is on screen to show it. */
class ShareInbox {
    private val _pending = MutableStateFlow<IncomingShare?>(null)
    val pending: StateFlow<IncomingShare?> = _pending.asStateFlow()

    fun receive(share: IncomingShare) {
        _pending.value = share
    }

    fun consume(): IncomingShare? = _pending.getAndUpdate { null }
}
