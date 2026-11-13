package io.github.asnaeb.remotedata

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.Serializable
import kotlinx.serialization.serializer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

open class RemoteDataRegistry(
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val defaultLoadOnInit: Boolean = false,
    val defaultStaleTime: Duration = 5.minutes,
    val defaultReloadInterval: Duration? = null,
    val defaultMaxRetries: Int = 0,
    val defaultRetryDelay: (Int) -> Duration = { 1.seconds * (1 shl it) },
    val defaultPersister: RemoteDataPersister? = null,
    val defaultActionStrategy: ConcurrentActionStrategy = ConcurrentActionStrategy.JOIN
) {
    private val actionRegistry: MutableMap<Any, RemoteAction<*, *>> = mutableMapOf()
    private val dataRegistry: MutableMap<RemoteDataKey<*, *>, RemoteData<*, *, *>> = mutableMapOf()

    private val dataKeys = mutableSetOf<Any?>()

    private val lock = SynchronizedObject()

    fun clear() {
        synchronized(lock) {
            actionRegistry.clear()
            dataRegistry.clear()
        }
    }

    fun remove(key: Any) {
        synchronized(lock) {
            dataKeys.remove(key)
            dataRegistry.keys.removeAll { it.key == key }
            actionRegistry.remove(key)
        }
    }

    fun <Key, Data, Params> dataFactory(
        key: @Serializable Key,
        serializer: RemoteDataSerializer<Key, Data, Params>,
        reloadInterval: Duration? = defaultReloadInterval,
        maxRetries: Int = defaultMaxRetries,
        retryDelay: (Int) -> Duration = defaultRetryDelay,
        persister: RemoteDataPersister? = defaultPersister,
        loader: suspend (@Serializable Params) -> @Serializable Data,
    ): (@Serializable Params) -> RemoteData<Key, Data, Params> {
        synchronized(lock) {
            require(dataKeys.add(key)) { "Key $key already present in RemoteDataRegistry" }

            return { params ->
                val remoteDataKey = RemoteDataKey(key = key, params = params)

                dataRegistry.getOrPut(remoteDataKey) {
                    RemoteData(
                        registry = this,
                        serializer = serializer,
                        key = remoteDataKey,
                        maxRetries = maxRetries.coerceAtLeast(0),
                        retryDelay = retryDelay,
                        reloadInterval = reloadInterval,
                        persister = persister,
                        loader = loader
                    )
                } as RemoteData<Key, Data, Params>
            }
        }
    }

    fun <Key, Data> data(
        key: @Serializable Key,
        serializer: RemoteDataSerializer<Key, Data, Unit>,
        reloadInterval: Duration? = defaultReloadInterval,
        maxRetries: Int = defaultMaxRetries,
        retryDelay: (Int) -> Duration = defaultRetryDelay,
        persister: RemoteDataPersister? = defaultPersister,
        loader: suspend () -> @Serializable Data,
    ): RemoteData<Key, Data, Unit> = dataFactory(
        key = key,
        serializer = serializer,
        reloadInterval = reloadInterval,
        maxRetries = maxRetries,
        retryDelay = retryDelay,
        persister = persister,
        loader = { loader() }
    )(Unit)

    fun <Params, Data> action(
        key: Any,
        strategy: ConcurrentActionStrategy = defaultActionStrategy,
        action: suspend (Params) -> Data
    ): RemoteAction<Params, Data> {
        synchronized(lock) {
            return actionRegistry.getOrPut(key) {
                RemoteAction(
                    scope = scope,
                    strategy = strategy,
                    action = action
                )
            }  as RemoteAction<Params, Data>
        }
    }
}

inline fun <reified Key, reified Data, reified Params>RemoteDataRegistry.dataFactory(
    key: Key,
    reloadInterval: Duration? = defaultReloadInterval,
    maxRetries: Int = defaultMaxRetries,
    noinline retryDelay: (Int) -> Duration = defaultRetryDelay,
    persister: RemoteDataPersister? = defaultPersister,
    noinline loader: suspend (Params) -> Data,
): (Params) -> RemoteData<Key, Data, Params> {
    val serializer = RemoteDataSerializer(
        keySerializer = serializer<Key>(),
        dataSerializer = serializer<Data>(),
        paramsSerializer = serializer<Params>()
    )

    return dataFactory(
        key = key,
        serializer = serializer,
        reloadInterval = reloadInterval,
        maxRetries = maxRetries,
        retryDelay = retryDelay,
        persister = persister,
        loader = loader
    )
}

inline fun <reified Key, reified Data> RemoteDataRegistry.data(
    key: Key,
    reloadInterval: Duration? = defaultReloadInterval,
    maxRetries: Int = defaultMaxRetries,
    noinline retryDelay: (Int) -> Duration = defaultRetryDelay,
    persister: RemoteDataPersister? = defaultPersister,
    noinline loader: suspend () -> Data,
): RemoteData<Key, Data, Unit> = dataFactory<Key, Data, Unit>(
    key = key,
    reloadInterval = reloadInterval,
    maxRetries = maxRetries,
    retryDelay = retryDelay,
    persister = persister,
    loader = { loader() }
)(Unit)