package command

sealed class RedisValue {

    abstract fun encodeValue(): String

    class SimpleString(val value: String) : RedisValue() {

        override fun encodeValue(): String {
            return "+${value}\r\n"
        }
    }

    class BulkString(val value: String) : RedisValue() {

        override fun encodeValue(): String {
            return "$${value.length}\r\n${value}\r\n"
        }
    }

    object NullBulkString : RedisValue() {
        override fun encodeValue(): String = "$-1\r\n"
    }

    class Integer(val value: Int) : RedisValue() {
        override fun encodeValue(): String = ":$value\r\n"
    }

    class Array(val elements: List<RedisValue>) : RedisValue() {

        override fun encodeValue(): String {
            val arrayLength = elements.size

            return "${arrayLength}\r\n${elements.joinToString { it.encodeValue() }}"
        }
    }

    class Error(val message: String) : RedisValue() {

        override fun encodeValue(): String {
            return message
        }
    }
}

fun List<RedisValue>.getBulkString(index: Int): RedisValue.BulkString? =
    getOrNull(index) as? RedisValue.BulkString

fun List<RedisValue>.getBulkStrings(skip: Int = 0): List<RedisValue.BulkString> =
    drop(skip)
        .filter { it is RedisValue.BulkString }
        .map { it as RedisValue.BulkString }


