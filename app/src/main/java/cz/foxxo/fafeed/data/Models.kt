package cz.foxxo.fafeed.data

data class FeedItem(
    val id: Long,
    val title: String,
    val author: String,
    val authorDisplay: String,
    val rating: String,
    val type: String,
    val thumbUrl: String,
    val width: Float,
    val height: Float,
) {
    val viewUrl: String get() = "${FaParser.BASE}/view/$id/"
    val authorUrl: String get() = "${FaParser.BASE}/user/$author/"
    val aspectRatio: Float
        get() = when {
            width > 0f && height > 0f -> (width / height).coerceIn(0.45f, 2.2f)
            else -> 1f
        }
    val isNsfw: Boolean get() = rating == "adult" || rating == "mature"
}

data class FeedPage(
    val items: List<FeedItem>,
    val nextUrl: String?,
    val loggedInUser: String?,
    val loginRequired: Boolean,
)

data class SubmissionDetail(
    val id: Long,
    val title: String,
    val author: String,
    val authorDisplay: String,
    val imageUrl: String,
    val dateEpoch: Long?,
    val description: String,
    val views: String,
    val favorites: String,
    val comments: String,
    val rating: String,
    val tags: List<String>,
    val downloadUrl: String?,
) {
    val viewUrl: String get() = "${FaParser.BASE}/view/$id/"
}

class FaAuthException(message: String = "Přihlášení na FurAffinity vypršelo") : Exception(message)
