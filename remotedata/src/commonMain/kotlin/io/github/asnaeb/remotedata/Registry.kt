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

open class Registry(
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    val defaultDataLoadOnInit: Boolean = false,
    val defaultDataStaleTime: Duration = 5.minutes,
    val defaultDataReloadInterval: Duration? = null,
    val defaultDataMaxRetries: Int = 0,
    val defaultDataRetryDelay: (Int) -> Duration = { 1.seconds * (1 shl it) },
    val defaultDataPersister: DataPersister? = null,
    val defaultActionStrategy: ConcurrentActionStrategy = ConcurrentActionStrategy.JOIN
) {
    private val actionRegistry: MutableMap<Any, Action<*, *>> = mutableMapOf()
    private val dataRegistry: MutableMap<DataKey<*, *>, Data<*, *, *>> = mutableMapOf()

    private val dataKeys = mutableSetOf<Any>()

    private val lock = SynchronizedObject()

    private fun <Key : Any, Data, Params> getData(
        key: Key,
        params: Params,
        serializer: DataSerializer<Key, Data, Params>,
        reloadInterval: Duration? = defaultDataReloadInterval,
        maxRetries: Int = defaultDataMaxRetries,
        retryDelay: (Int) -> Duration = defaultDataRetryDelay,
        persister: DataPersister? = defaultDataPersister,
        loader: suspend (@Serializable Params) -> @Serializable Data
    ): io.github.asnaeb.remotedata.Data<Key, Data, Params> {
        val dataKey = DataKey(key = key, params = params)

        @Suppress("UNCHECKED_CAST")
        return dataRegistry.getOrPut(dataKey) {
            Data(
                registry = this,
                serializer = serializer,
                key = dataKey,
                maxRetries = maxRetries.coerceAtLeast(0),
                retryDelay = retryDelay,
                reloadInterval = reloadInterval,
                persister = persister,
                loader = loader
            )
        } as io.github.asnaeb.remotedata.Data<Key, Data, Params>
    }

    private inline fun <T> withUniqueDataKey(key: Any, block: () ->  T): T {
        return synchronized(lock) {
            require(dataKeys.add(key)) { "Key $key already present in RemoteDataRegistry" }
            block()
        }
    }

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

    fun forEachMatchingKey(vararg keys: Any, action: (Data<*, *, *>) -> Unit) {
        val keySet = keys.toSet()

        val matchingData = synchronized(lock) {
            dataRegistry.filterKeys { it.key in keySet }
        }

        matchingData.values.forEach(action)
    }

    fun <Key : Any, Data, Params> factory(
        key: @Serializable Key,
        serializer: DataSerializer<Key, Data, Params>,
        reloadInterval: Duration? = defaultDataReloadInterval,
        maxRetries: Int = defaultDataMaxRetries,
        retryDelay: (Int) -> Duration = defaultDataRetryDelay,
        persister: DataPersister? = defaultDataPersister,
        loader: suspend (@Serializable Params) -> @Serializable Data,
    ): Factory<Key, Data, Params> {
        return withUniqueDataKey(key) {
            Factory(
                dataFactory = { params ->
                    getData(
                        key = key,
                        params = params,
                        serializer = serializer,
                        reloadInterval = reloadInterval,
                        maxRetries = maxRetries,
                        retryDelay = retryDelay,
                        persister = persister,
                        loader = loader
                    )
                },
                dataFlowFactory = { flow ->
                    DataFlow(
                        registry = this,
                        paramsFlow = flow,
                        getRemoteData = { params ->
                            getData(
                                key = key,
                                params = params,
                                serializer = serializer,
                                reloadInterval = reloadInterval,
                                maxRetries = maxRetries,
                                retryDelay = retryDelay,
                                persister = persister,
                                loader = loader
                            )
                        }
                    )
                }
            )
        }
    }

    fun <Key : Any, Data> data(
        key: @Serializable Key,
        serializer: DataSerializer<Key, Data, Unit>,
        reloadInterval: Duration? = defaultDataReloadInterval,
        maxRetries: Int = defaultDataMaxRetries,
        retryDelay: (Int) -> Duration = defaultDataRetryDelay,
        persister: DataPersister? = defaultDataPersister,
        loader: suspend () -> @Serializable Data,
    ): io.github.asnaeb.remotedata.Data<Key, Data, Unit> {
        return withUniqueDataKey(key) {
            getData(
                key = key,
                params = Unit,
                serializer = serializer,
                reloadInterval = reloadInterval,
                maxRetries = maxRetries,
                retryDelay = retryDelay,
                persister = persister,
                loader = { loader() }
            )
        }
    }

    fun <Params, Data> action(
        key: Any,
        strategy: ConcurrentActionStrategy = defaultActionStrategy,
        action: suspend (Params) -> Data
    ): Action<Params, Data> {
        synchronized(lock) {
            return actionRegistry.getOrPut(key) {
                Action(
                    scope = scope,
                    strategy = strategy,
                    action = action
                )
            }  as Action<Params, Data>
        }
    }
}

inline fun <reified Key : Any, reified Data, reified Params>Registry.factory(
    key: Key,
    reloadInterval: Duration? = defaultDataReloadInterval,
    maxRetries: Int = defaultDataMaxRetries,
    noinline retryDelay: (Int) -> Duration = defaultDataRetryDelay,
    persister: DataPersister? = defaultDataPersister,
    noinline loader: suspend (Params) -> Data
): Factory<Key, Data, Params> {
    val serializer = DataSerializer(
        keySerializer = serializer<Key>(),
        dataSerializer = serializer<Data>(),
        paramsSerializer = serializer<Params>()
    )

    return factory(
        key = key,
        serializer = serializer,
        reloadInterval = reloadInterval,
        maxRetries = maxRetries,
        retryDelay = retryDelay,
        persister = persister,
        loader = loader
    )
}

inline fun <reified Key : Any, reified Data> Registry.data(
    key: Key,
    reloadInterval: Duration? = defaultDataReloadInterval,
    maxRetries: Int = defaultDataMaxRetries,
    noinline retryDelay: (Int) -> Duration = defaultDataRetryDelay,
    persister: DataPersister? = defaultDataPersister,
    noinline loader: suspend () -> Data,
): io.github.asnaeb.remotedata.Data<Key, Data, Unit> {
    val serializer = DataSerializer(
        keySerializer = serializer<Key>(),
        dataSerializer = serializer<Data>(),
        paramsSerializer = serializer<Unit>()
    )

    return data(
        key = key,
        serializer = serializer,
        reloadInterval = reloadInterval,
        maxRetries = maxRetries,
        retryDelay = retryDelay,
        persister = persister,
        loader = { loader() }
    )
}

