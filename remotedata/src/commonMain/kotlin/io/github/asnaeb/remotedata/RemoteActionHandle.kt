package io.github.asnaeb.remotedata

import kotlinx.coroutines.flow.Flow

interface RemoteActionHandle<Params, Data> {
    suspend fun runSuspending(params: Params): Data?

    fun result(params: Params): Flow<ActionResult<Params, Data>?>
}