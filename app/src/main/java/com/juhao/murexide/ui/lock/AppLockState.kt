package com.juhao.murexide.ui.lock

object AppLockState {
    @Volatile
    var unlocked: Boolean = false

    @Volatile
    var gateVisible: Boolean = false

    fun reset() {
        unlocked = false
    }
}