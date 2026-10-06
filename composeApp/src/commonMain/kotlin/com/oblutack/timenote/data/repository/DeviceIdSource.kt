package com.oblutack.timenote.data.repository

/**
 * A random identifier for this installation, created once and kept. Sync records which device wrote each
 * field, so that two devices editing at the same moment can be told apart and merged predictably.
 */
interface DeviceIdSource {
    suspend fun deviceId(): String
}
