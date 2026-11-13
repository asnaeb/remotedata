package io.github.asnaeb.remotedata

import kotlin.time.Instant

sealed interface DataResult<T> {
    data class Error<Data>(
        val error: Throwable,
        val staleData: Data?,
        val lastLoaded: Instant?,
        val failedAt: Instant
    ) : DataResult<Data>

    data class Success<Data>(
        val data: Data,
        val lastLoaded: Instant
    ) : DataResult<Data>

    data class Loading<Data>(
        val staleData: Data?,
        val lastLoaded: Instant?
    ) : DataResult<Data>

    class Empty<Data> : DataResult<Data>

    fun lastLoaded(): Instant? = when (this) {
        is Success<T> -> lastLoaded
        is Loading<T> -> lastLoaded
        is Error<T> -> lastLoaded
        else -> null
    }

    fun data(): T? = when (this) {
        is Success<T> -> data
        is Loading<T> -> staleData
        is Error<T> -> staleData
        else -> null
    }

    fun isEmpty(): Boolean = this is Empty<T>

    fun error(): Throwable? = when (this) {
        is Error<T> -> error
        else -> null
    }

    fun loading(): Boolean = this is Loading<T>

    fun initializing(): Boolean = this is Loading<T> && lastLoaded == null

    fun reloading(): Boolean = this is Loading<T> && lastLoaded != null
}