package com.ranit.thingcount.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.core.graphics.ColorUtils
import com.ranit.thingcount.MainActivity
import com.ranit.thingcount.R
import com.ranit.thingcount.ThingCountApp
import com.ranit.thingcount.data.model.Counter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

class CounterWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_INCREMENT) {
            val counterId = intent.getStringExtra(EXTRA_COUNTER_ID)
            if (counterId != null) {
                CoroutineScope(Dispatchers.IO).launch {
                    val app = context.applicationContext as? ThingCountApp
                    app?.repository?.increment(counterId)
                    refreshAllWidgets(context)
                }
            }
        }
    }

    companion object {
        const val ACTION_INCREMENT = "com.ranit.thingcount.ACTION_WIDGET_INCREMENT"
        const val EXTRA_COUNTER_ID = "com.ranit.thingcount.EXTRA_COUNTER_ID"

        private const val MAX_ICON_BYTES = 5 * 1024 * 1024
        private const val ICON_CACHE_SIZE = 24
        private const val ICON_CONTAINER_DP = 40
        private const val BUTTON_BITMAP_SIZE = 128
        private const val ICON_DISK_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_SIZE) {}
        private val buttonCache = object : LruCache<Int, Bitmap>(16) {}
        private val updateMutex = Mutex()

        fun refreshAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, CounterWidgetProvider::class.java)
            for (widgetId in appWidgetManager.getAppWidgetIds(thisWidget)) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_counter)
            applyAppearance(context, views, appWidgetManager, appWidgetId)

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            CoroutineScope(Dispatchers.IO).launch {
                updateMutex.withLock {
                    val app = context.applicationContext as? ThingCountApp
                    val counter = app?.database?.counterDao()
                        ?.getAllCountersRaw()
                        ?.filter { !it.isDeleted }
                        ?.minWithOrNull(compareBy<Counter> { it.sortOrder }.thenByDescending { it.updatedAt })

                    if (counter != null) {
                        bindCounter(context, views, counter, appWidgetId)
                    } else {
                        bindEmptyState(context, views)
                    }

                    appWidgetManager.updateAppWidget(appWidgetId, views)
                }
            }
        }

        /**
         * Applies the user-selected shape and icon size. The widget layout itself stays
         * the same so RemoteViews keeps working on all supported Android versions.
         */
        private fun applyAppearance(
            context: Context,
            views: RemoteViews,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val shape = WidgetPreferences.getShape(context)
            val iconSizeDp = WidgetPreferences.getIconSize(context)

            views.setInt(R.id.widget_root, "setBackgroundResource", shape.backgroundRes)

            val rootPaddingDp = if (shape == WidgetShape.CIRCLE) {
                // Keep content inside the circle, based on the actual widget size.
                val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
                val shortestSide = minOf(
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH),
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                ).takeIf { it > 0 } ?: 110
                (shortestSide * 0.15f).roundToInt().coerceIn(16, 40)
            } else {
                10
            }
            val rootPadding = dp(context, rootPaddingDp)
            views.setViewPadding(
                R.id.widget_root,
                rootPadding,
                rootPadding,
                rootPadding,
                rootPadding
            )

            val iconPadding = dp(
                context,
                ((ICON_CONTAINER_DP - iconSizeDp) / 2f).coerceAtLeast(0f)
            )
            views.setViewPadding(R.id.widget_icon, iconPadding, iconPadding, iconPadding, iconPadding)
            views.setViewPadding(
                R.id.widget_icon_emoji,
                iconPadding,
                iconPadding,
                iconPadding,
                iconPadding
            )
            views.setTextViewTextSize(
                R.id.widget_icon_emoji,
                TypedValue.COMPLEX_UNIT_SP,
                iconSizeDp * 0.8f
            )

            views.setViewVisibility(
                R.id.widget_title,
                if (WidgetPreferences.getShowTitle(context)) View.VISIBLE else View.INVISIBLE
            )
        }

        private fun bindCounter(
            context: Context,
            views: RemoteViews,
            counter: Counter,
            appWidgetId: Int
        ) {
            views.setTextViewText(R.id.widget_title, counter.title)
            views.setTextViewText(R.id.widget_count, counter.currentValue.toString())

            val accent = counter.colorHex.toInt() or 0xFF000000.toInt()
            views.setImageViewBitmap(R.id.widget_btn_increment_bg, circleButtonBitmap(accent))
            views.setTextColor(R.id.widget_btn_plus, contentColorFor(accent))
            views.setContentDescription(
                R.id.widget_btn_increment,
                "Increase ${counter.title} by ${counter.step}"
            )

            bindIcon(context, views, counter.emoji)

            val incrementIntent = Intent(context, CounterWidgetProvider::class.java).apply {
                action = ACTION_INCREMENT
                putExtra(EXTRA_COUNTER_ID, counter.id)
            }
            val incrementPendingIntent = PendingIntent.getBroadcast(
                context,
                appWidgetId,
                incrementIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_btn_increment, incrementPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_count, incrementPendingIntent)
        }

        private fun bindEmptyState(context: Context, views: RemoteViews) {
            views.setTextViewText(R.id.widget_title, "ThingCount")
            views.setTextViewText(R.id.widget_count, "0")
            views.setImageViewBitmap(
                R.id.widget_btn_increment_bg,
                circleButtonBitmap(0xFF3871E0.toInt())
            )
            views.setTextColor(R.id.widget_btn_plus, Color.WHITE)
            views.setContentDescription(R.id.widget_btn_increment, "Open ThingCount")
            views.setViewVisibility(R.id.widget_icon, View.GONE)
            views.setViewVisibility(R.id.widget_icon_emoji, View.VISIBLE)
            views.setTextViewText(R.id.widget_icon_emoji, "🎯")

            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_increment, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_count, openAppPendingIntent)
        }

        private fun bindIcon(context: Context, views: RemoteViews, icon: String) {
            val iconValue = icon.trim()
            val bitmap = loadIconBitmap(
                context,
                iconValue,
                WidgetPreferences.getIconShape(context)
            )

            if (bitmap != null) {
                views.setImageViewBitmap(R.id.widget_icon, bitmap)
                views.setViewVisibility(R.id.widget_icon, View.VISIBLE)
                views.setViewVisibility(R.id.widget_icon_emoji, View.GONE)
            } else {
                // Never show a raw URL/path in the widget: fall back to an emoji.
                val fallback = if (isImageReference(iconValue)) "🎯" else iconValue.ifBlank { "🎯" }
                views.setTextViewText(R.id.widget_icon_emoji, fallback)
                views.setViewVisibility(R.id.widget_icon, View.GONE)
                views.setViewVisibility(R.id.widget_icon_emoji, View.VISIBLE)
            }
        }

        private fun isImageReference(value: String): Boolean =
            value.startsWith("http://", ignoreCase = true) ||
                    value.startsWith("https://", ignoreCase = true) ||
                    value.startsWith("content://", ignoreCase = true) ||
                    value.startsWith("file://", ignoreCase = true) ||
                    value.startsWith("/")

        private fun loadIconBitmap(
            context: Context,
            icon: String,
            iconShape: IconShape
        ): Bitmap? {
            if (icon.isBlank() || !isImageReference(icon)) return null

            val cacheKey = "$icon@${iconShape.key}"
            iconCache.get(cacheKey)?.let { return it }

            val bytes = try {
                when {
                    icon.startsWith("http://", ignoreCase = true) ||
                            icon.startsWith("https://", ignoreCase = true) ->
                        readUrlIcon(context, icon)

                    else -> {
                        val uri = Uri.parse(icon)
                        val stream: InputStream? = when {
                            icon.startsWith("content://", ignoreCase = true) ->
                                context.contentResolver.openInputStream(uri)

                            icon.startsWith("file://", ignoreCase = true) -> {
                                val path = uri.path ?: return null
                                FileInputStream(File(path))
                            }

                            else -> FileInputStream(File(icon))
                        }
                        stream?.use { readLimited(it) }
                    }
                }
            } catch (_: Exception) {
                null
            } ?: return null

            val density = context.resources.displayMetrics.density
            val sizePx = (ICON_CONTAINER_DP * density).toInt().coerceAtLeast(48)
            val decoded = decodeSampled(bytes, sizePx * 2) ?: return null
            val badge = iconBadge(decoded, sizePx, iconShape)
            if (badge !== decoded) decoded.recycle()
            iconCache.put(cacheKey, badge)
            return badge
        }

        /**
         * URL icons are cached on disk so the widget still shows the blade pack
         * image after a reboot or when the phone is offline.
         */
        private fun readUrlIcon(context: Context, url: String): ByteArray? {
            val file = iconDiskFile(context, url)
            val cached = readCachedBytes(file)
            val isFresh = file.exists() &&
                    System.currentTimeMillis() - file.lastModified() < ICON_DISK_CACHE_MAX_AGE_MS

            if (cached != null && isFresh) return cached

            val downloaded = downloadBytes(url)
            if (downloaded != null) {
                try {
                    file.parentFile?.mkdirs()
                    file.writeBytes(downloaded)
                } catch (_: Exception) {
                    // Disk cache is best-effort.
                }
                return downloaded
            }

            // Offline or bad response: fall back to whatever was cached before.
            return cached
        }

        private fun iconDiskFile(context: Context, url: String): File {
            val fileName = "${Integer.toHexString(url.hashCode())}_${url.length}.img"
            return File(File(context.filesDir, "widget_icons"), fileName)
        }

        private fun readCachedBytes(file: File): ByteArray? = try {
            if (file.exists()) FileInputStream(file).use { readLimited(it) } else null
        } catch (_: Exception) {
            null
        }

        private fun downloadBytes(url: String): ByteArray? {
            var connection: HttpURLConnection? = null
            return try {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", "ThingCount-Widget/1.0")
                if (connection.responseCode !in 200..299) return null
                connection.inputStream.use { readLimited(it) }
            } catch (_: Exception) {
                null
            } finally {
                connection?.disconnect()
            }
        }

        private fun readLimited(input: InputStream): ByteArray? {
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > MAX_ICON_BYTES) return null
                output.write(buffer, 0, read)
            }
            return output.toByteArray()
        }

        private fun decodeSampled(bytes: ByteArray, targetPx: Int): Bitmap? {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= targetPx &&
                bounds.outHeight / (sampleSize * 2) >= targetPx
            ) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        }

        private fun iconBadge(source: Bitmap, sizePx: Int, shape: IconShape): Bitmap {
            val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val size = sizePx.toFloat()
            val rect = RectF(0f, 0f, size, size)
            val path = Path().apply {
                when (shape) {
                    IconShape.CIRCLE ->
                        addCircle(size / 2f, size / 2f, size / 2f, Path.Direction.CW)

                    IconShape.ROUNDED ->
                        addRoundRect(rect, size * 0.22f, size * 0.22f, Path.Direction.CW)

                    IconShape.SQUARE ->
                        addRoundRect(rect, size * 0.03f, size * 0.03f, Path.Direction.CW)
                }
            }

            canvas.save()
            canvas.clipPath(path)

            // A white tile suits blade pack / product images that usually have
            // a white background, and keeps transparent PNGs readable.
            canvas.drawPath(
                path,
                Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            )

            // Fit the whole image inside the tile instead of cropping its edges.
            val available = size * 0.88f
            val scale = kotlin.math.min(
                available / source.width,
                available / source.height
            )
            val left = (size - source.width * scale) / 2f
            val top = (size - source.height * scale) / 2f
            val destination = RectF(
                left,
                top,
                left + source.width * scale,
                top + source.height * scale
            )
            canvas.drawBitmap(
                source,
                null,
                destination,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            canvas.restore()
            return output
        }

        private fun circleButtonBitmap(color: Int): Bitmap {
            buttonCache.get(color)?.let { return it }

            val size = BUTTON_BITMAP_SIZE
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f, 0f, 0f, size.toFloat(),
                ColorUtils.blendARGB(color, Color.WHITE, 0.16f),
                color,
                Shader.TileMode.CLAMP
            )
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
            buttonCache.put(color, bitmap)
            return bitmap
        }

        private fun contentColorFor(background: Int): Int =
            if (ColorUtils.calculateLuminance(background) > 0.55) Color.BLACK else Color.WHITE

        private fun dp(context: Context, value: Int): Int =
            (value * context.resources.displayMetrics.density).roundToInt()

        private fun dp(context: Context, value: Float): Int =
            (value * context.resources.displayMetrics.density).roundToInt()
    }
}
