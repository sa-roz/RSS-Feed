package com.example.data.parser

import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.util.regex.Pattern

data class DiscoveredFeed(
    val feedUrl: String,
    val title: String,
    val siteUrl: String,
    val channel: ParsedChannel
)

class RssDiscovery(private val httpClient: OkHttpClient) {

    private val linkTagPattern = Pattern.compile(
        "<link[^>]+rel=[\"']alternate[\"'][^>]*>",
        Pattern.CASE_INSENSITIVE
    )
    private val linkTypePattern = Pattern.compile(
        "<link[^>]+type=[\"'](application/rss\\+xml|application/atom\\+xml|text/xml|application/xml)[\"'][^>]*>",
        Pattern.CASE_INSENSITIVE
    )
    private val hrefPattern = Pattern.compile("href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)
    private val titleTagPattern = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
    private val ogSiteNamePattern = Pattern.compile("<meta[^>]+property=[\"']og:site_name[\"'][^>]+content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE)

    private val commonFeedPaths = listOf(
        "/feed",
        "/feed/",
        "/rss",
        "/rss.xml",
        "/atom.xml",
        "/index.xml",
        "/?feed=rss2",
        "/feeds/posts/default"
    )

    fun discoverFeed(inputUrl: String): DiscoveredFeed {
        var normalized = inputUrl.trim()
        if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
            normalized = "https://$normalized"
        }

        // 1. Try directly fetching as RSS/Atom feed first
        try {
            val directChannel = fetchAndParse(normalized)
            if (directChannel != null) {
                return DiscoveredFeed(
                    feedUrl = normalized,
                    title = directChannel.title.ifBlank { extractDomainName(normalized) },
                    siteUrl = directChannel.siteUrl.ifBlank { normalized },
                    channel = directChannel
                )
            }
        } catch (_: Exception) {
            // Not a direct feed, continue to HTML discovery
        }

        // 2. Fetch the webpage HTML
        val request = Request.Builder().url(normalized).build()
        val response = httpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("Failed to connect to $normalized (HTTP ${response.code})")
        }

        val finalUrl = response.request.url.toString()
        val bodyText = response.body?.string() ?: throw Exception("Empty response from $normalized")

        // Extract site title from HTML
        var pageTitle = ""
        val ogMatcher = ogSiteNamePattern.matcher(bodyText)
        if (ogMatcher.find()) {
            pageTitle = ogMatcher.group(1)?.trim() ?: ""
        }
        if (pageTitle.isBlank()) {
            val titleMatcher = titleTagPattern.matcher(bodyText)
            if (titleMatcher.find()) {
                pageTitle = RssParser.cleanHtml(titleMatcher.group(1) ?: "").trim()
            }
        }
        if (pageTitle.isBlank()) {
            pageTitle = extractDomainName(finalUrl)
        }

        // 3. Search HTML for RSS / Atom <link> tags
        val candidateUrls = mutableListOf<String>()

        fun extractLinks(matcher: java.util.regex.Matcher) {
            while (matcher.find()) {
                val tag = matcher.group(0)
                val hrefMatcher = hrefPattern.matcher(tag)
                if (hrefMatcher.find()) {
                    val rawHref = hrefMatcher.group(1)?.trim() ?: ""
                    if (rawHref.isNotBlank()) {
                        try {
                            val resolved = URI.create(finalUrl).resolve(rawHref).toString()
                            if (!candidateUrls.contains(resolved)) {
                                candidateUrls.add(resolved)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        }

        extractLinks(linkTagPattern.matcher(bodyText))
        extractLinks(linkTypePattern.matcher(bodyText))

        // 4. Test discovered <link> candidate URLs
        for (candidate in candidateUrls) {
            try {
                val parsed = fetchAndParse(candidate)
                if (parsed != null) {
                    val finalTitle = parsed.title.ifBlank { pageTitle }
                    return DiscoveredFeed(
                        feedUrl = candidate,
                        title = finalTitle,
                        siteUrl = finalUrl,
                        channel = parsed
                    )
                }
            } catch (_: Exception) {
            }
        }

        // 5. Fallback: probe common feed endpoints
        val baseUri = try {
            val uri = URI.create(finalUrl)
            "${uri.scheme}://${uri.host}${if (uri.port != -1) ":${uri.port}" else ""}"
        } catch (_: Exception) {
            finalUrl.trimEnd('/')
        }

        for (path in commonFeedPaths) {
            val candidate = baseUri.trimEnd('/') + path
            try {
                val parsed = fetchAndParse(candidate)
                if (parsed != null) {
                    val finalTitle = parsed.title.ifBlank { pageTitle }
                    return DiscoveredFeed(
                        feedUrl = candidate,
                        title = finalTitle,
                        siteUrl = finalUrl,
                        channel = parsed
                    )
                }
            } catch (_: Exception) {
            }
        }

        throw Exception("Could not find an RSS or Atom feed for '$inputUrl'. Please check the web address or provide the direct feed URL.")
    }

    private fun fetchAndParse(url: String): ParsedChannel? {
        val request = Request.Builder().url(url).build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body ?: return null
            val contentType = response.header("Content-Type")?.lowercase() ?: ""
            // Allow XML content or parse stream
            return try {
                val channel = RssParser.parse(body.byteStream())
                if (channel.items.isNotEmpty() || channel.title.isNotBlank()) {
                    channel
                } else null
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun extractDomainName(url: String): String {
        return try {
            val uri = URI.create(url)
            val host = uri.host ?: url
            host.removePrefix("www.")
                .substringBefore('.')
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        } catch (_: Exception) {
            "Feed"
        }
    }
}
