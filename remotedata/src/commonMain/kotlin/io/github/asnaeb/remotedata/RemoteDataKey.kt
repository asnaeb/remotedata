package io.github.asnaeb.remotedata

import kotlinx.serialization.Serializable

@Serializable
data class RemoteDataKey<Params>(val key: String, val params: Params)