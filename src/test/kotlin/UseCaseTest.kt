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
}