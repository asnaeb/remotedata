package io.github.asnaeb.remotedata

import kotlinx.coroutines.flow.StateFlow

interface RemoteDataHandle<Data> {
    val current: StateFlow<DataResult<Data>>
    fun setData(data: Data): DataResult.Success<Data>
    fun setData(setter: (Data?) -> Data): DataResult.Success<Data>
    fun load()
    suspend fun loadSuspending(): DataResult<Data>
    suspend fun ensure(): Data?
    fun cancel()
    fun invalidate()
    fun clear()
}