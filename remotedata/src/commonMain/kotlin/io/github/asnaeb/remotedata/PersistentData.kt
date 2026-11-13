package io.github.asnaeb.remotedata

import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
data class PersistentData<Data>(
    val data: @Serializable Data,
    val time: Instant
) {
    constructor(result: DataResult.Success<Data>) : this(result.data, result.lastLoaded)
}
