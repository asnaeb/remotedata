package io.github.asnaeb.remotedata

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

interface RemoteDataPersister {
    suspend fun <Key, Data, Params> write(
        key: RemoteDataKey<Key, Params>,
        data: PersistentData<Data>,
        serializer: RemoteDataSerializer<Key, Data, Params>
    )

    suspend fun <Key, Data, Params> read(
        key: RemoteDataKey<Key, Params>,
        serializer: RemoteDataSerializer<Key, Data, Params>
    ): PersistentData<Data>?

    fun onWriteError(error: Throwable) {}

    fun onReadError(error: Throwable) {}
}

class RemoteDataSerializer<Key, Data, Params>(
    val keySerializer: KSerializer<Key>,
    val dataSerializer: KSerializer<Data>,
    val paramsSerializer: KSerializer<Params>
) {
    val remoteDataKeySerializer = RemoteDataKey.serializer(keySerializer, paramsSerializer)
    val persistentDataSerializer = PersistentData.serializer(dataSerializer)

    fun serializeKeyToJson(key: RemoteDataKey<Key, Params>): String {
        return Json.encodeToString(remoteDataKeySerializer, key)
    }

    fun deserializeKeyFromJson(key: String): RemoteDataKey<Key, Params> {
        return Json.decodeFromString(remoteDataKeySerializer, key)
    }

    fun serializeDataToJson(data: PersistentData<Data>): String {
        return Json.encodeToString(persistentDataSerializer, data)
    }

    fun deserializeDataFromJson(data: String): PersistentData<Data> {
        return Json.decodeFromString(persistentDataSerializer, data)
    }
}