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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        for (appWidgetId in appWidgetIds) {
            WidgetPreferences.removeCounterIdForWidget(context, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action
        if (action != ACTION_INCREMENT && action != ACTION_DECREMENT) return

        val counterId = intent.getStringExtra(EXTRA_COUNTER_ID) ?: return
        CoroutineScope(Dispatchers.IO).launch {
            val app = context.applicationContext as? ThingCountApp
            when (action) {
                ACTION_INCREMENT -> app?.repository?.increment(counterId)
                ACTION_DECREMENT -> app?.repository?.decrement(counterId)
            }
            refreshAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_INCREMENT = "com.ranit.thingcount.ACTION_WIDGET_INCREMENT"
        const val ACTION_DECREMENT = "com.ranit.thingcount.ACTION_WIDGET_DECREMENT"
        const val EXTRA_COUNTER_ID = "com.ranit.thingcount.EXTRA_COUNTER_ID"

        private const val MAX_ICON_BYTES = 5 * 1024 * 1024
        private const val ICON_CACHE_SIZE = 24
        private const val ICON_CONTAINER_DP = 40
        private const val BUTTON_BITMAP_SIZE = 128
        private const val ICON_DISK_CACHE_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000

        private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_SIZE) {}
        private val buttonCache = object : LruCache<Int, Bitmap>(16) {}
        private val updateMutex = Mutex()

        /**
         * Dynamically computes title text size based on the widget's current dimensions.
         * As the user resizes the widget (larger width or height), the title scales up
         * to fill the available space nicely.
         */
        private fun computeDynamicTitleSize(
            widthDp: Int,
            heightDp: Int,
            hasIcon: Boolean
        ): Float {
            val clampedWidth = widthDp.coerceIn(100, 480)
            val clampedHeight = heightDp.coerceIn(100, 480)

            // Factor ranges from 0.0 (compact 2x2 ~110dp) to 1.0 (large 4x4 or full width ~350dp+)
            val widthGrowth = ((clampedWidth - 110) / 240f).coerceIn(0f, 1f)
            val heightGrowth = ((clampedHeight - 110) / 240f).coerceIn(0f, 1f)
            val growthFactor = (widthGrowth * 0.75f + heightGrowth * 0.25f)

            return if (hasIcon) {
                // Scales smoothly from 16sp on 2x2 up to 30sp on large widgets
                16f + (growthFactor * 14f)
            } else {
                // Scales smoothly from 22sp on 2x2 up to 38sp on large widgets
                22f + (growthFactor * 16f)
            }
        }

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
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH).takeIf { it > 0 } ?: 120
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT).takeIf { it > 0 } ?: 120

            applyAppearance(context, views, appWidgetManager, appWidgetId)

            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                (appWidgetId shl 2) or 2,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openAppPendingIntent)

            CoroutineScope(Dispatchers.IO).launch {
                updateMutex.withLock {
                    val app = context.applicationContext as? ThingCountApp
                    val allCounters = app?.database?.counterDao()
                        ?.getAllCountersRaw()
                        ?.filter { !it.isDeleted } ?: emptyList()

                    val targetCounterId = WidgetPreferences.getCounterIdForWidget(context, appWidgetId)
                    var counter = allCounters.find { it.id == targetCounterId }

                    if (counter == null && allCounters.isNotEmpty()) {
                        counter = allCounters.minWithOrNull(compareBy<Counter> { it.sortOrder }.thenByDescending { it.updatedAt })
                        if (counter != null) {
                            WidgetPreferences.setCounterIdForWidget(context, appWidgetId, counter.id)
                        }
                    }

                    if (counter != null) {
                        bindCounter(context, views, counter, appWidgetId, widthDp, heightDp)
                    } else {
                        bindEmptyState(context, views, appWidgetId, widthDp, heightDp)
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
                (shortestSide * 0.10f).roundToInt().coerceIn(8, 28)
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
                if (WidgetPreferences.getShowTitle(context)) View.VISIBLE else View.GONE
            )
        }

        private fun bindCounter(
            context: Context,
            views: RemoteViews,
            counter: Counter,
            appWidgetId: Int,
            widthDp: Int,
            heightDp: Int
        ) {
            views.setTextViewText(R.id.widget_title, counter.title)
            views.setTextViewText(R.id.widget_count, counter.currentValue.toString())

            val hasIcon = bindIcon(context, views, counter.emoji)
            val titleSize = computeDynamicTitleSize(widthDp, heightDp, hasIcon)

            views.setTextViewTextSize(
                R.id.widget_title,
                TypedValue.COMPLEX_UNIT_SP,
                titleSize
            )
            views.setInt(R.id.widget_title, "setMaxLines", if (heightDp >= 180) 3 else 2)

            val accent = counter.colorHex.toInt() or 0xFF000000.toInt()
            val buttonColor = contentColorFor(accent)
            views.setImageViewBitmap(R.id.widget_btn_increment_bg, circleButtonBitmap(accent))
            views.setImageViewBitmap(R.id.widget_btn_decrement_bg, circleButtonBitmap(accent))
            views.setTextColor(R.id.widget_btn_plus, buttonColor)
            views.setTextColor(R.id.widget_btn_minus, buttonColor)

            views.setContentDescription(
                R.id.widget_btn_increment,
                "Increase ${counter.title} by ${counter.step}"
            )
            views.setContentDescription(
                R.id.widget_btn_decrement,
                "Decrease ${counter.title} by ${counter.step}"
            )

            val incrementIntent = Intent(context, CounterWidgetProvider::class.java).apply {
                action = ACTION_INCREMENT
                putExtra(EXTRA_COUNTER_ID, counter.id)
            }
            val decrementIntent = Intent(context, CounterWidgetProvider::class.java).apply {
                action = ACTION_DECREMENT
                putExtra(EXTRA_COUNTER_ID, counter.id)
            }
            val incrementPendingIntent = PendingIntent.getBroadcast(
                context,
                (appWidgetId shl 2) or 0,
                incrementIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val decrementPendingIntent = PendingIntent.getBroadcast(
                context,
                (appWidgetId shl 2) or 1,
                decrementIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            views.setOnClickPendingIntent(R.id.widget_btn_increment, incrementPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_decrement, decrementPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_count, incrementPendingIntent)
        }

        private fun bindEmptyState(
            context: Context,
            views: RemoteViews,
            appWidgetId: Int,
            widthDp: Int,
            heightDp: Int
        ) {
            val titleSize = computeDynamicTitleSize(widthDp, heightDp, true)
            views.setTextViewText(R.id.widget_title, "Tap to Select")
            views.setTextViewTextSize(
                R.id.widget_title,
                TypedValue.COMPLEX_UNIT_SP,
                titleSize
            )
            views.setTextViewText(R.id.widget_count, "+")

            val blue = 0xFF3871E0.toInt()
            views.setImageViewBitmap(R.id.widget_btn_increment_bg, circleButtonBitmap(blue))
            views.setImageViewBitmap(R.id.widget_btn_decrement_bg, circleButtonBitmap(blue))
            views.setTextColor(R.id.widget_btn_plus, Color.WHITE)
            views.setTextColor(R.id.widget_btn_minus, Color.WHITE)
            views.setContentDescription(R.id.widget_btn_increment, "Select counter")
            views.setContentDescription(R.id.widget_btn_decrement, "Select counter")

            views.setViewVisibility(R.id.widget_icon_frame, View.VISIBLE)
            views.setViewVisibility(R.id.widget_icon, View.GONE)
            views.setViewVisibility(R.id.widget_icon_emoji, View.VISIBLE)
            views.setTextViewText(R.id.widget_icon_emoji, "🎯")

            val configIntent = Intent(context, CounterWidgetConfigureActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val configPendingIntent = PendingIntent.getActivity(
                context,
                (appWidgetId shl 2) or 3,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, configPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_increment, configPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_btn_decrement, configPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_count, configPendingIntent)
        }

        private fun bindIcon(context: Context, views: RemoteViews, icon: String): Boolean {
            val iconValue = icon.trim()
            if (iconValue.isBlank()) {
                views.setViewVisibility(R.id.widget_icon_frame, View.GONE)
                views.setViewVisibility(R.id.widget_icon, View.GONE)
                views.setViewVisibility(R.id.widget_icon_emoji, View.GONE)
                return false
            }

            views.setViewVisibility(R.id.widget_icon_frame, View.VISIBLE)
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
                val fallback = if (isImageReference(iconValue)) "🎯" else iconValue
                views.setTextViewText(R.id.widget_icon_emoji, fallback)
                views.setViewVisibility(R.id.widget_icon, View.GONE)
                views.setViewVisibility(R.id.widget_icon_emoji, View.VISIBLE)
            }
            return true
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
