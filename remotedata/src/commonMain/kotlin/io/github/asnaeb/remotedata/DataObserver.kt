package io.github.asnaeb.remotedata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration

class DataObserver<Key : Any, Data, Params> internal constructor(
    private val base: io.github.asnaeb.remotedata.Data<Key, Data, Params>,
    private val scope: CoroutineScope,
    loadOnInit: Boolean,
    private val staleTime: Duration
) : DataHandle<Data> by base {
    private fun canReload(value: DataResult<Data> = base.current.value): Boolean {
        if (value.loading()) {
            return false
        }

        if (value is DataResult.Error<Data>) {
            return Clock.System.now() - value.failedAt > staleTime
        }

        return value.lastLoaded().let { it == null || Clock.System.now() - it > staleTime }
    }

    override val current = base.current.onSubscription { loadIfStale() }

    init {
        if (loadOnInit) {
            scope.launch {
                loadSuspendIfStale()
            }
        }
    }

    private fun loadIfStale() {
        if (base.restored.isCompleted && !canReload()) {
            return
        }

        scope.launch {
            loadSuspendIfStale()
        }
    }

    private suspend fun loadSuspendIfStale() {
        base.internalLoadSuspending(::canReload)
    }

    override fun load() {
        scope.launch { base.loadSuspending() }
    }
}