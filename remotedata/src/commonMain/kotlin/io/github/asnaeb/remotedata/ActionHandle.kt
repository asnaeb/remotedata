package io.github.asnaeb.remotedata

import kotlinx.coroutines.flow.Flow

interface ActionHandle<Params, Data> {
    suspend fun runSuspending(params: Params): Data?

    fun result(params: Params): Flow<ActionResult<Params, Data>?>
}