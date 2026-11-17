package com.example.data.parser

import android.util.Xml
import com.example.data.local.FeedEntity
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class FeedImportItem(
    val name: String,
    val url: String,
    val category: String,
    val siteUrl: String? = null
)

enum class FeedExportFormat {
    OPML,
    XML,
    JSON
}

object OpmlParser {

    /**
     * Parses OPML, XML, or JSON automatically depending on content structure.
     */
    fun importFeeds(content: String): List<FeedImportItem> {
        val trimmed = content.trim()
        return when {
            trimmed.startsWith("[") || trimmed.startsWith("{") -> parseJson(trimmed)
            trimmed.contains("<opml", ignoreCase = true) -> parseOpml(trimmed)
            trimmed.contains("<feeds", ignoreCase = true) || trimmed.contains("<feed", ignoreCase = true) -> parseXml(trimmed)
            else -> {
                // Try OPML first, then XML, then JSON
                try {
                    parseOpml(trimmed)
                } catch (_: Exception) {
                    try {
                        parseXml(trimmed)
                    } catch (_: Exception) {
                        parseJson(trimmed)
                    }
                }
            }
        }
    }

    fun parseOpml(opmlContent: String): List<FeedImportItem> {
        val results = mutableListOf<FeedImportItem>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(opmlContent))

