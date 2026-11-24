package io.github.asnaeb.remotedata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class DataFlowObserver<Key : Any, Data, Params>(
    scope: CoroutineScope,
    private val observerFlow: Flow<DataObserver<Key, Data, Params>>
) : DataFlowHandle<Data> {
    @OptIn(ExperimentalCoroutinesApi::class)
    override val current: StateFlow<DataResult<Data>> = observerFlow
        .flatMapLatest { it.current }
        .stateIn(scope, SharingStarted.WhileSubscribed(), DataResult.Empty())

    private suspend fun getObserver() = observerFlow.first()

    override suspend fun setData(data: Data): DataResult.Success<Data> {
        return getObserver().setData(data)
    }

    override suspend fun setData(setter: (Data?) -> Data): DataResult.Success<Data> {
        return getObserver().setData(setter)
    }

    override suspend fun load() {
        getObserver().load()
    }

    override suspend fun loadSuspending(): DataResult<Data> {
        return getObserver().loadSuspending()
    }

    override suspend fun ensure(): Data? {
        return getObserver().ensure()
    }

    override suspend fun cancel() {
        getObserver().cancel()
    }

    override suspend fun invalidate() {
        getObserver().invalidate()
    }

    override suspend fun clear() {
        getObserver().clear()
    }

}