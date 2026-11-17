package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.FeedEntity
import com.example.data.parser.FeedExportFormat
import com.example.data.parser.OpmlParser
import com.example.data.parser.RssParser
import com.example.ui.theme.AccentPalette
import com.example.ui.theme.AppSettings
import com.example.ui.theme.SettingsStorage
import com.example.ui.theme.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("CleanFeed", appName)
    }

    @Test
    fun testCleanHtmlStripsTagsAndEntities() {
        val rawHtml = "<p>Welcome to <b>CleanFeed</b> &amp; enjoy the <i>news</i>!</p>"
        val cleaned = RssParser.cleanHtml(rawHtml)
        assertEquals("Welcome to CleanFeed & enjoy the news!", cleaned)
    }

    @Test
    fun testOpmlExportAndImport() {
        val feeds = listOf(
            FeedEntity(id = 1L, name = "BBC News", url = "https://feeds.bbci.co.uk/news/rss.xml", category = "News"),
            FeedEntity(id = 2L, name = "The Verge", url = "https://www.theverge.com/rss/index.xml", category = "Tech")
        )
        val opmlString = OpmlParser.exportFeeds(feeds, FeedExportFormat.OPML)
        assertTrue(opmlString.contains("<opml version=\"2.0\">"))
        assertTrue(opmlString.contains("BBC News"))

        val imported = OpmlParser.parseOpml(opmlString)
        assertEquals(2, imported.size)
        assertEquals("BBC News", imported[0].name)
        assertEquals("https://feeds.bbci.co.uk/news/rss.xml", imported[0].url)
        assertEquals("News", imported[0].category)
    }

    @Test
    fun testJsonExportAndImport() {
        val feeds = listOf(
            FeedEntity(id = 1L, name = "NASA", url = "https://www.nasa.gov/feed", category = "Science")
        )
        val jsonString = OpmlParser.exportFeeds(feeds, FeedExportFormat.JSON)
        assertTrue(jsonString.contains("NASA"))

        val imported = OpmlParser.parseJson(jsonString)
        assertEquals(1, imported.size)
        assertEquals("NASA", imported[0].name)
        assertEquals("https://www.nasa.gov/feed", imported[0].url)
        assertEquals("Science", imported[0].category)
    }

    @Test
    fun testXmlExportAndImport() {
        val feeds = listOf(
            FeedEntity(id = 1L, name = "Ars Technica", url = "https://arstechnica.com/feed", category = "Tech")
        )
        val xmlString = OpmlParser.exportFeeds(feeds, FeedExportFormat.XML)
        assertTrue(xmlString.contains("<feeds>"))

        val imported = OpmlParser.parseXml(xmlString)
        assertEquals(1, imported.size)
        assertEquals("Ars Technica", imported[0].name)
    }

    @Test
    fun testSettingsStorageSaveAndLoad() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val customSettings = AppSettings(
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            accentPalette = AccentPalette.BLUE,
            openInAppBrowser = true,
            showUnreadOnly = true,
            pullToRefreshEnabled = true
        )
        SettingsStorage.saveSettings(context, customSettings)
        val loaded = SettingsStorage.loadSettings(context)
        assertEquals(ThemeMode.DARK, loaded.themeMode)
        assertEquals(false, loaded.dynamicColor)
        assertEquals(AccentPalette.BLUE, loaded.accentPalette)
        assertEquals(true, loaded.openInAppBrowser)
        assertEquals(true, loaded.showUnreadOnly)
    }

    @Test
    fun testFeedEntityCopyForEditing() {
        val original = FeedEntity(
            id = 42L,
            name = "Old Title",
            url = "https://old.example.com/rss",
            category = "Old Category"
        )
        val edited = original.copy(
            name = "New Title",
            url = "https://new.example.com/rss",
            category = "New Category"
        )
        assertEquals(42L, edited.id)
        assertEquals("New Title", edited.name)
        assertEquals("https://new.example.com/rss", edited.url)
        assertEquals("New Category", edited.category)
    }

    @Test
    fun testNepaliPortalsCatalogUniqueAndCategorized() {
        val portals = com.example.data.repository.NepaliPortalsCatalog.portals
        assertTrue(portals.size >= 30)

        val uniqueNormalizedUrls = portals.map {
            com.example.data.repository.NepaliPortalsCatalog.normalizeUrlKey(it.url)
        }.toSet()
        assertEquals(portals.size, uniqueNormalizedUrls.size)

        val categories = portals.map { it.category }.toSet()
        assertTrue(categories.contains("National News"))
        assertTrue(categories.contains("English News"))
        assertTrue(categories.contains("Business & Economy"))
        assertTrue(categories.contains("Technology"))
        assertTrue(categories.contains("Sports"))
        assertTrue(categories.contains("Health"))
        assertTrue(categories.contains("Entertainment"))
    }

    @Test
    fun testNepaliPortalsCategoryAndFeedSorting() {
        val unsortedCats = listOf("Technology", "Entertainment", "Custom", "National News", "Sports")
        val sortedCats = com.example.data.repository.NepaliPortalsCatalog.sortCategories(unsortedCats)
        assertEquals(
            listOf("National News", "Technology", "Sports", "Entertainment", "Custom"),
            sortedCats
        )

        val unsortedFeeds = listOf(
            FeedEntity(name = "Techpana", url = "https://techpana.com/feed/", category = "Technology"),
            FeedEntity(name = "Setopati", url = "https://www.setopati.com/feed", category = "National News"),
            FeedEntity(name = "OnlineKhabar", url = "https://www.onlinekhabar.com/feed", category = "National News"),
            FeedEntity(name = "Bizmandu", url = "https://bizmandu.com/feed/", category = "Business & Economy")
        )
        val sortedFeeds = com.example.data.repository.NepaliPortalsCatalog.sortFeeds(unsortedFeeds)
        assertEquals(
            listOf("OnlineKhabar", "Setopati", "Bizmandu", "Techpana"),
            sortedFeeds.map { it.name }
        )
    }
}
