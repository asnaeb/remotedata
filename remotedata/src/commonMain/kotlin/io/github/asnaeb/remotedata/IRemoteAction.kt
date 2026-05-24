package io.github.asnaeb.remotedata

import kotlinx.coroutines.flow.StateFlow

interface IRemoteAction<Data, Params> {
    val loading: StateFlow<Boolean>
    val params: StateFlow<Params?>
    val data: StateFlow<Data?>
    val error: StateFlow<Throwable?>

    suspend fun runAsync(params: Params): Data?
}