package io.github.asnaeb.remotedata

import kotlinx.serialization.Serializable

@Serializable
data class RemoteDataKey<Key, Params>(
    val key: @Serializable Key,
    val params: @Serializable Params,
)