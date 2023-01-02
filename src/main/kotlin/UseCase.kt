import java.io.FileWriter
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Path

class UseCase(id: String, title: String, designScope: DesignScope, goalLevel: GoalLevel) {

    private val metadata = Metadata(id, title, designScope, goalLevel)
    private val systems = LinkedHashMap<Any, System>()
    private val goals = LinkedHashMap<System, String>()
    private val steps = mutableListOf<Step>()

    enum class DesignScope(val icon: String) {
        ORGANIZATION("🏠"),
        SYSTEM("📦"),
        COMPONENT("🔩")
    }
    enum class GoalLevel(val icon: String, val character: String) {
        VERY_HIGH_SUMMARY("☁︎", "++"),
        SUMMARY("🪁", "+"),
        USER_GOAL("🌊", "!"),
        SUB_FUNCTION("🐟", "−")
    }
    data class Metadata(val id: String, val title: String, val designScope: DesignScope, val goalLevel: GoalLevel)
    data class System(val name: String, val description: String)
    data class Step(val label: String, val content: StepContent)
    sealed interface StepContent
    data class Activity(val actor: Any, val name: String) : StepContent
    data class Message(val sender: Any, val name: String, val receiver: Any) : StepContent

    fun system(system: Any, name: String, description: String, goal: String) {
        val s = System(name, description)
        systems[system] = s
        goals[s] = goal
    }

    fun system(system: Any, name: String, description: String) {
        systems[system] = System(name, description)
    }

    fun activity(label: String, actor: Any, name: String) {
        steps.add(Step(label, Activity(actor, name)))
    }

    fun writeTo(path: Path) {
        Files.createDirectories(path.parent)
        FileWriter(path.toFile()).use { fileWriter ->
            PrintWriter(fileWriter).use { printWriter ->
                printWriter.println("## Use case ${metadata.id} \uD83D\uDCE6${metadata.goalLevel.icon}")
                printWriter.println("# ${metadata.title}")
            }
        }
    }

    companion object {
        const val NOTE_WIDTH = 40
    }
}