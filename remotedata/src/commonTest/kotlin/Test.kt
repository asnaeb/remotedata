
import io.github.asnaeb.remotedata.Registry
import io.github.asnaeb.remotedata.data
import io.github.asnaeb.remotedata.factory
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class Test {
    val registry = Registry()
    val dynamic = registry.factory("dynamic") { param: Int -> "Hello $param" }
    val static = registry.data("static") { "" }

    @Test
    fun `initial test`() = runTest {
        val r1 = dynamic.data(1)
        val r2 = dynamic.data(2)

        val r1Result = r1.observe(backgroundScope)
        static.observe(backgroundScope).loadSuspending()
    }
}