package com.blocknize.app.data

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Coordinates application writes with logical snapshots and preference recovery. */
object LocalDataAccess {
    private val mutex = Mutex()
    suspend fun <T> exclusive(action: suspend () -> T): T = mutex.withLock { action() }
}
