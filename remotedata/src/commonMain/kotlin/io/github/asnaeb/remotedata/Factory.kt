package io.github.asnaeb.remotedata

import kotlinx.coroutines.flow.Flow

class Factory<Key : Any, Data, Params>(
    private val dataFactory: (Params) -> io.github.asnaeb.remotedata.Data<Key, Data, Params>,
    private val dataFlowFactory: (Flow<Params>) -> DataFlow<Key, Data, Params>
) {
    fun data(params: Params) = dataFactory(params)
    fun dataFlow(flow: Flow<Params>) = dataFlowFactory(flow)
}