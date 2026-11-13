package io.github.asnaeb.remotedata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RemoteActionRunner<Params, Data> internal constructor(
    private val action: RemoteAction<Params, Data>,
    private val scope: CoroutineScope,
) : RemoteActionHandle<Params, Data> by action {
    private val lastParams = MutableStateFlow<Params?>(null)

    val currentResult: Flow<ActionResult<Params, Data>?> =
        combine(lastParams, action.results) { last, results -> last?.let { results[it] } }
            .distinctUntilChanged()

    private suspend fun internalRunSuspending(params: Params): ActionResult<Params, Data> {
        lastParams.update { params }
        return action.internalRunSuspending(params)
    }

    override suspend fun runSuspending(params: Params): Data? {
        lastParams.update { params }
        return action.runSuspending(params)
    }

    fun run(
        params: Params,
        onSuccess: (suspend (Data) -> Unit)? = null,
        onError: (suspend (Throwable) -> Unit)? = null,
        onCancel: (suspend () -> Unit)? = null,
        onSettled: (suspend () -> Unit)? = null
    ) {
        scope.launch {
            val result = try {
                internalRunSuspending(params)
            }
            catch (e: CancellationException) {
                if (isActive) {
                    try {
                        onCancel?.invoke()
                    }
                    finally {
                        onSettled?.invoke()
                    }
                }

                throw e
            }

            try {
                when (result) {
                    is ActionResult.Success -> onSuccess?.invoke(result.data)
                    is ActionResult.Error -> onError?.invoke(result.error)
                    is ActionResult.Loading -> Unit
                }
            }
            finally {
                onSettled?.invoke()
            }
        }
    }

    fun reset() {
        lastParams.update { null }
    }
}