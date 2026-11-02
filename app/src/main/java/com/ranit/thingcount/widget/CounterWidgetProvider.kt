package com.ranit.thingcount.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.util.LruCache
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
import kotlin.math.max

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
        private const val PILL_WIDTH = 512
        private const val PILL_HEIGHT = 96

        private val iconCache = object : LruCache<String, Bitmap>(ICON_CACHE_SIZE) {}
        private val pillCache = object : LruCache<Int, Bitmap>(16) {}
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

        private fun bindCounter(
            context: Context,
            views: RemoteViews,
            counter: Counter,
            appWidgetId: Int
        ) {
            views.setTextViewText(R.id.widget_title, counter.title)
            views.setTextViewText(R.id.widget_count, counter.currentValue.toString())
            views.setTextViewText(R.id.widget_step, "Step ±${counter.step}")
            views.setTextViewText(R.id.widget_btn_increment_label, "+ ${counter.step}")

            val accent = counter.colorHex.toInt() or 0xFF000000.toInt()
            views.setImageViewBitmap(R.id.widget_btn_increment_bg, pillBitmap(accent))
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
            views.setTextViewText(R.id.widget_step, "Tap to add a counter")
            views.setTextViewText(R.id.widget_btn_increment_label, "Open app")
            views.setImageViewBitmap(
                R.id.widget_btn_increment_bg,
                pillBitmap(0xFF3871E0.toInt())
            )
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
            val bitmap = loadIconBitmap(context, iconValue)

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

        private fun loadIconBitmap(context: Context, icon: String): Bitmap? {
            if (icon.isBlank() || !isImageReference(icon)) return null

            iconCache.get(icon)?.let { return it }

            val bytes = try {
                when {
                    icon.startsWith("http://", ignoreCase = true) ||
                            icon.startsWith("https://", ignoreCase = true) -> downloadBytes(icon)

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
            val sizePx = (28 * density).toInt().coerceAtLeast(48)
            val decoded = decodeSampled(bytes, sizePx * 2) ?: return null
            val circular = circleCrop(decoded, sizePx)
            if (circular !== decoded) decoded.recycle()
            iconCache.put(icon, circular)
            return circular
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

        private fun circleCrop(source: Bitmap, sizePx: Int): Bitmap {
            val output = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

            val scale = max(
                sizePx.toFloat() / source.width,
                sizePx.toFloat() / source.height
            )
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    (sizePx - source.width * scale) / 2f,
                    (sizePx - source.height * scale) / 2f
                )
            }
            shader.setLocalMatrix(matrix)
            paint.shader = shader
            canvas.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, paint)
            return output
        }

        private fun pillBitmap(color: Int): Bitmap {
            pillCache.get(color)?.let { return it }

            val bitmap = Bitmap.createBitmap(PILL_WIDTH, PILL_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.shader = LinearGradient(
                0f, 0f, 0f, PILL_HEIGHT.toFloat(),
                ColorUtils.blendARGB(color, Color.WHITE, 0.16f),
                color,
                Shader.TileMode.CLAMP
            )
            canvas.drawRoundRect(
                RectF(0f, 0f, PILL_WIDTH.toFloat(), PILL_HEIGHT.toFloat()),
                PILL_HEIGHT / 2f,
                PILL_HEIGHT / 2f,
                paint
            )
            pillCache.put(color, bitmap)
            return bitmap
        }
    }
}
