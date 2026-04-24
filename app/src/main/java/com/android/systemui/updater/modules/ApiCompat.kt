package com.android.systemui.updater.modules

import android.os.Build

object ApiCompat {
    val isOreo: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    val isQ: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    val isR: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    val isS: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val isTIRAMISU: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val isU: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
}
