package com.lagradost.cloudstream3.ui.categories

import androidx.annotation.StringRes
import com.lagradost.cloudstream3.R

enum class CategorySource { Rows, ForYou, TopWeek, TopMonth, TopRated, Imdb }

data class Category(
    val key: String,
    @StringRes val title: Int,
    val emoji: String,
    val colors: Pair<Int, Int>,
    val rowKeywords: List<String> = emptyList(),
    val searchQuery: String? = null,
    val source: CategorySource = CategorySource.Rows,
    val imdbGenre: String? = null,
    val imdbCatalog: String = "top",
    val imdbTypes: List<String> = listOf("movie", "series"),
    val tmdb: String? = null,
)

object CategoryCatalog {
    val all = listOf(
        Category("for_you", R.string.cat_for_you, "✨", 0xFFE50914.toInt() to 0xFF5A0006.toInt(), source = CategorySource.ForYou),
        Category("top_week", R.string.cat_top_week, "🔥", 0xFFFF7A00.toInt() to 0xFF6B1C00.toInt(), source = CategorySource.TopWeek),
        Category("top_month", R.string.cat_top_month, "🏆", 0xFFF5B700.toInt() to 0xFF6A4500.toInt(), source = CategorySource.TopMonth),
        Category("imdb_movies", R.string.cat_imdb_movies, "🏅", 0xFFF5C518.toInt() to 0xFF3D3000.toInt(), source = CategorySource.Imdb, imdbCatalog = "imdbRating", imdbTypes = listOf("movie")),
        Category("imdb_series", R.string.cat_imdb_series, "📺", 0xFFF5C518.toInt() to 0xFF2B2200.toInt(), source = CategorySource.Imdb, imdbCatalog = "imdbRating", imdbTypes = listOf("series")),
        Category("popular_movies", R.string.cat_popular_movies, "🍿", 0xFFE50914.toInt() to 0xFF2A0003.toInt(), source = CategorySource.Imdb, imdbTypes = listOf("movie")),
        Category("popular_series", R.string.cat_popular_series, "🎞️", 0xFF9B1B30.toInt() to 0xFF26050C.toInt(), source = CategorySource.Imdb, imdbTypes = listOf("series")),
        Category("top_hindi", R.string.cat_top_hindi, "🇮🇳", 0xFFFF9933.toInt() to 0xFF0B5D1E.toInt(), source = CategorySource.Imdb, imdbTypes = emptyList(), tmdb = "top_hindi"),
        Category("top_rated", R.string.cat_top_rated, "⭐", 0xFFFFC83D.toInt() to 0xFF4A3500.toInt(), source = CategorySource.TopRated),
        Category("trending", R.string.cat_trending, "📈", 0xFF2F80ED.toInt() to 0xFF0B2350.toInt(), listOf("trending", "popular", "top", "most watched"), tmdb = "trending_week"),
        Category("latest", R.string.cat_latest, "🆕", 0xFF12B5A6.toInt() to 0xFF04403A.toInt(), listOf("latest", "new", "recent", "release", "added", "update"), tmdb = "new_hindi"),
        Category("bollywood", R.string.cat_bollywood, "🎬", 0xFFFF4F8B.toInt() to 0xFF5C0F2B.toInt(), listOf("bollywood", "hindi movie", "hindi"), "bollywood", tmdb = "trending_india"),
        Category("hollywood", R.string.cat_hollywood, "🎥", 0xFF6C63FF.toInt() to 0xFF1D1A5C.toInt(), listOf("hollywood", "english"), "hollywood", tmdb = "hollywood"),
        Category("south", R.string.cat_south, "🌴", 0xFF27AE60.toInt() to 0xFF0A3D1F.toInt(), listOf("south", "dubbed", "tamil", "telugu", "malayalam", "kannada"), "south hindi dubbed", tmdb = "south"),
        Category("web_series", R.string.cat_web_series, "📺", 0xFF8E44AD.toInt() to 0xFF2E0F3D.toInt(), listOf("web series", "series", "tv show", "shows", "season"), "web series", tmdb = "hindi_series"),
        Category("anime", R.string.cat_anime, "🎌", 0xFFE84393.toInt() to 0xFF4A0B2C.toInt(), listOf("anime"), "anime", imdbGenre = "Animation"),
        Category("cartoons", R.string.cat_cartoons, "🧸", 0xFF00B4D8.toInt() to 0xFF003A47.toInt(), listOf("cartoon", "kids", "animation", "animated"), "cartoon"),
        Category("kdrama", R.string.cat_kdrama, "🇰🇷", 0xFFFF6B6B.toInt() to 0xFF4D1414.toInt(), listOf("korean", "k-drama", "kdrama", "asian", "chinese", "drama"), "korean drama", tmdb = "korean"),
        Category("action", R.string.cat_action, "💥", 0xFFD35400.toInt() to 0xFF3D1800.toInt(), listOf("action"), "action", imdbGenre = "Action"),
        Category("comedy", R.string.cat_comedy, "😂", 0xFFF1C40F.toInt() to 0xFF4A3B00.toInt(), listOf("comedy"), "comedy", imdbGenre = "Comedy"),
        Category("horror", R.string.cat_horror, "👻", 0xFF34495E.toInt() to 0xFF0B1116.toInt(), listOf("horror"), "horror", imdbGenre = "Horror"),
        Category("thriller", R.string.cat_thriller, "🕵️", 0xFF2C3E50.toInt() to 0xFF0A1015.toInt(), listOf("thriller", "mystery", "suspense"), "thriller", imdbGenre = "Thriller"),
        Category("romance", R.string.cat_romance, "❤️", 0xFFE74C3C.toInt() to 0xFF4A0F0A.toInt(), listOf("romance", "romantic", "love"), "romance", imdbGenre = "Romance"),
        Category("scifi", R.string.cat_scifi, "🚀", 0xFF3498DB.toInt() to 0xFF0C2A40.toInt(), listOf("sci-fi", "science", "fantasy", "superhero"), "sci-fi", imdbGenre = "Sci-Fi"),
        Category("crime", R.string.cat_crime, "🔪", 0xFF7F8C8D.toInt() to 0xFF1F2424.toInt(), listOf("crime", "gangster"), "crime", imdbGenre = "Crime"),
        Category("family", R.string.cat_family, "👨‍👩‍👧", 0xFF16A085.toInt() to 0xFF053329.toInt(), listOf("family"), "family", imdbGenre = "Family"),
        Category("documentary", R.string.cat_documentary, "🌍", 0xFF95A5A6.toInt() to 0xFF262B2C.toInt(), listOf("documentary", "docu"), "documentary", imdbGenre = "Documentary"),
    )
}
