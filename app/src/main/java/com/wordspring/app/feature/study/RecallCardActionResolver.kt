package com.wordspring.app.feature.study

import kotlin.math.abs

/** Keeps a quick tap distinct from the drag gestures used to self-grade a card. */
internal fun isCardTap(offsetX: Float, offsetY: Float, touchSlopPx: Float): Boolean =
    abs(offsetX) <= touchSlopPx && abs(offsetY) <= touchSlopPx
