package io.github.asnaeb.remotedata

import kotlinx.serialization.Serializable

@Serializable
data class DataKey<Key : Any, Params>(
    val key: @Serializable Key,
    val params: @Serializable Params,
)