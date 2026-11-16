package com.shilapi.xcertplay

import android.content.Context
import android.content.SharedPreferences
import android.graphics.RectF
import org.json.JSONObject

/** Positions are fractions of the whole map plane, independent of display density. */
internal enum class OriginalClusterComponent(val key: String, val left: Float, val top: Float,
    val width: Float, val height: Float) {
    TIRES("tires", .045f, .379f, .241f, .244f),
    DRIVING("driving", .045f, .665f, .241f, .244f),
    TURN("turn", .736f, .094f, .221f, .255f),
    TIME("time", .736f, .828f, .221f, .081f),
    MUSIC("music", .371f, .704f, .258f, .230f),
}

internal data class OriginalClusterPlacement(val visible: Boolean = true, val x: Float, val y: Float)

internal data class OriginalClusterLayout(val items: Map<OriginalClusterComponent, OriginalClusterPlacement> =
    OriginalClusterComponent.entries.associateWith { OriginalClusterPlacement(x = it.left, y = it.top) }) {
    operator fun get(component: OriginalClusterComponent) = items[component]
        ?: OriginalClusterPlacement(x = component.left, y = component.top)

    fun place(component: OriginalClusterComponent, x: Float, y: Float): OriginalClusterLayout {
        val safeX = x.takeIf { it.isFinite() } ?: component.left
        val safeY = y.takeIf { it.isFinite() } ?: component.top
        return copy(items = items + (component to get(component).copy(
            x = safeX.coerceIn(0f, 1f - component.width),
            y = safeY.coerceIn(0f, 1f - component.height))))
    }

    fun show(component: OriginalClusterComponent, visible: Boolean) =
        copy(items = items + (component to get(component).copy(visible = visible)))

    fun bounds(component: OriginalClusterComponent): RectF = get(component).let {
        RectF(it.x * WIDTH, it.y * HEIGHT, (it.x + component.width) * WIDTH, (it.y + component.height) * HEIGHT)
    }

    fun encode(): String = JSONObject().apply {
        put("version", 1)
        OriginalClusterComponent.entries.forEach { component ->
            val p = get(component)
            put(component.key, JSONObject().put("visible", p.visible).put("x", p.x).put("y", p.y))
        }
    }.toString()

    companion object {
        const val WIDTH = 2048f
        const val HEIGHT = 768f
        fun decode(json: String?): OriginalClusterLayout {
            val root = runCatching { JSONObject(json.orEmpty()) }.getOrNull() ?: return OriginalClusterLayout()
            if (root.optInt("version", 1) != 1) return OriginalClusterLayout()
            return OriginalClusterComponent.entries.fold(OriginalClusterLayout()) { result, component ->
                val p = root.optJSONObject(component.key) ?: return@fold result
                result.place(component, p.optDouble("x", component.left.toDouble()).toFloat(),
                    p.optDouble("y", component.top.toDouble()).toFloat())
                    .show(component, p.optBoolean("visible", true))
            }
        }
    }
}

internal object OriginalClusterSettings {
    private const val KEY = "layout"
    private fun prefs(context: Context) = context.applicationContext
        .getSharedPreferences("original_cluster_components", Context.MODE_PRIVATE)
    fun load(context: Context) = OriginalClusterLayout.decode(prefs(context).getString(KEY, null))
    fun save(context: Context, layout: OriginalClusterLayout) { prefs(context).edit().putString(KEY, layout.encode()).apply() }
    fun subscribe(context: Context, changed: () -> Unit): () -> Unit {
        val prefs = prefs(context)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == KEY) changed() }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}
