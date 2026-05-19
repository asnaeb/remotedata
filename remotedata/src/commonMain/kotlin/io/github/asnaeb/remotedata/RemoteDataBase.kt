package io.github.asnaeb.remotedata

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Instant

internal class RemoteDataBase<Data, Params>(
    private val params: Params,
    private val loader: suspend (Params) -> Data?
) : IRemoteData<Data> {
    private val mutex = Mutex()

    private var job: Job? = null

    @Volatile
    var isVirgin: Boolean = true
        private set

    @Volatile
    var lastLoaded: Instant? = null
        private set

    private val _loading: MutableStateFlow<Boolean> = MutableStateFlow(false)
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _data: MutableStateFlow<Data?> = MutableStateFlow(null)
    override val data: StateFlow<Data?> = _data.asStateFlow()

    private val _error: MutableStateFlow<Throwable?> = MutableStateFlow(null)
    override val error: StateFlow<Throwable?> = _error.asStateFlow()

    override fun setData(data: Data?) {
        if (data != null) {
            isVirgin = false
        }
        _data.value = data
        _error.value = null
        lastLoaded = Clock.System.now()
    }

    override fun setData(function: (Data?) -> Data) {
        _data.update {
            if (it != null) {
                isVirgin = false
            }

            function(it)
        }
        _error.value = null
        lastLoaded = Clock.System.now()
    }

    suspend fun loadSuspend(throwOnError: Boolean) = coroutineScope {
        val lockedJob: Job? = mutex.withLock {
            if (job?.isActive == true) {
                job
            }
            else {
                job = launch {
                    _loading.value = true

                    try {
                        setData(loader(params))
                    }
                    catch (e: Throwable) {
                        println(e) // <-- TODO Remove log

                        if (throwOnError || e is CancellationException) {
                            throw e
                        }

                        launch {
                            _error.value = e
                        }
                        launch {
                            _data.value = null
                        }
                        launch {
                            lastLoaded = null
                        }
                    }
                    finally {
                        _loading.value = false
                    }
                }

                null
            }
        }

        lockedJob?.join()
    }

    fun cancel() {
        job?.let {
            if (it.isActive) {
                it.cancel()
            }
        }
    }
}