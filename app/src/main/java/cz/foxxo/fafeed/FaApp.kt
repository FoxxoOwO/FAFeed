package cz.foxxo.fafeed

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import cz.foxxo.fafeed.data.FA_USER_AGENT
import cz.foxxo.fafeed.data.FaParser
import cz.foxxo.fafeed.data.Prefs
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class FaApp : Application(), SingletonImageLoader.Factory {

    lateinit var prefs: Prefs
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

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { imageClient }))
            }
            .crossfade(true)
            .build()
}
