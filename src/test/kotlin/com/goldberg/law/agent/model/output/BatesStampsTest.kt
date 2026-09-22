package com.goldberg.law.agent.model.output

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BatesStampsTest {

    @Test
    fun `a sequence fills in every page between its endpoints, keeping the stamp's width`() {
        val report = BatesReport(sequences = listOf(BatesSequence(1, 4, "MH-000008", "MH-000011")))

        assertThat(report.toPageStamps()).containsExactlyEntriesOf(mapOf(
            1 to "MH-000008",
            2 to "MH-000009",
            3 to "MH-000010",
            4 to "MH-000011",
        ))
    }

    @Test
    fun `non-sequenced pages are included and win over a sequence covering the same page`() {
        val report = BatesReport(
            sequences = listOf(BatesSequence(1, 3, "AG-001", "AG-003")),
            nonSequenced = mapOf(3 to "AG-999", 7 to "AG-007"),
        )

        assertThat(report.toPageStamps()).containsExactlyEntriesOf(mapOf(
            1 to "AG-001",
            2 to "AG-002",
            3 to "AG-999",
            7 to "AG-007",
        ))
    }

    @Test
    fun `a sequence that doesn't advance by one per page is trusted only at its endpoints`() {
        val report = BatesReport(sequences = listOf(BatesSequence(1, 3, "AG-001", "AG-050")))

        assertThat(report.toPageStamps()).containsExactlyEntriesOf(mapOf(1 to "AG-001", 3 to "AG-050"))
    }

    @Test
    fun `a stamp with no number is trusted only at its endpoints`() {
        val report = BatesReport(sequences = listOf(BatesSequence(1, 3, "EXHIBIT-A", "EXHIBIT-C")))

        assertThat(report.toPageStamps()).containsExactlyEntriesOf(mapOf(1 to "EXHIBIT-A", 3 to "EXHIBIT-C"))
    }

    @Test
    fun `a single-page sequence is just that page`() {
        val report = BatesReport(sequences = listOf(BatesSequence(5, 5, "AG-005", "AG-005")))

        assertThat(report.toPageStamps()).containsExactlyEntriesOf(mapOf(5 to "AG-005"))
    }

    @Test
    fun `a report with nothing in it produces no stamps`() {
        assertThat(BatesReport().toPageStamps()).isEmpty()
    }

    @Test
    fun `stamps come back in page order`() {
        val report = BatesReport(
            sequences = listOf(BatesSequence(10, 11, "AG-010", "AG-011")),
            nonSequenced = mapOf(2 to "AG-002"),
        )

        assertThat(report.toPageStamps().keys).containsExactly(2, 10, 11)
    }
}
