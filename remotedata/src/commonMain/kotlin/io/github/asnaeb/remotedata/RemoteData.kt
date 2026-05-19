package io.github.asnaeb.remotedata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration

class RemoteData<Data> internal constructor(
    private val base: RemoteDataBase<Data, *>,
    private val scope: CoroutineScope,
    private val loadOnInit: Boolean,
    private val staleTime: Duration,
) : IRemoteData<Data> by base {
    private val isStale: Boolean get() = base.lastLoaded?.let { Clock.System.now() - it > staleTime } ?: true

    init {
        if (loadOnInit) {
            loadIfStale()
        }
    }

    @OptIn(ExperimentalForInheritanceCoroutinesApi::class)
    override val data: StateFlow<Data?> = object : StateFlow<Data?> by base.data {
        override val value: Data? get() {
            loadIfStale()
            return base.data.value
        }

        override suspend fun collect(collector: FlowCollector<Data?>): Nothing {
            loadIfStale()

            base.data.collect(collector)
//            base.data.collect {
//                if (base.isVirgin && it == null) {
//                    return@collect
//                }
//
//                collector.emit(it)
//            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val initializing: Flow<Boolean> = base.loading.mapLatest { it && base.lastLoaded == null }

    @OptIn(ExperimentalCoroutinesApi::class)
    val reloading: Flow<Boolean> = base.loading.mapLatest { it && base.lastLoaded != null }

    fun loadIfStale() {
        scope.launch {
            if (isStale) {
                base.loadSuspend(false)
            }
        }
    }

    suspend fun loadSuspendIfStale() {
        if (isStale) {
            base.loadSuspend(false)
        }
    }

    fun load() {
        scope.launch { base.loadSuspend(false) }
    }

    suspend fun loadSuspend() {
        base.loadSuspend(false)
    }

    @Throws(Throwable::class)
    suspend fun ensure(): Data? {
        return base.data.value.let {
            if (base.lastLoaded == null) {
                base.loadSuspend(true)
                base.data.value
            }
            else {
                it
            }
        }
    }

    fun withOptions(
        scope: CoroutineScope = this.scope,
        loadOnInit: Boolean = this.loadOnInit,
        staleTime: Duration = this.staleTime,
    ) = RemoteData(base, scope, loadOnInit, staleTime)
}