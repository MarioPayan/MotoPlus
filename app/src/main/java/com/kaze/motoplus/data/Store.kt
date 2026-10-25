package com.kaze.motoplus.data

import android.content.Context
import androidx.core.util.AtomicFile
import com.kaze.motoplus.domain.Category
import com.kaze.motoplus.domain.Garage
import com.kaze.motoplus.domain.GarageCodec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
data class CatalogModel(val n: String, val y: List<Int> = emptyList(), val c: Category = Category.naked)

@Serializable
data class CatalogBrand(val n: String, val models: List<CatalogModel>)

@Serializable
data class Catalog(val brands: List<CatalogBrand>) {
    data class Hit(val brand: String, val model: CatalogModel)

    private val all by lazy { brands.flatMap { b -> b.models.map { Hit(b.n, it) } } }

    /** Every word of the query must appear in "brand model"; newest models first. */
    fun search(q: String, limit: Int = 40): List<Hit> {
        val words = q.lowercase().split(' ').filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()
        return all.asSequence()
            .filter { h -> val s = "${h.brand} ${h.model.n}".lowercase(); words.all { it in s } }
            .sortedByDescending { it.model.y.maxOrNull() ?: 0 }
            .take(limit).toList()
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
