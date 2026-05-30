package io.github.asnaeb.remotedata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

open class RemoteDataRegistry(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
    private val defaultLoadOnInit: Boolean = false,
    private val defaultStaleTime: Duration = 5.minutes
) {
    private val dataRegistry: MutableMap<RemoteDataKey<*>, RemoteDataBase<*, *>> = mutableMapOf()
    private val actionRegistry: MutableMap<String, RemoteActionBase<*, *>> = mutableMapOf()

    fun <Data, Params> useRemoteData(
        id: String,
        scope: CoroutineScope = this.scope,
        loadOnInit: Boolean = defaultLoadOnInit,
        staleTime: Duration = defaultStaleTime,
        loader: suspend (Params) -> Data? = { error("Loader not provided") },
    ): (Params) -> RemoteData<Data> = { params ->
        val key: RemoteDataKey<Params> = RemoteDataKey(id, params)
        val base: RemoteDataBase<*, *> = dataRegistry[key] ?: RemoteDataBase(key.params, loader).also {
            dataRegistry[key] = it
        }

        @Suppress("UNCHECKED_CAST")
        (RemoteData(
            base = base as RemoteDataBase<Data, Params>,
            scope = scope,
            loadOnInit = loadOnInit,
            staleTime = staleTime
        ))
    }

    fun <Data> useStaticRemoteData(
        id: String,
        scope: CoroutineScope = this.scope,
        loadOnInit: Boolean = defaultLoadOnInit,
        staleTime: Duration = defaultStaleTime,
        loader: suspend () -> Data? = { error("Loader not provided") },
    ): RemoteData<Data> = useRemoteData<Data, Unit>(
        id = id,
        scope = scope,
        loadOnInit = loadOnInit,
        staleTime = staleTime,
        loader = { loader() }
    )(Unit)

    fun <Data, Params> useRemoteAction(
        id: String,
        scope: CoroutineScope = this.scope,
        action: suspend (Params) -> Data = { error("Action not provided") }
    ): RemoteAction<Data, Params> {
        val base: RemoteActionBase<*, *> = actionRegistry[id] ?: RemoteActionBase(action).also {
            actionRegistry[id] = it
        }

        @Suppress("UNCHECKED_CAST")
        return RemoteAction(base as RemoteActionBase<Data, Params>, scope)
    }
}