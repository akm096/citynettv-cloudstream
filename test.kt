import java.util.UUID

fun main() {
    val user = "+994501234567"
    val fullUuid = UUID.nameUUIDFromBytes("CityNetTV-CS-$user".toByteArray()).toString()
    val androidIdStyle = fullUuid.replace("-", "").substring(0, 16)
    println(fullUuid)
    println(androidIdStyle)
}
