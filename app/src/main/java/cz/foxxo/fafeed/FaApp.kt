package cz.foxxo.fafeed

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import cz.foxxo.fafeed.data.FA_USER_AGENT
import cz.foxxo.fafeed.data.FaParser
import cz.foxxo.fafeed.data.Prefs
import okhttp3.OkHttpClient
import okio.Path.Companion.toPath
import java.util.concurrent.TimeUnit

class FaApp : Application(), SingletonImageLoader.Factory {

    lateinit var prefs: Prefs
        private set

    /** Disk cache for thumbnails / images, so scrolling back never re-downloads. */
    var imageDiskCache: DiskCache? = null
        private set

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        imageClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val builder = chain.request().newBuilder()
                    .header("User-Agent", FA_USER_AGENT)
                    .header("Referer", FaParser.BASE + "/")
                if (prefs.cookies.isNotBlank()) {
                    builder.header("Cookie", prefs.cookies)
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    private lateinit var imageClient: OkHttpClient

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val disk = DiskCache.Builder()
            .directory(context.cacheDir.resolve(DISK_CACHE_DIR).absolutePath.toPath())
            .maxSizeBytes(DISK_CACHE_MAX_BYTES)
            .build()
        imageDiskCache = disk

        return ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache(disk)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(220)
            .build()
    }

    companion object {
        /** Same directory Coil used implicitly before, so already cached thumbs stay valid. */
        const val DISK_CACHE_DIR = "coil3_disk_cache"
        const val DISK_CACHE_MAX_BYTES = 512L * 1024L * 1024L
        const val MEMORY_CACHE_PERCENT = 0.20
    }
}
