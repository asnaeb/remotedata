package io.github.asnaeb.remotedata

import kotlinx.coroutines.flow.StateFlow

interface IRemoteData<T> {
    val loading: StateFlow<Boolean>
    val data: StateFlow<T?>
    val error: StateFlow<Throwable?>
    fun setData(data: T?)
    fun setData(function: (T?) -> T)
}