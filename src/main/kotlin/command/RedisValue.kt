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
            value ?: return "$0\r\n\r\n"

            return "$${value.length}\r\n${value}\r\n"
        }
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
