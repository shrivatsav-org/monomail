package com.shrivatsav.monomail

import com.shrivatsav.monomail.core.database.local.MIGRATION_2_3
import com.shrivatsav.monomail.core.database.local.MIGRATION_3_4
import com.shrivatsav.monomail.core.database.local.MIGRATION_4_5
import org.junit.Test
import org.junit.Assert.*

class MigrationTest {
    @Test
    fun allMigrationsAreRegistered() {
        val migrations = listOf(2 to 3, 3 to 4, 4 to 5)
        val objects = listOf(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
        for ((i, m) in migrations.withIndex()) {
            assertEquals(m.first, objects[i].startVersion)
            assertEquals(m.second, objects[i].endVersion)
        }
    }
}
