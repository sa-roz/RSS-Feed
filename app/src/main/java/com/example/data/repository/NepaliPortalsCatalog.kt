package com.example.data.repository

import com.example.data.local.FeedEntity

object NepaliPortalsCatalog {

    val categoryOrder: List<String> = listOf(
        "National News",
        "English News",
        "Business & Economy",
        "Technology",
        "Sports",
        "Health",
        "Entertainment",
        "General"
    )

    fun categoryOrderIndex(category: String): Int {
        val idx = categoryOrder.indexOfFirst { it.equals(category.trim(), ignoreCase = true) }
        return if (idx >= 0) idx else Int.MAX_VALUE
    }

    fun sortCategories(categories: List<String>): List<String> {
        return categories
            .distinctBy { it.trim().lowercase() }
            .sortedWith(
                compareBy<String> { categoryOrderIndex(it) }
                    .thenBy { it.lowercase() }
            )
    }

    fun sortFeeds(feeds: List<FeedEntity>): List<FeedEntity> {
        return feeds.sortedWith(
            compareBy<FeedEntity> { categoryOrderIndex(it.category) }
                .thenBy { it.category.lowercase() }
                .thenBy { it.name.lowercase() }
        )
    }

    fun normalizeUrlKey(url: String): String {
        return url.trim()
            .lowercase()
            .removePrefix("https://")
            .removePrefix("http://")
            .removePrefix("www.")
            .trimEnd('/')
    }

