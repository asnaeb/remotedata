package io.github.asnaeb.remotedata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

class RemoteData<Key, Data, Params> internal constructor(
    private val registry: RemoteDataRegistry,
    private val serializer: RemoteDataSerializer<Key, Data, Params>,
    private val key: RemoteDataKey<Key, Params>,
    private val reloadInterval: Duration?,
    private val persister: RemoteDataPersister?,
    private val maxRetries: Int,
    private val retryDelay: (attempt: Int) -> Duration,
    private val loader: suspend (Params) -> Data,
) : RemoteDataHandle<Data> {
    private val loadMutex = Mutex()

    @Volatile
    private var deferredResult: Deferred<DataResult<Data>>? = null

    override val current: StateFlow<DataResult<Data>>
        field = MutableStateFlow<DataResult<Data>>(DataResult.Empty())

    internal val restored = registry.scope.async {
        try {
            persister?.read(key, serializer)?.let {
                ensureData(it.data, it.time)
                it
            }
        }
        catch (e: Throwable) {
            if (e is CancellationException) {
                currentCoroutineContext().ensureActive()
            }
            else {
                persister?.onReadError(e)
            }

            null
        }
    }

    init {
        if (persister != null) {
            registry.scope.launch {
                val restoredResult = restored.await()

                current.filterIsInstance<DataResult.Success<Data>>()
                    .filter {
                        restoredResult == null
                        || it.data != restoredResult.data
                        || it.lastLoaded != restoredResult.time
                    }
                    .collect {
                        try {
                            persister.write(key, PersistentData(it), serializer)
                        }
                        catch (e: Throwable) {
                            if (e is CancellationException) {
                                currentCoroutineContext().ensureActive()
                            }
                            else {
                                persister.onWriteError(e)
                            }
                        }
                    }
            }
        }

        if (reloadInterval != null) {
            val defaultSubscribers = if (persister != null) 1 else 0

            registry.scope.launch {
                while (isActive) {
                    if (current.subscriptionCount.value > defaultSubscribers && current.value.lastLoaded() != null) {
                        loadSuspending()
                    }
                    delay(reloadInterval)
                }
            }
        }
    }

    private fun ensureData(data: Data, time: Instant) {
        current.updateAndGet {
            when (it) {
                is DataResult.Empty -> DataResult.Success(data, time)
                is DataResult.Loading<Data> -> {
                    if (it.lastLoaded == null) {
                        it.copy(staleData = data, lastLoaded = time)
                    }
                    else it
                }
                is DataResult.Error<Data> -> {
                    if (it.lastLoaded == null) {
                        it.copy(staleData = data, lastLoaded = time)
                    }
                    else it
                }
                is DataResult.Success -> it
            }
        }
    }

    private suspend fun runLoader(): DataResult<Data> {
        var prev = current.value

        val loading = current.updateAndGet {
            prev = it

            DataResult.Loading(
                staleData = it.data(),
                lastLoaded = it.lastLoaded()
            )
        }

        repeat(maxRetries + 1) { attempt ->
            try {
                if (attempt > 0) {
                    delay(retryDelay(attempt - 1))
                }
                return setData(loader(key.params))
            }
            catch (e: Throwable) {
                if (e is CancellationException && !currentCoroutineContext().isActive) {
                    current.compareAndSet(loading, prev)
                    throw e
                }

                if (attempt >= maxRetries) {
                    return current.updateAndGet {
                        DataResult.Error(
                            error = e,
                            staleData = it.data(),
                            lastLoaded = it.lastLoaded(),
                            failedAt = Clock.System.now()
                        )
                    }
                }
            }
        }

        error("Unreachable")
    }

    internal suspend fun internalLoadSuspending(
        enabled: (DataResult<Data>) -> Boolean = { true }
    ): DataResult<Data> {
        restored.await()

        val def = loadMutex.withLock {
            deferredResult?.takeIf(Deferred<DataResult<Data>>::isActive)
                ?: if (enabled(current.value)) {
                    registry.scope.async { runLoader() }.also(::deferredResult::set)
                }
                else {
                    return current.value
                }
        }

        return def.await()
    }

    override fun setData(data: Data): DataResult.Success<Data> {
        return current.updateAndGet {
            DataResult.Success(
                data = data,
                lastLoaded = Clock.System.now()
            )
        } as DataResult.Success<Data>
    }

    override fun setData(setter: (Data?) -> Data): DataResult.Success<Data> {
        return current.updateAndGet {
            DataResult.Success(
                data = setter(it.data()),
                lastLoaded = Clock.System.now()
            )
        } as DataResult.Success<Data>
    }

    override suspend fun loadSuspending(): DataResult<Data> {
        return internalLoadSuspending { true }
    }

    override fun load() {
        registry.scope.launch { loadSuspending() }
    }

    override suspend fun ensure(): Data? {
        restored.await()

        return current.value.let {
            when (it) {
                is DataResult.Success<Data> -> it.data
                else -> {
                    val result = internalLoadSuspending { curr -> curr !is DataResult.Success<Data> }

                    result.error()?.let { error -> throw error }

                    result.data()
                }
            }
        }
    }

    override fun cancel() {
        deferredResult?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
    }

    override fun invalidate() {
        current.update {
            when (it) {
                is DataResult.Empty -> it
                is DataResult.Loading -> it.copy(lastLoaded = null)
                is DataResult.Error -> it.copy(lastLoaded = null)
                is DataResult.Success -> it.copy(lastLoaded = Instant.DISTANT_PAST)
            }
        }
    }

    override fun clear() {
        current.update { DataResult.Empty() }
    }

    fun observe(
        scope: CoroutineScope,
        staleTime: Duration = registry.defaultStaleTime,
        loadOnInit: Boolean = registry.defaultLoadOnInit,
    ) = RemoteDataObserver(
        base = this,
        scope = scope,
        staleTime = staleTime,
        loadOnInit = loadOnInit
    )
}