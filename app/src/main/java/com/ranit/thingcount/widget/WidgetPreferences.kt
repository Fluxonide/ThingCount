package com.ranit.thingcount.widget

import android.content.Context
import com.ranit.thingcount.R

enum class WidgetShape(
    val key: String,
    val label: String,
    val backgroundRes: Int
) {
    ROUNDED("rounded", "Rounded", R.drawable.widget_bg),
    SQUARE("square", "Square", R.drawable.widget_bg_square),
    RECTANGLE("rectangle", "Rectangle", R.drawable.widget_bg_rectangle),
    CIRCLE("circle", "Circle", R.drawable.widget_bg_circle);

    companion object {
        fun fromKey(key: String?): WidgetShape =
            values().firstOrNull { it.key == key } ?: ROUNDED
    }
}

enum class IconShape(val key: String, val label: String) {
    ROUNDED("rounded", "Rounded"),
    CIRCLE("circle", "Circle"),
    SQUARE("square", "Square");

    companion object {
        fun fromKey(key: String?): IconShape =
            values().firstOrNull { it.key == key } ?: ROUNDED
    }
}

object WidgetPreferences {

    private const val PREFS_NAME = "widget_appearance"
    private const val KEY_SHAPE = "shape"
    private const val KEY_ICON_SIZE = "icon_size_dp"
    private const val KEY_ICON_SHAPE = "icon_shape"
    private const val KEY_SHOW_TITLE = "show_title"
    private const val KEY_PREFIX_WIDGET_COUNTER = "widget_counter_"

    const val DEFAULT_ICON_SIZE = 36

    val iconSizes = listOf(20, 28, 36, 40)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCounterIdForWidget(context: Context, appWidgetId: Int): String? =
        prefs(context).getString("$KEY_PREFIX_WIDGET_COUNTER$appWidgetId", null)

    fun setCounterIdForWidget(context: Context, appWidgetId: Int, counterId: String) {
        prefs(context).edit()
            .putString("$KEY_PREFIX_WIDGET_COUNTER$appWidgetId", counterId)
            .apply()
    }

    fun removeCounterIdForWidget(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove("$KEY_PREFIX_WIDGET_COUNTER$appWidgetId")
            .apply()
    }

    fun getShape(context: Context): WidgetShape =
        WidgetShape.fromKey(prefs(context).getString(KEY_SHAPE, WidgetShape.ROUNDED.key))

    fun setShape(context: Context, shape: WidgetShape) {
        prefs(context).edit().putString(KEY_SHAPE, shape.key).apply()
    }

    fun getIconSize(context: Context): Int {
        val size = prefs(context).getInt(KEY_ICON_SIZE, DEFAULT_ICON_SIZE)
        return if (size in iconSizes) size else DEFAULT_ICON_SIZE
    }

    fun setIconSize(context: Context, sizeDp: Int) {
        prefs(context).edit()
            .putInt(KEY_ICON_SIZE, sizeDp.coerceIn(iconSizes.first(), iconSizes.last()))
            .apply()
    }

    fun getIconShape(context: Context): IconShape =
        IconShape.fromKey(prefs(context).getString(KEY_ICON_SHAPE, IconShape.ROUNDED.key))

    fun setIconShape(context: Context, shape: IconShape) {
        prefs(context).edit().putString(KEY_ICON_SHAPE, shape.key).apply()
    }

    fun getShowTitle(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SHOW_TITLE, true)

    fun setShowTitle(context: Context, show: Boolean) {
        prefs(context).edit().putBoolean(KEY_SHOW_TITLE, show).apply()
    }
}
