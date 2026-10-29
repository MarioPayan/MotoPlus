package com.kaze.motoplus.data

import android.content.Context
import androidx.core.util.AtomicFile
import com.kaze.motoplus.domain.Category
import com.kaze.motoplus.domain.Garage
import com.kaze.motoplus.domain.GarageCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.text.Normalizer
import java.util.Locale

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
private val catalogMarks = Regex("\\p{M}")
private val catalogSymbols = Regex("[^a-z0-9]")
private fun fold(s: String) = Normalizer.normalize(s, Normalizer.Form.NFKC)
    .let { Normalizer.normalize(it, Normalizer.Form.NFD) }.replace(catalogMarks, "").lowercase(Locale.ROOT)
private fun compact(s: String) = fold(s).replace(catalogSymbols, "")
private val catalogParts = Regex("[a-z]+|[0-9]+")
private fun modelKey(s: String) = catalogParts.findAll(fold(s)).map { it.value }.sorted().joinToString("|")

/** The whole garage in one JSON file, written atomically. */
// ponytail: single file, fine for a handful of bikes; move to Room when we need queries or sync
class GarageStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "garage.json"))

    fun load(): Garage = try {
        GarageCodec.decode(file.readFully().decodeToString())
    } catch (e: java.io.FileNotFoundException) {
        Garage()
    }

    fun save(g: Garage) {
        val out = file.startWrite()
        try {
            out.write(GarageCodec.encode(g).encodeToByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            throw e
        }
    }
}

@Serializable
data class CatalogModel(
    val n: String, val y: List<Int> = emptyList(), val c: Category = Category.naked,
    val a: List<String> = emptyList(), val s: List<String> = emptyList(),
)

@Serializable
data class CatalogBrand(val n: String, val models: List<CatalogModel>)

@Serializable
data class Catalog(val brands: List<CatalogBrand>) {
    data class Hit(val brand: String, val model: CatalogModel)

    private data class Entry(
        val hit: Hit, val fields: List<String>, val name: String, val key: String, val brand: String, val sortName: String,
    )
    private val all by lazy {
        brands.flatMap { b -> b.models.map { m ->
            Entry(Hit(b.n, m), (listOf(b.n, m.n) + m.a).map(::compact), compact(m.n), modelKey(m.n), fold(b.n), fold(m.n))
        } }
    }

    /** Every word must match a brand, model or alias; exact and prefix names come first. */
    fun search(q: String, limit: Int = 40): List<Hit> {
        val words = fold(q).split(Regex("\\s+")).map(::compact).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        fun rank(e: Entry): Int {
            val modelQuery = words.filterNot { it in e.fields.first() }.joinToString("")
            return when {
                modelQuery.isEmpty() -> 2
                e.key == modelKey(modelQuery) -> 0
                e.name.startsWith(modelQuery) -> 1
                else -> 2
            }
        }
        return all.asSequence()
            .filter { e -> words.all { word -> e.fields.any { word in it } } }
            .map { it to rank(it) }
            .sortedWith(compareBy<Pair<Entry, Int>> { it.second }.thenByDescending { it.first.hit.model.s.size }
                .thenBy { it.first.brand }.thenBy { it.first.sortName })
            .take(limit.coerceAtLeast(0)).map { it.first.hit }.toList()
    }

    companion object {
        fun load(context: Context): Catalog =
            json.decodeFromString(context.assets.open("catalog.json").use { it.readBytes().decodeToString() })
    }
}

@Serializable
data class DrawingItem(val z: String? = null, val k: String, val d: String, val w: Float? = null, val dash: List<Float>? = null, val eo: Boolean = false)

@Serializable
data class Drawing(val viewBox: List<Float>, val zones: List<String>, val items: List<DrawingItem>) {
    companion object {
        fun load(context: Context, name: String = "bike_naked.json"): Drawing =
            json.decodeFromString(context.assets.open(name).use { it.readBytes().decodeToString() })
    }
}
