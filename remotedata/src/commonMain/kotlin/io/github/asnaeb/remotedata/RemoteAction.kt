package io.github.asnaeb.remotedata


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

class RemoteAction<Params, Data> internal constructor(
    private val scope: CoroutineScope,
    private val strategy: ConcurrentActionStrategy,
    private val action: suspend (Params) -> Data
) : RemoteActionHandle<Params, Data> {
    val results: StateFlow<Map<Params, ActionResult<Params, Data>>>
        field = MutableStateFlow<Map<Params, ActionResult<Params, Data>>>(emptyMap())

    private val mutex = Mutex()
    private val jobs = mutableMapOf<Params, Deferred<ActionResult<Params, Data>>>()

    private suspend fun runAction(params: Params): ActionResult<Params, Data> {
        var prev: ActionResult<Params, Data>? = null

        val loading = results.updateAndGet {
            prev = it[params]

            it.toMutableMap().apply {
                set(params, ActionResult.Loading(params = params, staleData = prev?.data()))
            }
        }

        try {
            val data = action(params)
            val result = ActionResult.Success(params = params, data = data)

            results.update {
                it.toMutableMap().apply {
                    set(params, result)
                }
            }

            return result
        }
        catch (e: Throwable) {
            if (e is CancellationException) {
                results.update {
                    if (it[params] !== loading[params]) {
                        it
                    }
                    else {
                        it.toMutableMap().apply {
                            if (prev != null) {
                                set(params, prev)
                            }
                            else {
                                remove(params)
                            }
                        }
                    }
                }

                throw e
            }

            return results.updateAndGet {
                it.toMutableMap().apply {
                    set(
                        params,
                        ActionResult.Error(
                            params = params,
                            error = e,
                            staleData = get(params)?.data()
                        )
                    )
                }
            }[params]!!
        }
    }

    private fun start(params: Params, toJoin: Job?): Deferred<ActionResult<Params, Data>> {
        return scope.async { toJoin?.join(); runAction(params) }.also { jobs[params] = it }
    }

    internal suspend fun internalRunSuspending(params: Params): ActionResult<Params, Data> {
        val def = mutex.withLock {
            jobs.values.removeAll { !it.isActive }

            val currentJob = jobs[params]

            if (currentJob == null) {
                start(params, null)
            }
            else when (strategy) {
                ConcurrentActionStrategy.CANCEL -> {
                    currentJob.cancel()
                    start(params, currentJob)
                }

                ConcurrentActionStrategy.JOIN -> currentJob

                ConcurrentActionStrategy.QUEUE -> start(params, currentJob)
            }
        }

        return def.await()
    }

    override suspend fun runSuspending(params: Params): Data? {
        return when (val result = internalRunSuspending(params)) {
            is ActionResult.Success -> result.data
            is ActionResult.Error -> throw result.error
            is ActionResult.Loading<*, *> -> null
        }
    }

    override fun result(params: Params): Flow<ActionResult<Params, Data>?> {
        return results.map { it[params] }
    }

    fun runner(scope: CoroutineScope) = RemoteActionRunner(this, scope)
}