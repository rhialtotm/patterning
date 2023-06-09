package patterning

import java.math.BigDecimal
import java.math.BigInteger


class FlexibleInteger(initialValue: Number) : Comparable<FlexibleInteger> {

    private val value: Number = when (initialValue) {
        is BigInteger -> {
            when (initialValue) {
                in INT_MIN_BIG_INTEGER..INT_MAX_BIG_INTEGER -> initialValue.toInt()
                in LONG_MIN_BIG_INTEGER..LONG_MAX_BIG_INTEGER -> initialValue.toLong()
                else -> initialValue
            }
        }

        is Long -> {
            when (initialValue) {
                in INT_MIN..INT_MAX -> initialValue.toInt()
                else -> initialValue
            }
        }

        is Int -> initialValue

        else -> throw IllegalArgumentException("Unsupported number type")
    }


    operator fun plus(other: FlexibleInteger): FlexibleInteger {
        return when {
            value is Int && other.value is Int -> handleIntAddition(value, other.value)
            value is Int && other.value is Long -> handleLongAddition(value.toLong(), other.value)
            value is Int && other.value is BigInteger -> handleBigIntegerAddition(value.toBigIntegerSafe(), other.value)
            value is Long && other.value is Int -> handleLongAddition(value, other.value.toLong())
            value is Long && other.value is Long -> handleLongAddition(value, other.value)
            value is Long && other.value is BigInteger -> handleBigIntegerAddition(
                value.toBigIntegerSafe(),
                other.value
            )

            value is BigInteger && other.value is BigInteger -> handleBigIntegerAddition(value, other.value)

            // the possible choice for else is going to be either an int or a long
            else -> handleBigIntegerAddition(value.toBigIntegerSafe(), other.value.toBigIntegerSafe())
        }
    }

    private fun handleIntAddition(a: Int, b: Int): FlexibleInteger {
        val result = a + b
        return if (isOverflowInt(a, b, result)) {
            FlexibleInteger(a.toLong() + b.toLong())
        } else {
            FlexibleInteger(result)
        }
    }

    private fun isOverflowInt(a: Int, b: Int, result: Int): Boolean {
        return (a > 0 && b > 0 && result < 0) || (a < 0 && b < 0 && result > 0)
    }

    private fun handleLongAddition(a: Long, b: Long): FlexibleInteger {
        val result = a + b
        return if (isOverflowLong(a, b, result)) {
            FlexibleInteger(BigInteger.valueOf(a) + BigInteger.valueOf(b))
        } else {
            FlexibleInteger(result)
        }
    }

    private fun isOverflowLong(a: Long, b: Long, result: Long): Boolean {
        return (a > 0 && b > 0 && result < 0) || (a < 0 && b < 0 && result > 0)
    }

    private fun handleBigIntegerAddition(a: BigInteger, b: BigInteger): FlexibleInteger {
        return FlexibleInteger(a + b)
    }

    fun get(): Number = value

    fun isOne(): Boolean = when (value) {
        is Int -> value == 1
        is Long -> value == 1L
        is BigInteger -> value == BigInteger.ONE
        else -> throw IllegalArgumentException("Unexpected type")
    }

    fun isNotZero(): Boolean = when (value) {
        is Int -> value != 0
        is Long -> value != 0L
        is BigInteger -> value != BigInteger.ZERO
        else -> throw IllegalArgumentException("Unexpected type")
    }

    fun isZero(): Boolean = when (value) {
        is Int -> value == 0
        is Long -> value == 0L
        is BigInteger -> value == BigInteger.ZERO
        else -> throw IllegalArgumentException("Unexpected type")
    }

    fun shiftLeft(n: Int): Int {
        if (value is Int) {
            return (value) shl n
        } else {
            throw IllegalArgumentException("Operation only supported for Int values.")
        }
    }

    fun or(other: FlexibleInteger): Int {
        if (value is Int && other.value is Int) {
            return (value) or (other.value)
        } else {
            throw IllegalArgumentException("Operation only supported for Int values.")
        }
    }

    fun toInt(): Int {
        if (value is Int) {
            return value
        } else {
            throw IllegalArgumentException("Operation only supported for Int values.")
        }
    }

