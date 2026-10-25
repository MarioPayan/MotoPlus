package com.kaze.motoplus.domain

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A backup is one zip: garage.json plus every photo the garage points to, stored under files/ with
 * paths relative to the app's files folder. Restoring on another phone rebases them onto its folder.
 */
object BackupArchive {
    const val GARAGE = "garage.json"

    /** Photo paths the garage refers to (invoices and papers). */
    fun photos(g: Garage): Set<String> =
        (g.bikes.flatMap { b -> b.history.mapNotNull { it.photo } } + g.documents.flatMap { it.photos }).toSet()

    /** [files] maps a photo path (as in the garage) to its bytes; [root] is the files folder those paths live in. */
    fun pack(g: Garage, root: String, files: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(GARAGE)); zip.write(GarageCodec.encode(rebase(g, root, "")).encodeToByteArray()); zip.closeEntry()
            for ((path, bytes) in files) {
                zip.putNextEntry(ZipEntry("files/" + path.removePrefix(root).trimStart('/'))); zip.write(bytes); zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    class Unpacked(val garage: Garage, val files: Map<String, ByteArray>)

    /** Returns the garage with photo paths under [root], and the photo files to write there. */
    fun unpack(zip: ByteArray, root: String): Unpacked {
        var garage: Garage? = null
        val files = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val bytes = z.readBytes()
                when {
                    e.name == GARAGE -> garage = GarageCodec.decode(bytes.decodeToString())
                    // Never write outside the files folder, whatever the zip says.
                    e.name.startsWith("files/") && ".." !in e.name -> files["$root/${e.name.removePrefix("files/")}"] = bytes
                }
            }
        }
        return Unpacked(rebase(garage ?: error("No garage.json in backup"), "", root), files)
    }

    /** Moves every photo path from under [from] to under [to]. */
    fun rebase(g: Garage, from: String, to: String): Garage {
        fun move(p: String) = to.trimEnd('/').let { base -> if (base.isEmpty()) p.removePrefix(from).trimStart('/') else "$base/" + p.removePrefix(from).trimStart('/') }
        return g.copy(
            bikes = g.bikes.map { b -> b.copy(history = b.history.map { r -> r.copy(photo = r.photo?.let(::move)) }) },
            documents = g.documents.map { d -> d.copy(photos = d.photos.map(::move)) },
        )
    }
}

/** Service history and fill-ups as CSV, for a spreadsheet or the workshop. */
fun historyCsv(g: Garage): String = buildString {
    appendLine("moto,fecha,km,tipo,concepto,coste,litros,notas")
    fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
    for (b in g.bikes) {
        val names = b.parts.associate { it.id to it.name }
        b.history.sortedBy { it.date }.forEach { r ->
            appendLine(listOf(q(b.title), r.date, r.km, "mantenimiento", q(names[r.partId] ?: r.partId), r.cost ?: "", "", q(r.notes)).joinToString(","))
        }
        b.fuel.sortedBy { it.date }.forEach { f ->
            appendLine(listOf(q(b.title), f.date, f.km, "repostaje", q(if (f.full) "tanque lleno" else "parcial"), f.total ?: "", f.liters, "\"\"").joinToString(","))
        }
    }
}
