package com.xiaomanjun.sleepdownschedule.feature.importing

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiguangWarehouseProtocolV2Test {
    @Test
    fun bundledOfficialIndexUsesProtocolV2AndResolvesAdapters() {
        val index = sequenceOf(
            File("src/main/assets/shiguang_warehouse-main/school_index.pb"),
            File("app/src/main/assets/shiguang_warehouse-main/school_index.pb")
        ).first(File::isFile)

        val adapters = ShiguangWarehouse.parseProtocolV2Index(index.readBytes())

        assertTrue(adapters.size > 50)
        assertTrue(adapters.all { it.school.id.isNotBlank() && it.adapterId.isNotBlank() })
        assertEquals(
            "BACHELOR_AND_ASSOCIATE",
            adapters.first { it.school.id == "BUPT" }.category
        )
    }
}
