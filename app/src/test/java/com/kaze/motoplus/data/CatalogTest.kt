package com.kaze.motoplus.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale
import java.io.File

class CatalogTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val catalog = Catalog(listOf(
        CatalogBrand("AKT", listOf(CatalogModel("NKD 125", a = listOf("AK125NKD EIII")))),
        CatalogBrand("Yamaha", listOf(CatalogModel("NMAX 155", a = listOf("GPD155-A (NMAX155)")))),
        CatalogBrand("KTM", listOf(CatalogModel("390 Duke"), CatalogModel("Duke 200"))),
        CatalogBrand("Bultacó", listOf(CatalogModel("Métralla-250"))),
    ))

    @Test fun wordsMatchInAnyOrder() {
        assertEquals("390 Duke", catalog.search("duke 390").single().model.n)
        assertEquals(catalog.search("AKT nkd 125"), catalog.search("125 akt nkd"))
        assertTrue(catalog.search("AKT duke").isEmpty())
    }

    @Test fun registrationAliasesAndJoinedNamesMatch() {
        assertEquals("NKD 125", catalog.search("ak125nkd").single().model.n)
        assertEquals("NMAX 155", catalog.search("GPD155A").single().model.n)
        assertEquals("NMAX 155", catalog.search("nmax155").single().model.n)
        assertEquals("NMAX 155", catalog.search("155 yamaha gpd").single().model.n)
    }

    @Test fun accentsHyphensAndCaseAreIgnored() {
        assertEquals("Métralla-250", catalog.search("BULTACO metralla250").single().model.n)
        assertEquals("Métralla-250", catalog.search("métralla 250").single().model.n)
        assertEquals("NMAX 155", catalog.search("\tNMAX-155\n").single().model.n)
    }

    @Test fun normalizationDoesNotDependOnDeviceLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"))
            val c = Catalog(listOf(CatalogBrand("Hero", listOf(CatalogModel("IGNITOR 125")))))
            assertEquals(1, c.search("ignitor").size)
        } finally { Locale.setDefault(before) }
    }

    @Test fun brandOnlyQueryReturnsItsModels() {
        assertEquals(listOf("390 Duke", "Duke 200"), catalog.search("ktm").map { it.model.n })
        assertTrue(catalog.search(" \t -- ").isEmpty())
        assertEquals(1, catalog.search("ktm", 1).size)
        assertTrue(catalog.search("ktm", 0).isEmpty())
    }

    @Test fun exactThenPrefixThenSourceCountThenAlphabetical() {
        val c = Catalog(listOf(CatalogBrand("KTM", listOf(
            CatalogModel("Super Duke", s = listOf("a", "b", "c")),
            CatalogModel("Duke 390", s = listOf("a")),
            CatalogModel("Duke"),
            CatalogModel("Duke 200", s = listOf("a")),
        ))))
        assertEquals(listOf("Duke", "Duke 200", "Duke 390", "Super Duke"), c.search("KTM duke").map { it.model.n })
        assertEquals("Super Duke", c.search("KTM").first().model.n)
    }

    @Test fun exactNameRanksFirstInEitherWordOrder() {
        val c = Catalog(listOf(CatalogBrand("KTM", listOf(
            CatalogModel("390 Duke"), CatalogModel("390 Duke GEN3", s = listOf("a", "b", "c")),
        ))))
        assertEquals("390 Duke", c.search("KTM duke 390").first().model.n)
    }

    @Test fun builtAssetLoadsAliasesWithTheGeneratedSerializer() {
        val asset = listOf(File("src/main/assets/catalog.json"), File("app/src/main/assets/catalog.json"))
            .first { it.exists() }.readText()
        val c = json.decodeFromString<Catalog>(asset)
        assertEquals("NKD 125", c.search("AKT ak125nkd").first().model.n)
        assertTrue(c.search("yamaha nmax155").isNotEmpty())
        assertEquals("TNT 150i", c.search("benelli tnt150i").first().model.n)
        assertEquals("TNT 135", c.search("benelli tnt135").first().model.n)
    }

    @Test fun serializableModelsSupportOldAndNewCatalogs() {
        val old = json.decodeFromString<Catalog>("""{"brands":[{"n":"AKT","models":[{"n":"NKD 125"}]}]}""")
        assertTrue(old.brands.single().models.single().a.isEmpty())
        val copy = json.decodeFromString<Catalog>(json.encodeToString(catalog))
        assertEquals("NKD 125", copy.search("ak125nkd").single().model.n)
    }
}
