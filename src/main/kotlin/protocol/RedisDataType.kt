package protocol

enum class RedisDataType(
    val typeName: String
) {
    STRING("string"),
    LIST("list"),
    SORTED_SET("sorted_set"),
    SET("set"),
    ZSET("zset"),
    HASH("hash"),
    STREAM("stream"),
    VECTORSET("vectorset"),
    NONE("none")
}
