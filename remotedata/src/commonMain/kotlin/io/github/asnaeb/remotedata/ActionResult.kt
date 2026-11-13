package io.github.asnaeb.remotedata

sealed interface ActionResult<Params, Data> {
    data class Success<Params, Data>(val params: Params, val data: Data) : ActionResult<Params, Data>

    data class Loading<Params, Data>(val params: Params, val staleData: Data?) : ActionResult<Params, Data>

    data class Error<Params, Data>(
        val error: Throwable,
        val params: Params,
        val staleData: Data?,
    ) : ActionResult<Params, Data>

    fun data(): Data? = when (this) {
        is Success -> data
        is Error -> staleData
        is Loading -> staleData
    }

    fun error(): Throwable? = when (this) {
        is Error -> error
        else -> null
    }

    fun loading(): Boolean = this is Loading

    fun params(): Params? = when (this) {
        is Success -> params
        is Error -> params
        is Loading -> params
    }
}