        var eventType = parser.eventType
        val categoryStack = mutableListOf<String>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true)) {
                        val xmlUrl = parser.getAttributeValue(null, "xmlUrl")
                            ?: parser.getAttributeValue(null, "xmlurl")
                            ?: parser.getAttributeValue(null, "url")
                        val title = parser.getAttributeValue(null, "title")
                            ?: parser.getAttributeValue(null, "text")
                            ?: "Feed"
                        val htmlUrl = parser.getAttributeValue(null, "htmlUrl")
                            ?: parser.getAttributeValue(null, "htmlurl")
                        val explicitCategory = parser.getAttributeValue(null, "category")

                        if (!xmlUrl.isNullOrBlank()) {
                            val category = when {
                                !explicitCategory.isNullOrBlank() -> explicitCategory
                                categoryStack.isNotEmpty() -> categoryStack.last()
                                else -> "General"
                            }
                            results.add(
                                FeedImportItem(
                                    name = title.trim(),
                                    url = xmlUrl.trim(),
                                    category = category.trim(),
                                    siteUrl = htmlUrl?.trim()
                                )
                            )
                        } else {
                            // This outline may be a category grouping
                            categoryStack.add(title.trim())
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name.equals("outline", ignoreCase = true) && categoryStack.isNotEmpty()) {
                        categoryStack.removeAt(categoryStack.size - 1)
                    }
                }
            }
            eventType = parser.next()
        }
        return results
    }

    fun parseXml(xmlContent: String): List<FeedImportItem> {
        val results = mutableListOf<FeedImportItem>()
        val parser = Xml.newPullParser()
        parser.setInput(StringReader(xmlContent))

        var eventType = parser.eventType
        var currentName = ""
        var currentUrl = ""
        var currentCategory = "General"
        var currentSiteUrl: String? = null
        var inFeed = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    val name = parser.name.lowercase(Locale.ROOT)
                    if (name == "feed" || name == "item") {
                        inFeed = true
                        currentName = ""
                        currentUrl = ""
                        currentCategory = "General"
                        currentSiteUrl = null
                    } else if (inFeed) {
                        when (name) {
                            "name", "title" -> currentName = readText(parser)
                            "url", "xmlurl", "link" -> currentUrl = readText(parser)
                            "category", "group" -> currentCategory = readText(parser)
                            "siteurl", "htmlurl" -> currentSiteUrl = readText(parser)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val name = parser.name.lowercase(Locale.ROOT)
                    if (name == "feed" || name == "item") {
                        if (currentUrl.isNotBlank()) {
                            results.add(
                                FeedImportItem(
                                    name = currentName.ifBlank { "Feed" },
                                    url = currentUrl.trim(),
                                    category = currentCategory.ifBlank { "General" },
                                    siteUrl = currentSiteUrl?.trim()
                                )
                            )
                        }
                        inFeed = false
                    }
                }
            }
            eventType = parser.next()
        }
        return results
    }

    fun parseJson(jsonContent: String): List<FeedImportItem> {
        val results = mutableListOf<FeedImportItem>()
        try {
            val array = if (jsonContent.trim().startsWith("[")) {
                JSONArray(jsonContent)
            } else {
                val obj = JSONObject(jsonContent)
                obj.optJSONArray("feeds") ?: JSONArray()
            }

            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val url = item.optString("url").ifBlank { item.optString("xmlUrl") }
                if (url.isNotBlank()) {
                    val name = item.optString("name").ifBlank { item.optString("title", "Feed") }
                    val category = item.optString("category", "General")
                    val siteUrl = item.optString("siteUrl", "").ifBlank { null }
                    results.add(
                        FeedImportItem(
                            name = name,
                            url = url,
                            category = category.ifBlank { "General" },
                            siteUrl = siteUrl
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return results
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result.trim()
    }

    fun exportFeeds(feeds: List<FeedEntity>, format: FeedExportFormat): String {
        return when (format) {
            FeedExportFormat.OPML -> exportToOpml(feeds)
            FeedExportFormat.XML -> exportToXml(feeds)
            FeedExportFormat.JSON -> exportToJson(feeds)
        }
    }

    fun exportToOpml(feeds: List<FeedEntity>): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US)
        val nowStr = sdf.format(Date())
        val grouped = feeds.groupBy { it.category.ifBlank { "General" } }

        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<opml version=\"2.0\">\n")
        sb.append("  <head>\n")
        sb.append("    <title>CleanFeed RSS Subscriptions</title>\n")
        sb.append("    <dateCreated>$nowStr</dateCreated>\n")
        sb.append("  </head>\n")
        sb.append("  <body>\n")

        for ((category, categoryFeeds) in grouped) {
            sb.append("    <outline text=\"${escapeXml(category)}\" title=\"${escapeXml(category)}\">\n")
            for (feed in categoryFeeds) {
                val siteAttr = if (!feed.siteUrl.isNullOrBlank()) " htmlUrl=\"${escapeXml(feed.siteUrl)}\"" else ""
                sb.append("      <outline type=\"rss\" text=\"${escapeXml(feed.name)}\" title=\"${escapeXml(feed.name)}\" xmlUrl=\"${escapeXml(feed.url)}\"$siteAttr category=\"${escapeXml(category)}\" />\n")
            }
            sb.append("    </outline>\n")
        }

        sb.append("  </body>\n")
        sb.append("</opml>\n")
        return sb.toString()
    }

    fun exportToXml(feeds: List<FeedEntity>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<feeds>\n")
        for (feed in feeds) {
            sb.append("  <feed>\n")
            sb.append("    <name>${escapeXml(feed.name)}</name>\n")
            sb.append("    <url>${escapeXml(feed.url)}</url>\n")
            sb.append("    <category>${escapeXml(feed.category)}</category>\n")
            if (!feed.siteUrl.isNullOrBlank()) {
                sb.append("    <siteUrl>${escapeXml(feed.siteUrl)}</siteUrl>\n")
            }
            sb.append("  </feed>\n")
        }
        sb.append("</feeds>\n")
        return sb.toString()
    }

    fun exportToJson(feeds: List<FeedEntity>): String {
        val array = JSONArray()
        for (feed in feeds) {
            val obj = JSONObject()
            obj.put("name", feed.name)
            obj.put("url", feed.url)
            obj.put("category", feed.category)
            if (!feed.siteUrl.isNullOrBlank()) {
                obj.put("siteUrl", feed.siteUrl)
            }
            array.put(obj)
        }
        return array.toString(2)
    }

    private fun escapeXml(input: String): String {
        return input.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
