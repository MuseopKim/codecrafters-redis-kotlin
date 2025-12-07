package command

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Condition
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max
import kotlin.math.min

class RedisDataStore {

    private val lock = ReentrantLock()

    private val strings = mutableMapOf<String, Entry>()
    private val lists = mutableMapOf<String, ArrayDeque<String>>()
    private val lockConditions = mutableMapOf<String, Condition>()

    fun set(key: String, value: String, px: Long? = null) {
        val expiresAt = px?.let { System.currentTimeMillis() + it }

        strings[key] = Entry(value, expiresAt)
    }

    fun get(key: String): String? {
        val currentTime = System.currentTimeMillis()

        val entry = strings[key] ?: return null

        if (entry.isExpired(currentTime)) {
            strings.remove(key)
            return null
        }

        return entry.value
    }

    fun lpush(key: String, values: List<String>): Int {
        val list = lists.getOrPut(key) { ArrayDeque() }
        values.forEach { list.addFirst(it) }

        return list.size
    }

    fun rpush(key: String, values: List<String>): Int {
        lock.lock()
        try {
            val list = lists.getOrPut(key) { ArrayDeque() }
            list.addAll(values)

            lockConditions[key]?.signalAll()

            return list.size
        } finally {
            lock.unlock()
        }
    }

    fun lrange(key: String, start: Int, stop: Int): List<String> {
        val list = lists[key] ?: return emptyList()
        val length = list.size

        val normalizedStart = if (start < 0) {
            max(0, start + length)
        } else {
            start
        }

        val normalizedStop = if (stop < 0) {
            max(0, stop + length)
        } else {
            stop
        }

        if (normalizedStart > normalizedStop) {
            return emptyList()
        }

        if (normalizedStart >= length) {
            return emptyList()
        }

        return list.subList(normalizedStart, min(normalizedStop, length - 1) + 1)
    }

    fun llen(key: String): Int {
        return lists[key]?.size ?: 0
    }

    fun lpop(key: String, count: Int?): List<String> {
        val list = lists[key]
        if (list.isNullOrEmpty()) {
            return emptyList()
        }

        val length = list.size
        val normalizedCount = min(length, count ?: 1)

        return List(normalizedCount) { list.removeFirst() }
    }

    fun blpop(key: String, timeoutMilliSeconds: Long): String? {
        lock.lock()
        try {
            val list = lists.getOrPut(key) { ArrayDeque() }
            val condition = lockConditions.getOrPut(key) { lock.newCondition() }
            val timeoutNano = TimeUnit.MILLISECONDS.toNanos(timeoutMilliSeconds)
            val deadLineNano = System.nanoTime() + timeoutNano

            while (list.isEmpty()) {
                val remainingNano = deadLineNano - System.nanoTime()
                if (timeoutMilliSeconds == 0L) {
                    condition.await()
                    continue
                }

                if (remainingNano <= 0) {
                    return null
                }

                condition.awaitNanos(remainingNano)

            }

            return list.removeFirst()
        } finally {
            lock.unlock()
        }
    }

    fun <R> atomic(operation: () -> R): R {
        lock.lock()
        return try {
            operation()
        } finally {
            lock.unlock()
        }
    }

    data class Entry(
        val value: String,
        val expiresAt: Long? = null
    ) {
        fun isExpired(currentTimestamp: Long): Boolean =
            expiresAt != null && expiresAt <= currentTimestamp
    }
}

