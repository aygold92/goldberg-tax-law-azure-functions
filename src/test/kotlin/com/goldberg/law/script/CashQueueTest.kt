package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.CashQueue
import com.goldberg.law.util.bd
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class CashQueueTest {

    @Nested
    inner class TotalByClassification {

        @Test
        fun `empty queue returns zero`() {
            val queue = CashQueue()
            assertThat(queue.totalByClassification()).bigDecimalCompare()
                .isEqualTo(Pair(BigDecimal.ZERO, BigDecimal.ZERO))
        }

        @Test
        fun `single fully pre-marital item`() {
            val queue = CashQueue()
            queue.add(200.bd(), BigDecimal.ONE)
            assertThat(queue.totalByClassification()).bigDecimalCompare()
                .isEqualTo(Pair(200.bd(), BigDecimal.ZERO))
        }

        @Test
        fun `single fully marital item`() {
            val queue = CashQueue()
            queue.add(150.bd(), BigDecimal.ZERO)
            assertThat(queue.totalByClassification()).bigDecimalCompare()
                .isEqualTo(Pair(BigDecimal.ZERO, 150.bd()))
        }

        @Test
        fun `single item at fractional ratio`() {
            val queue = CashQueue()
            queue.add(100.bd(), "0.6".bd())
            assertThat(queue.totalByClassification()).bigDecimalCompare()
                .isEqualTo(Pair(60.bd(), 40.bd()))
        }

        @Test
        fun `multiple items with different ratios are summed correctly`() {
            val queue = CashQueue()
            queue.add(100.bd(), BigDecimal.ONE)  // 100 PM, 0 M
            queue.add(200.bd(), BigDecimal.ZERO) // 0 PM, 200 M
            queue.add(60.bd(), "0.5".bd())       // 30 PM, 30 M
            assertThat(queue.totalByClassification()).bigDecimalCompare()
                .isEqualTo(Pair(130.bd(), 230.bd()))
        }
    }

    @Nested
    inner class Poll {

        @Nested
        inner class SingleBlock {

            @Test
            fun `zero amount returns zero without modifying queue`() {
                val queue = CashQueue()
                queue.add(100.bd(), "0.6".bd())
                val result = queue.poll(BigDecimal.ZERO)
                assertThat(result).bigDecimalCompare().isEqualTo(Pair(BigDecimal.ZERO, BigDecimal.ZERO))
                assertThat(queue.totalByClassification()).bigDecimalCompare().isEqualTo(Pair(60.bd(), 40.bd()))
            }

            @Test
            fun `empty queue returns zero`() {
                val queue = CashQueue()
                assertThat(queue.poll(50.bd())).bigDecimalCompare()
                    .isEqualTo(Pair(BigDecimal.ZERO, BigDecimal.ZERO))
            }

            @Test
            fun `less than block amount leaves remainder with original ratio`() {
                val queue = CashQueue()
                queue.add(100.bd(), "0.6".bd())

                assertThat(queue.poll(40.bd())).bigDecimalCompare().isEqualTo(Pair(24.bd(), 16.bd()))
                assertThat(queue.totalByClassification()).bigDecimalCompare().isEqualTo(Pair(36.bd(), 24.bd()))
            }

            @Test
            fun `exactly one block removes it entirely`() {
                val queue = CashQueue()
                queue.add(100.bd(), "0.75".bd())

                assertThat(queue.poll(100.bd())).bigDecimalCompare().isEqualTo(Pair(75.bd(), 25.bd()))
                assertThat(queue.totalByClassification()).bigDecimalCompare()
                    .isEqualTo(Pair(BigDecimal.ZERO, BigDecimal.ZERO))
            }

            @Test
            fun `more than available drains queue and returns only what existed`() {
                val queue = CashQueue()
                queue.add(80.bd(), "0.5".bd())

                assertThat(queue.poll(200.bd())).bigDecimalCompare().isEqualTo(Pair(40.bd(), 40.bd()))
                assertThat(queue.totalByClassification()).bigDecimalCompare()
                    .isEqualTo(Pair(BigDecimal.ZERO, BigDecimal.ZERO))
            }
        }

        @Nested
        inner class SpanningMultipleBlocks {

            @Test
            fun `spanning two blocks with same ratio`() {
                val queue = CashQueue()
                queue.add(50.bd(), "0.4".bd())
                queue.add(100.bd(), "0.4".bd())

                // 50 from first block + 50 from second, both at 40% PM
                assertThat(queue.poll(100.bd())).bigDecimalCompare().isEqualTo(Pair(40.bd(), 60.bd()))
                // 50 remaining in second block still at 40%
                assertThat(queue.totalByClassification()).bigDecimalCompare().isEqualTo(Pair(20.bd(), 30.bd()))
            }

            @Test
            fun `spanning two blocks with different ratios splits each correctly`() {
                val queue = CashQueue()
                queue.add(60.bd(), BigDecimal.ONE)   // 60 PM, 0 M
                queue.add(100.bd(), BigDecimal.ZERO) // 0 PM, 100 M

                // consumes all 60 from first block (60 PM), then 40 from second (0 PM, 40 M)
                assertThat(queue.poll(100.bd())).bigDecimalCompare().isEqualTo(Pair(60.bd(), 40.bd()))
                // 60 remaining in second block, all marital
                assertThat(queue.totalByClassification()).bigDecimalCompare()
                    .isEqualTo(Pair(BigDecimal.ZERO, 60.bd()))
            }

            @Test
            fun `poll exactly the first block leaves the second intact`() {
                val queue = CashQueue()
                queue.add(50.bd(), BigDecimal.ONE)
                queue.add(80.bd(), "0.25".bd())

                assertThat(queue.poll(50.bd())).bigDecimalCompare()
                    .isEqualTo(Pair(50.bd(), BigDecimal.ZERO))
                assertThat(queue.totalByClassification()).bigDecimalCompare().isEqualTo(Pair(20.bd(), 60.bd()))
            }

            @Test
            fun `totalByClassification reflects state after multiple polls`() {
                val queue = CashQueue()
                queue.add(100.bd(), BigDecimal.ONE)  // 100 PM, 0 M
                queue.add(200.bd(), BigDecimal.ZERO) // 0 PM, 200 M
                queue.add(60.bd(), "0.5".bd())       // 30 PM, 30 M

                queue.poll(120.bd()) // consumes all 100 PM block + 20 M from next block
                // remaining: 180 M + 60 at 50%

                assertThat(queue.totalByClassification()).bigDecimalCompare().isEqualTo(Pair(30.bd(), 210.bd()))
            }
        }

        @Nested
        inner class FifoOrdering {

            @Test
            fun `pre-marital block added first is consumed before marital block`() {
                val queue = CashQueue()
                queue.add(100.bd(), BigDecimal.ONE)  // PM first
                queue.add(100.bd(), BigDecimal.ZERO) // M second

                assertThat(queue.poll(100.bd())).bigDecimalCompare()
                    .isEqualTo(Pair(100.bd(), BigDecimal.ZERO))
                assertThat(queue.totalByClassification()).bigDecimalCompare()
                    .isEqualTo(Pair(BigDecimal.ZERO, 100.bd()))
            }

            @Test
            fun `marital block added first is consumed before pre-marital block`() {
                val queue = CashQueue()
                queue.add(100.bd(), BigDecimal.ZERO) // M first
                queue.add(100.bd(), BigDecimal.ONE)  // PM second

                assertThat(queue.poll(100.bd())).bigDecimalCompare()
                    .isEqualTo(Pair(BigDecimal.ZERO, 100.bd()))
                assertThat(queue.totalByClassification()).bigDecimalCompare()
                    .isEqualTo(Pair(100.bd(), BigDecimal.ZERO))
            }
        }
    }
}