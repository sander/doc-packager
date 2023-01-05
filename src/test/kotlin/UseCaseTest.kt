import org.junit.jupiter.api.Test
import java.nio.file.Path

internal class UseCaseTest {

    @Test
    fun useCaseTest() {
        val uc = UseCase("UC002", "My use case", UseCase.DesignScope.SYSTEM, UseCase.GoalLevel.USER_GOAL)
        val x = Object()
        uc.system(x, "x", "description")
        uc.activity("01", x, "does something")
        uc.writeTo(Path.of("doc/uc/002.md"))
    }

    val x = Object()
    val y = Object()
    var uc = UseCase2("UC003", "My use case")

    @Test
    fun useCase2Test() {
        uc = uc
            .define(x, "x", "description", listOf())
            .define(y, "y", "foo", listOf())
        println(uc)
    }
}