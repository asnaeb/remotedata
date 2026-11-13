import io.github.asnaeb.remotedata.RemoteDataRegistry
import io.github.asnaeb.remotedata.data
import io.github.asnaeb.remotedata.dataFactory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class Test {
    val registry = RemoteDataRegistry()
    val dynamic = registry.dataFactory("dynamic") { param: Int -> "Hello $param" }
    val static = registry.data("static") { "" }

    @Test
    fun `initial test`() = runTest {
        val r1 = dynamic(1)
        val r2 = dynamic(2)

        val r1Result = r1.observe(backgroundScope)
        static.observe(backgroundScope).loadSuspending()
    }
}