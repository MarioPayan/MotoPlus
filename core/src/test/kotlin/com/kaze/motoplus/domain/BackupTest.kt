package com.kaze.motoplus.domain

import org.junit.Assert.*
import org.junit.Test

class BackupTest {
    private val root = "/data/user/0/com.kaze.motoplus/files"
    private val g = Garage(
        bikes = listOf(Bike("b", "Yamaha", "MT-07", null, Category.naked, Drive.chain, Cooling.liquid, emptyList(), listOf(Reading("2026-10-25", 12000)),
            history = listOf(ServiceRecord("r", "chain_lube", "2026-10-25", 12000, 20000.0, "con \"grasa\"", "$root/invoices/a.jpg")),
            fuel = listOf(FuelEntry("f", "2026-10-25", 12000, 11.2, 182000.0)))),
        documents = listOf(Document("d", DocType.soat, "b", photos = listOf("$root/documents/s.jpg"))),
    )

    @Test fun roundTripsToAnotherPhone() {
        val zip = BackupArchive.pack(g, root, mapOf("$root/invoices/a.jpg" to byteArrayOf(1, 2), "$root/documents/s.jpg" to byteArrayOf(3)))
        val other = "/data/user/10/com.kaze.motoplus/files"
        val u = BackupArchive.unpack(zip, other)
        assertEquals("$other/invoices/a.jpg", u.garage.bikes[0].history[0].photo)
        assertEquals(listOf("$other/documents/s.jpg"), u.garage.documents[0].photos)
        assertArrayEquals(byteArrayOf(1, 2), u.files["$other/invoices/a.jpg"])
        assertEquals(setOf("$root/invoices/a.jpg", "$root/documents/s.jpg"), BackupArchive.photos(g))
    }

    @Test fun refusesPathsOutsideFiles() {
        val zip = java.io.ByteArrayOutputStream().also { o ->
            java.util.zip.ZipOutputStream(o).use { z ->
                z.putNextEntry(java.util.zip.ZipEntry("garage.json")); z.write(GarageCodec.encode(Garage()).toByteArray()); z.closeEntry()
                z.putNextEntry(java.util.zip.ZipEntry("files/../../evil")); z.write(byteArrayOf(9)); z.closeEntry()
            }
        }.toByteArray()
        assertTrue(BackupArchive.unpack(zip, root).files.isEmpty())
    }

    @Test fun csvEscapesQuotes() {
        val csv = historyCsv(g)
        assertTrue(csv.contains("\"Yamaha MT-07\",2026-10-25,12000,mantenimiento,\"chain_lube\",20000.0,,\"con \"\"grasa\"\"\""))
        assertTrue(csv.contains("repostaje,\"tanque lleno\",182000.0,11.2"))
    }
}
