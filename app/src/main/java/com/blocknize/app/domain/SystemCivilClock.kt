package com.blocknize.app.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** Unlike Clock.systemDefaultZone(), resolves the current zone on every access. */
class SystemCivilClock(private val zoneProvider: () -> ZoneId = { ZoneId.systemDefault() }) : Clock() {
    override fun getZone(): ZoneId = zoneProvider()
    override fun withZone(zone: ZoneId): Clock = Clock.system(zone)
    override fun instant(): Instant = Instant.now()
}
