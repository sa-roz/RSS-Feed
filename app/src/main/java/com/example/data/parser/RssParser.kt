package com.example.data.parser

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

data class ParsedChannel(
    val title: String,
    val description: String,
    val siteUrl: String,
    val items: List<ParsedArticle>
)

data class ParsedArticle(
    val title: String,
    val link: String,
    val description: String,
    val content: String?,
    val imageUrl: String?,
    val pubDate: Long,
    val pubDateFormatted: String
)

object RssParser {

    private val DATE_FORMATS = listOf(
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss Z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US),
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd", Locale.US)
    )

    private val IMG_REGEX = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    fun parse(inputStream: InputStream): ParsedChannel {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, null)

        var eventType = parser.eventType
        var isAtom = false

        var channelTitle = ""
        var channelDesc = ""
        var channelLink = ""
        val items = mutableListOf<ParsedArticle>()

        while (eventType != XmlPullParser.END_DOCUMENT) {
            val name = parser.name?.lowercase(Locale.ROOT) ?: ""
            if (eventType == XmlPullParser.START_TAG) {
                if (name == "feed") {
                    isAtom = true
                } else if (name == "channel" && !isAtom) {
                    // RSS Channel
                } else if (!isAtom && name == "item") {
                    val item = parseRssItem(parser)
                    if (item.link.isNotBlank() || item.title.isNotBlank()) {
                        items.add(item)
                    }
                } else if (isAtom && name == "entry") {
                    val item = parseAtomEntry(parser)
                    if (item.link.isNotBlank() || item.title.isNotBlank()) {
                        items.add(item)
                    }
                } else if (items.isEmpty()) {
                    // Top-level channel metadata
                    when (name) {
                        "title" -> if (channelTitle.isBlank()) channelTitle = readText(parser)
                        "description", "subtitle" -> if (channelDesc.isBlank()) channelDesc = readText(parser)
                        "link" -> {
                            if (channelLink.isBlank()) {
                                val href = parser.getAttributeValue(null, "href")
                                channelLink = if (!href.isNullOrBlank()) href else readText(parser)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return ParsedChannel(
            title = cleanHtml(channelTitle).ifBlank { "Feed" },
            description = cleanHtml(channelDesc),
            siteUrl = channelLink.trim(),
            items = items
        )
    }

    private fun parseRssItem(parser: XmlPullParser): ParsedArticle {
        var title = ""
        var link = ""
        var description = ""
        var content = ""
        var imageUrl: String? = null
        var pubDateStr = ""

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("item", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase(Locale.ROOT)
                when {
                    name == "title" -> title = readText(parser)
                    name == "link" -> link = readText(parser).trim()
                    name == "description" -> {
                        description = readText(parser)
                        if (imageUrl == null) {
                            imageUrl = extractImageUrlFromHtml(description)
                        }
                    }
                    name.endsWith("encoded") || name == "content" -> {
                        content = readText(parser)
                        if (imageUrl == null) {
                            imageUrl = extractImageUrlFromHtml(content)
                        }
                    }
                    name == "pubdate" -> pubDateStr = readText(parser).trim()
                    name == "enclosure" -> {
                        val type = parser.getAttributeValue(null, "type") ?: ""
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null && (type.startsWith("image/") || isImageUrl(url))) {
                            imageUrl = url
                        }
                    }
                    name.endsWith("content") || name.endsWith("thumbnail") -> {
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null && (isImageUrl(url) || parser.getAttributeValue(null, "medium") == "image")) {
                            imageUrl = url
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val parsedPubDate = parseDate(pubDateStr)
        val cleanSummary = cleanHtml(description.ifBlank { content })

        return ParsedArticle(
            title = cleanHtml(title).ifBlank { "Untitled Article" },
            link = link,
            description = cleanSummary,
            content = cleanHtml(content).ifBlank { cleanSummary },
            imageUrl = imageUrl,
            pubDate = parsedPubDate,
            pubDateFormatted = formatDate(parsedPubDate)
        )
    }

    private fun parseAtomEntry(parser: XmlPullParser): ParsedArticle {
        var title = ""
        var link = ""
        var summary = ""
        var content = ""
        var imageUrl: String? = null
        var dateStr = ""

        var eventType = parser.next()
        while (!(eventType == XmlPullParser.END_TAG && parser.name.equals("entry", ignoreCase = true))) {
            if (eventType == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase(Locale.ROOT)
                when {
                    name == "title" -> title = readText(parser)
                    name == "link" -> {
                        val href = parser.getAttributeValue(null, "href")
                        val rel = parser.getAttributeValue(null, "rel")
                        val type = parser.getAttributeValue(null, "type")
                        if (type != null && type.startsWith("image/") && href != null) {
                            imageUrl = href
                        } else if (href != null && (rel == null || rel == "alternate" || link.isBlank())) {
                            link = href.trim()
                        }
                    }
                    name == "summary" -> {
                        summary = readText(parser)
                        if (imageUrl == null) {
                            imageUrl = extractImageUrlFromHtml(summary)
                        }
                    }
                    name == "content" -> {
                        content = readText(parser)
                        if (imageUrl == null) {
                            imageUrl = extractImageUrlFromHtml(content)
                        }
                    }
                    name == "published" || (name == "updated" && dateStr.isBlank()) -> {
                        dateStr = readText(parser).trim()
                    }
                    name.endsWith("thumbnail") || name.endsWith("content") -> {
                        val url = parser.getAttributeValue(null, "url")
                        if (url != null && isImageUrl(url)) {
                            imageUrl = url
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        val parsedPubDate = parseDate(dateStr)
        val cleanSummary = cleanHtml(summary.ifBlank { content })

        return ParsedArticle(
            title = cleanHtml(title).ifBlank { "Untitled Article" },
            link = link,
            description = cleanSummary,
            content = cleanHtml(content).ifBlank { cleanSummary },
            imageUrl = imageUrl,
            pubDate = parsedPubDate,
            pubDateFormatted = formatDate(parsedPubDate)
        )
    }

    private fun readText(parser: XmlPullParser): String {
        var result = ""
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.text ?: ""
            parser.nextTag()
        }
        return result
    }

    fun cleanHtml(html: String): String {
        if (html.isBlank()) return ""
        var text = html
        // Remove scripts and style tags
        text = text.replace(Regex("<(script|style)[^>]*?>.*?</\\1>", RegexOption.DOT_MATCHES_ALL), " ")
        // Replace block-level tags with space so words don't glue together
        text = text.replace(Regex("(?i)<(p|br|div|h[1-6]|li|tr|blockquote)[^>]*?>"), " ")
        // Remove remaining inline tags without injecting spaces
        text = text.replace(Regex("<[^>]+>"), "")
        // Unescape HTML entities
        text = text.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#8217;", "'")
            .replace("&#8216;", "'")
            .replace("&#8220;", "\"")
            .replace("&#8221;", "\"")
            .replace("&#8212;", "—")
            .replace("&#8211;", "–")
            .replace(Regex("&[a-zA-Z0-9#]+;"), " ")
        // Normalize whitespace
        text = text.replace(Regex("\\s+"), " ").trim()
        return text
    }

    private fun extractImageUrlFromHtml(html: String): String? {
        if (html.isBlank()) return null
        val matcher = IMG_REGEX.matcher(html)
        if (matcher.find()) {
            val url = matcher.group(1)
            if (!url.isNullOrBlank() && !url.startsWith("data:") && isImageUrl(url)) {
                return url.trim()
            }
        }
        return null
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") ||
                lower.endsWith(".png") || lower.endsWith(".webp") ||
                lower.endsWith(".gif") || lower.contains("/image") ||
                lower.contains("format=jpg") || lower.contains("format=png")
    }

    private fun parseDate(dateStr: String): Long {
        if (dateStr.isBlank()) return System.currentTimeMillis()
        for (format in DATE_FORMATS) {
            try {
                val parsed = format.parse(dateStr)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return System.currentTimeMillis()
    }

    fun formatDate(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24

        return when {
            diff < 0 -> "Just now"
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days == 1L -> "Yesterday"
            days < 7 -> "${days}d ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                sdf.format(Date(timestamp))
            }
        }
    }
}
