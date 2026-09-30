package com.goldberg.law.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MemoryStoreListingTest {

    private fun listing(vararg paths: String) =
        MemoryStoreListing.of(MemoryConsolidation.SPLITTING, paths.map { StoredMemory(it, 10, 1L) })

    @Test
    fun `files are grouped into a folder per bank id, sorted`() {
        val listing = listing("/wells_fargo/main.json", "/chase_cc/sesn_2.json", "/chase_cc/main.json", "/chase_cc/sesn_1.json")

        assertThat(listing.folders.map { it.bankId }).containsExactly("chase_cc", "wells_fargo")
        assertThat(listing.folder("chase_cc")!!.files.map { it.name }).containsExactly("main.json", "sesn_1.json", "sesn_2.json")
    }

    @Test
    fun `a folder reports its main file and how many session files wait to be consolidated`() {
        val listing = listing("/chase_cc/main.json", "/chase_cc/sesn_1.json", "/chase_cc/sesn_2.json", "/new_bank/sesn_3.json")

        val chase = listing.folder("chase_cc")!!
        assertThat(chase.hasMain).isTrue()
        assertThat(chase.sessionFileCount).isEqualTo(2)
        val newBank = listing.folder("new_bank")!!
        assertThat(newBank.hasMain).isFalse()
        assertThat(newBank.sessionFileCount).isEqualTo(1)
    }

    @Test
    fun `only sesn_ json files directly in the folder count as session files`() {
        val folder = listing("/chase_cc/notes.json", "/chase_cc/sesn_1.md", "/chase_cc/old/sesn_2.json").folder("chase_cc")!!

        assertThat(folder.sessionFileCount).isZero()
        assertThat(folder.files.map { it.name }).containsExactly("notes.json", "old/sesn_2.json", "sesn_1.md")
    }

    @Test
    fun `files at the store root are listed apart from the bank folders`() {
        val listing = listing("/truist.json", "/chase_cc/main.json")

        assertThat(listing.looseFiles.map { it.name }).containsExactly("truist.json")
        assertThat(listing.folders.map { it.bankId }).containsExactly("chase_cc")
    }

    @Test
    fun `an empty store lists nothing`() {
        val listing = listing()

        assertThat(listing.folders).isEmpty()
        assertThat(listing.looseFiles).isEmpty()
        assertThat(listing.folder("chase_cc")).isNull()
    }
}
