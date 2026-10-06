package com.example.runtime.ui.common

import java.util.Locale

fun formatDistance(meters: Int): String =
    "%.2f".format(Locale.US, meters / 1000.0).trimEnd('0').trimEnd('.') + "km"
