package io.github.asnaeb.remotedata

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

interface DataPersister {
    suspend fun <Key : Any, Data, Params> write(
        key: DataKey<Key, Params>,
        data: PersistentData<Data>,
        serializer: DataSerializer<Key, Data, Params>
    )

    suspend fun <Key : Any, Data, Params> read(
        key: DataKey<Key, Params>,
        serializer: DataSerializer<Key, Data, Params>
    ): PersistentData<Data>?

    fun onWriteError(error: Throwable) {}

    fun onReadError(error: Throwable) {}
}

class DataSerializer<Key : Any, Data, Params>(
    val keySerializer: KSerializer<Key>,
    val dataSerializer: KSerializer<Data>,
    val paramsSerializer: KSerializer<Params>
) {
    val dataKeySerializer = DataKey.serializer(keySerializer, paramsSerializer)
    val persistentDataSerializer = PersistentData.serializer(dataSerializer)

    fun serializeKeyToJson(key: DataKey<Key, Params>): String {
        return Json.encodeToString(dataKeySerializer, key)
    }

    fun deserializeKeyFromJson(key: String): DataKey<Key, Params> {
        return Json.decodeFromString(dataKeySerializer, key)
    }

    fun serializeDataToJson(data: PersistentData<Data>): String {
        return Json.encodeToString(persistentDataSerializer, data)
    }

    fun deserializeDataFromJson(data: String): PersistentData<Data> {
        return Json.decodeFromString(persistentDataSerializer, data)
    }
}