    val portals: List<FeedEntity> = listOf(
        // 1. National News (Nepali Language)
        FeedEntity(
            name = "OnlineKhabar",
            url = "https://www.onlinekhabar.com/feed",
            category = "National News",
            siteUrl = "https://www.onlinekhabar.com"
        ),
        FeedEntity(
            name = "Setopati",
            url = "https://www.setopati.com/feed",
            category = "National News",
            siteUrl = "https://www.setopati.com"
        ),
        FeedEntity(
            name = "Ratopati",
            url = "https://www.ratopati.com/feed",
            category = "National News",
            siteUrl = "https://www.ratopati.com"
        ),
        FeedEntity(
            name = "Ekantipur",
            url = "https://ekantipur.com/feed",
            category = "National News",
            siteUrl = "https://ekantipur.com"
        ),
        FeedEntity(
            name = "BBC Nepali",
            url = "https://feeds.bbci.co.uk/nepali/rss.xml",
            category = "National News",
            siteUrl = "https://www.bbc.com/nepali"
        ),
        FeedEntity(
            name = "Khabarhub",
            url = "https://www.khabarhub.com/feed/",
            category = "National News",
            siteUrl = "https://www.khabarhub.com"
        ),
        FeedEntity(
            name = "Nepal Press",
            url = "https://www.nepalpress.com/feed/",
            category = "National News",
            siteUrl = "https://www.nepalpress.com"
        ),
        FeedEntity(
            name = "Desh Sanchar",
            url = "https://deshsanchar.com/feed/",
            category = "National News",
            siteUrl = "https://deshsanchar.com"
        ),
        FeedEntity(
            name = "Ujyaalo Online",
            url = "https://www.ujyaaloonline.com/feed/",
            category = "National News",
            siteUrl = "https://www.ujyaaloonline.com"
        ),
        FeedEntity(
            name = "Makalu Khabar",
            url = "https://www.makalukhabar.com/feed/",
            category = "National News",
            siteUrl = "https://www.makalukhabar.com"
        ),
        FeedEntity(
            name = "Himal Khabar",
            url = "https://www.himalkhabar.com/feed/",
            category = "National News",
            siteUrl = "https://www.himalkhabar.com"
        ),
        FeedEntity(
            name = "Nepal Live",
            url = "https://nepallive.com/feed/",
            category = "National News",
            siteUrl = "https://nepallive.com"
        ),
        FeedEntity(
            name = "Image Khabar",
            url = "https://www.imagekhabar.com/feed/",
            category = "National News",
            siteUrl = "https://www.imagekhabar.com"
        ),
        FeedEntity(
            name = "Ukeraa",
            url = "https://ukeraa.com/feed/",
            category = "National News",
            siteUrl = "https://ukeraa.com"
        ),

        // 2. English News (Nepal English Portals)
        FeedEntity(
            name = "The Kathmandu Post",
            url = "https://kathmandupost.com/rss",
            category = "English News",
            siteUrl = "https://kathmandupost.com"
        ),
        FeedEntity(
            name = "The Himalayan Times",
            url = "https://thehimalayantimes.com/feed",
            category = "English News",
            siteUrl = "https://thehimalayantimes.com"
        ),
        FeedEntity(
            name = "My Republica",
            url = "https://myrepublica.nagariknetwork.com/rss",
            category = "English News",
            siteUrl = "https://myrepublica.nagariknetwork.com"
        ),
        FeedEntity(
            name = "Nepali Times",
            url = "https://nepalitimes.com/feed/",
            category = "English News",
            siteUrl = "https://nepalitimes.com"
        ),
        FeedEntity(
            name = "OnlineKhabar English",
            url = "https://english.onlinekhabar.com/feed",
            category = "English News",
            siteUrl = "https://english.onlinekhabar.com"
        ),
        FeedEntity(
            name = "Setopati English",
            url = "https://en.setopati.com/feed",
            category = "English News",
            siteUrl = "https://en.setopati.com"
        ),
        FeedEntity(
            name = "Khabarhub English",
            url = "https://english.khabarhub.com/feed/",
            category = "English News",
            siteUrl = "https://english.khabarhub.com"
        ),

        // 3. Business & Economy
        FeedEntity(
            name = "Bizmandu",
            url = "https://bizmandu.com/feed/",
            category = "Business & Economy",
            siteUrl = "https://bizmandu.com"
        ),
        FeedEntity(
            name = "Artha Sarokar",
            url = "https://arthasarokar.com/feed/",
            category = "Business & Economy",
            siteUrl = "https://arthasarokar.com"
        ),
        FeedEntity(
            name = "Clickmandu",
            url = "https://clickmandu.com/feed/",
            category = "Business & Economy",
            siteUrl = "https://clickmandu.com"
        ),
        FeedEntity(
            name = "Bizshala",
            url = "https://bizshala.com/feed/",
            category = "Business & Economy",
            siteUrl = "https://bizshala.com"
        ),

        // 4. Technology
        FeedEntity(
            name = "Techpana",
            url = "https://techpana.com/feed/",
            category = "Technology",
            siteUrl = "https://techpana.com"
        ),
        FeedEntity(
            name = "GadgetByte Nepal",
            url = "https://www.gadgetbytenepal.com/feed/",
            category = "Technology",
            siteUrl = "https://www.gadgetbytenepal.com"
        ),
        FeedEntity(
            name = "TechLekh",
            url = "https://techlekh.com/feed/",
            category = "Technology",
            siteUrl = "https://techlekh.com"
        ),

        // 5. Sports
        FeedEntity(
            name = "Hamro Khelkud",
            url = "https://www.hamrokhelkud.com/feed/",
            category = "Sports",
            siteUrl = "https://www.hamrokhelkud.com"
        ),
        FeedEntity(
            name = "Khelpati",
            url = "https://www.khelpati.com/feed/",
            category = "Sports",
            siteUrl = "https://www.khelpati.com"
        ),

        // 6. Health
        FeedEntity(
            name = "Swasthya Khabar",
            url = "https://swasthyakhabar.com/feed/",
            category = "Health",
            siteUrl = "https://swasthyakhabar.com"
        ),

        // 7. Entertainment
        FeedEntity(
            name = "Artist Khabar",
            url = "https://artistkhabar.com/feed/",
            category = "Entertainment",
            siteUrl = "https://artistkhabar.com"
        )
    )
}
