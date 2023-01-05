data class UseCase2(
    val id: Identifier,
    val title: Title,
    val designScope: DesignScope = DesignScope.SYSTEM,
    val goalLevel: GoalLevel = GoalLevel.USER_GOAL,
    val systems: List<System> = emptyList(),
) {

    constructor(id: String, title: String) : this(Identifier(id), Title(title))

    fun define(reference: Any, name: String, description: String, goals: List<String> = emptyList()) =
        copy(systems = systems + System(reference, name, description, goals.map(::Goal)))

    @JvmInline
    value class Identifier(val value: String)

    @JvmInline
    value class Title(val value: String)

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

    data class System(
        val reference: Any,
        val name: String,
        val description: String,
        val goals: List<Goal>
    )

    @JvmInline
    value class Goal(val description: String)

    data class Scenario(val title: String, val steps: List<Step>)

    data class Step(val label: String, val content: Content) {

        sealed interface Content

        data class Activity(val actor: Any, val name: String) : Content

        data class Message(val sender: Any, val name: String, val receiver: Any) : Content
    }
}
