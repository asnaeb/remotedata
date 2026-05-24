package io.github.asnaeb.remotedata

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal class RemoteActionBase<Data, Params>(private val action: suspend (Params) -> Data) :
    IRemoteAction<Data, Params> {
    private val _loading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _params: MutableStateFlow<Params?> = MutableStateFlow(null)
    override val params: StateFlow<Params?> = _params.asStateFlow()

    private val _data: MutableStateFlow<Data?> = MutableStateFlow(null)
    override val data: StateFlow<Data?> = _data.asStateFlow()

    private val _error: MutableStateFlow<Throwable?> = MutableStateFlow(null)
    override val error: StateFlow<Throwable?> = _error.asStateFlow()

    private suspend fun runSuspend(params: Params, throwOnError: Boolean = false) = coroutineScope {
        launch {
            _loading.update { true }
        }

        launch {
            _params.update { params }
        }

        try {
            val result: Data = action(params)
            _data.update { result }
        }
        catch (e: Throwable) {
            launch {
                _error.update { e }
            }

            launch {
                _data.update { null }
            }

            if (throwOnError || e is CancellationException) {
                throw e
            }
        }
        finally {
            _loading.update { false }
        }
    }

    suspend fun runAsync(params: Params): Data? {
        runSuspend(params, throwOnError = true)
        return data.value
    }
}