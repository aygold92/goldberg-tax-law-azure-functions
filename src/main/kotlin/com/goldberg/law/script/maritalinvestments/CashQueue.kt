package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.util.clean
import java.math.BigDecimal
import java.util.LinkedList
import kotlin.minus
import kotlin.plus

class CashQueue {
    private val queue: LinkedList<QueueItem> = LinkedList()

    fun add(amt: BigDecimal, preMaritalPercentage: BigDecimal) {
        queue.add(QueueItem(amt.clean(), preMaritalPercentage))
    }

    fun totalByClassification(): Pair<BigDecimal, BigDecimal> {
        var premaritalSum = BigDecimal.ZERO
        var maritalSum = BigDecimal.ZERO

        for ((amount, preMaritalPercent) in queue) {
            val preMaritalAmount = amount * preMaritalPercent
            premaritalSum += preMaritalAmount
            maritalSum += (amount - preMaritalAmount)
        }

        return Pair(premaritalSum.clean(), maritalSum.clean())
    }

    fun poll(amt: BigDecimal): Pair<BigDecimal, BigDecimal> {
        var remaining = amt
        var premaritalPolled = BigDecimal.ZERO
        var maritalPolled = BigDecimal.ZERO

        while (remaining > BigDecimal.ZERO && queue.isNotEmpty()) {
            val (blockAmt, blockPreMaritalPercent) = queue.poll()!!

            if (blockAmt <= remaining) {
                remaining -= blockAmt
                val preMaritalBlockAmount = blockAmt * blockPreMaritalPercent
                premaritalPolled += preMaritalBlockAmount
                maritalPolled += (blockAmt - preMaritalBlockAmount)
            } else {
                val preMaritalBlockRemainingAmount = remaining * blockPreMaritalPercent
                premaritalPolled += preMaritalBlockRemainingAmount
                maritalPolled += (remaining - preMaritalBlockRemainingAmount)
                queue.addFirst(QueueItem((blockAmt - remaining).clean(), blockPreMaritalPercent))
                remaining = BigDecimal.ZERO
            }
        }

        return Pair(premaritalPolled.clean(), maritalPolled.clean())
    }

    override fun toString(): String {
        return queue.toString()
    }

    data class QueueItem(
        val amt: BigDecimal,
        val preMaritalPercentage: BigDecimal,
    )
}