package fixture

expect fun platformName(): String

class KmpLogic {
    fun classify(value: Int): String {
        return when {
            value < 0 -> "negative"
            value == 0 -> "zero"
            else -> "positive"
        }
    }
}
