package io.github.asnaeb.remotedata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration

class  DataFlow<Key : Any, Data, Params>(
    private val registry: Registry,
    paramsFlow: Flow<Params>,
    getRemoteData: (Params) -> io.github.asnaeb.remotedata.Data<Key, Data, Params>
) : DataFlowHandle<Data> {
    private val remoteDataFlow = paramsFlow.map(getRemoteData)

    @OptIn(ExperimentalCoroutinesApi::class)
    override val current: StateFlow<DataResult<Data>> = remoteDataFlow
        .flatMapLatest { it.current }
        .stateIn(registry.scope, SharingStarted.WhileSubscribed(), DataResult.Empty())

    private suspend fun getRemoteData() = remoteDataFlow.first()

    override suspend fun setData(data: Data): DataResult.Success<Data> {
        return getRemoteData().setData(data)
    }

    override suspend fun setData(setter: (Data?) -> Data): DataResult.Success<Data> {
        return getRemoteData().setData(setter)
    }

    override suspend fun load() {
        getRemoteData().load()
    }

    override suspend fun loadSuspending(): DataResult<Data> {
        return getRemoteData().loadSuspending()
    }

    override suspend fun ensure(): Data? {
        return getRemoteData().ensure()
    }

    override suspend fun cancel() {
        getRemoteData().cancel()
    }

    override suspend fun invalidate() {
        getRemoteData().invalidate()
    }

    override suspend fun clear() {
        getRemoteData().clear()
    }

    fun observe(
        scope: CoroutineScope,
        staleTime: Duration = registry.defaultDataStaleTime,
        loadOnInit: Boolean = registry.defaultDataLoadOnInit
    ): DataFlowObserver<Key, Data, Params> {
        return DataFlowObserver(
            scope = scope,
            observerFlow = remoteDataFlow.map { it.observe(scope, staleTime, loadOnInit) }
        )
    }
}