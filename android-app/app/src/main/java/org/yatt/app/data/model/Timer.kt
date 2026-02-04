package org.yatt.app.data.model

import java.time.Instant

data class Timer(
    val id: String,
    val startTime: Instant,
    val endTime: Instant?,
    val tag: String?
)