    fun negate(): FlexibleInteger {
        return when (value) {
            is Int -> FlexibleInteger(-value)
            is Long -> FlexibleInteger(-value)
            is BigInteger -> FlexibleInteger(value.negate())
            else -> throw IllegalArgumentException("Unsupported number type")
        }
    }

    fun addOne(): FlexibleInteger {
        return this + ONE
    }

    operator fun minus(other: FlexibleInteger): FlexibleInteger {
        return when {
            value is Int && other.value is Int -> FlexibleInteger(value - other.value)
            value is Int && other.value is Long -> FlexibleInteger(value.toLong() - other.value)
            value is Long && other.value is Long -> FlexibleInteger(value - other.value)
            value is Long && other.value is Int -> FlexibleInteger(value - other.value.toLong())
            value is BigInteger && other.value is BigInteger -> FlexibleInteger(value - other.value)
            else -> FlexibleInteger(value.toBigIntegerSafe() - other.value.toBigIntegerSafe())
        }
    }

    fun min(other: FlexibleInteger): FlexibleInteger {
        return when {
            value is Int && other.value is Int -> if (value <= other.value) this else other
            value is Int && other.value is Long -> if (value.toLong() <= other.value) this else other
            value is Long && other.value is Long -> if (value <= other.value) this else other
            value is Long && other.value is Int -> if (value <= other.value.toLong()) this else other
            value is BigInteger && other.value is BigInteger -> if (value <= other.value) this else other
            else -> if (value.toBigIntegerSafe() <= other.value.toBigIntegerSafe()) this else other
        }
    }

    fun max(other: FlexibleInteger): FlexibleInteger {
        return when {
            value is Int && other.value is Int -> if (value >= other.value) this else other
            value is Int && other.value is Long -> if (value.toLong() >= other.value) this else other
            value is Long && other.value is Long -> if (value >= other.value) this else other
            value is Long && other.value is Int -> if (value >= other.value.toLong()) this else other
            value is BigInteger && other.value is BigInteger -> if (value >= other.value) this else other
            else -> if (value.toBigIntegerSafe() >= other.value.toBigIntegerSafe()) this else other
        }
    }

    override fun compareTo(other: FlexibleInteger): Int {
        return when {
            value is Int && other.value is Int -> value.compareTo(other.value)
            value is Int && other.value is Long -> (value.toLong()).compareTo(other.value)
            value is Long && other.value is Long -> value.compareTo(other.value)
            value is Long && other.value is Int -> value.compareTo(other.value.toLong())
            value is BigInteger && other.value is BigInteger -> (value).compareTo(other.value)
            else -> value.toBigIntegerSafe().compareTo(other.value.toBigIntegerSafe())
        }
    }

    fun toBigDecimal(): BigDecimal {
        return when (value) {
            is Int -> BigDecimal(value)
            is Long -> BigDecimal(value)
            is BigInteger -> BigDecimal(value)
            else -> BigDecimal(value.toBigIntegerSafe())
        }
    }

    private fun Number.toBigIntegerSafe(): BigInteger {
        return when (this) {
            is BigInteger -> this
            is Int -> BigInteger.valueOf(toLong())
            is Long -> BigInteger.valueOf(this)
            else -> throw IllegalArgumentException("Unsupported number type")
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FlexibleInteger) return false
        return value == other.value
    }

    override fun hashCode(): Int {
        return value.hashCode()
    }

    companion object {
        val ONE = FlexibleInteger(1)
        val ZERO = FlexibleInteger(0)
        private const val INT_MIN = Int.MIN_VALUE
        private const val INT_MAX = Int.MAX_VALUE
        private val INT_MIN_BIG_INTEGER = Int.MIN_VALUE.toBigInteger()
        private val INT_MAX_BIG_INTEGER = Int.MAX_VALUE.toBigInteger()
        private val LONG_MIN_BIG_INTEGER = Long.MIN_VALUE.toBigInteger()
        private val LONG_MAX_BIG_INTEGER = Long.MAX_VALUE.toBigInteger()

    }

}