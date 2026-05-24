package io.github.asnaeb.remotedata

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class RemoteAction<Data, Params> internal constructor(
    private val base: RemoteActionBase<Data, Params>,
    private val scope: CoroutineScope
) : IRemoteAction<Data, Params> by base {
    fun run(
        params: Params,
        onSuccess: (suspend (Data?) -> Unit)? = null,
        onError: (suspend (Throwable) -> Unit)? = null,
        onCancel: (suspend (CancellationException) -> Unit)? = null,
        onSettled: (suspend () -> Unit)? = null
    ) {
        scope.launch {
            try {
                val data: Data? = base.runAsync(params)

                onSuccess?.let {
                    scope.launch { it(data) }
                }
            }
            catch (e: Throwable) {
                if (e is CancellationException) {
                    onCancel?.let {
                        scope.launch { it(e) }
                    }
                }
                else {
                    onError?.let {
                        scope.launch { it(e) }
                    }
                }
            }
            finally {
                onSettled?.let {
                    scope.launch { it() }
                }
            }
        }
    }

    fun loading(params: Params): Flow<Boolean> = loading
        .combine(this.params) {
            loading, currentParams -> loading && params == currentParams
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)
}