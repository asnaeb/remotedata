package io.github.asnaeb.remotedata

import kotlinx.coroutines.flow.StateFlow

interface DataFlowHandle<Data> {
    val current: StateFlow<DataResult<Data>>
    suspend fun setData(data: Data): DataResult.Success<Data>
    suspend fun setData(setter: (Data?) -> Data): DataResult.Success<Data>
    suspend fun load()
    suspend fun loadSuspending(): DataResult<Data>
    suspend fun ensure(): Data?
    suspend fun cancel()
    suspend fun invalidate()
    suspend fun clear()
}