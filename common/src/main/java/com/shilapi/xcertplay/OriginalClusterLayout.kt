package com.shilapi.xcertplay

import android.content.Context
import android.content.SharedPreferences
import android.graphics.RectF
import org.json.JSONObject
import org.json.JSONArray
import android.graphics.Matrix

/** Positions are fractions of the whole map plane, independent of display density. */
internal enum class OriginalClusterComponent(val key: String, val left: Float, val top: Float,
    val width: Float, val height: Float) {
    MAP("map", 0f, 0f, 1f, 1f),
    TIRES("tires", .045f, .379f, .241f, .244f),
    DRIVING("driving", .045f, .665f, .241f, .244f),
    TURN("turn", .736f, .094f, .221f, .255f),
    TIME("time", .736f, .828f, .221f, .081f),
    MUSIC("music", .371f, .704f, .258f, .230f),
}

/** Initial glass appearance; the saved value itself always spans transparent to opaque. */
internal val OriginalClusterComponent.defaultBackgroundOpacity: Float get() = when (this) {
    OriginalClusterComponent.MAP -> 1f
    OriginalClusterComponent.TURN -> 252f / 255f
    OriginalClusterComponent.TIME -> 232f / 255f
    else -> 92f / 255f
}

internal data class OriginalClusterPlacement(val visible: Boolean = true, val x: Float, val y: Float,
    val scale: Float = 1f, val rotation: Float = 0f, val backgroundOpacity: Float = 1f, val opacity: Float = 1f,
    val flipHorizontal: Boolean = false, val flipVertical: Boolean = false) {
    // Negative origins let a map scaled around its centre reach the top/left edges too.
    fun sanitized(minScale: Float = .2f, maxScale: Float = 3f) = copy(x = x.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
        y = y.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f,
        scale = scale.takeIf { it.isFinite() }?.coerceIn(minScale, maxScale) ?: 1f,
        rotation = rotation.takeIf { it.isFinite() }?.coerceIn(-180f, 180f) ?: 0f,
        backgroundOpacity = backgroundOpacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f,
        opacity = opacity.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: 1f)
    fun json() = JSONObject().put("visible", visible).put("x", x).put("y", y)
        .put("scale", scale).put("rotation", rotation).put("backgroundOpacity", backgroundOpacity).put("opacity", opacity)
        .put("flipHorizontal", flipHorizontal).put("flipVertical", flipVertical)
    companion object {
        fun read(p: JSONObject, x: Float, y: Float, backgroundDefault: Float = 1f, minScale: Float = .2f, maxScale: Float = 3f) = OriginalClusterPlacement(p.optBoolean("visible", true),
            p.optDouble("x", x.toDouble()).toFloat().takeIf { it.isFinite() } ?: x,
            p.optDouble("y", y.toDouble()).toFloat().takeIf { it.isFinite() } ?: y,
            p.optDouble("scale", 1.0).toFloat(), p.optDouble("rotation", 0.0).toFloat(),
            p.optDouble("backgroundOpacity", backgroundDefault.toDouble()).toFloat(), p.optDouble("opacity", 1.0).toFloat(),
            p.optBoolean("flipHorizontal", false), p.optBoolean("flipVertical", false)).sanitized(minScale, maxScale)
    }
}

internal enum class OriginalMediaType { IMAGE, VIDEO }
internal data class OriginalClusterMedia(val id: String, val type: OriginalMediaType, val uri: String,
    val placement: OriginalClusterPlacement = OriginalClusterPlacement(x = .35f, y = .2f),
    val stretchFullScreen: Boolean = false) {
    val width get() = if (stretchFullScreen) 1f else WIDTH
    val height get() = if (stretchFullScreen) 1f else HEIGHT
    companion object { const val WIDTH = .3f; const val HEIGHT = .4f; const val MIN_SCALE = .05f; const val MAX_SCALE = 10f }
}

internal data class OriginalMusicStyle(
    val titleSize: Float = 19f,
    val titleColor: Int = android.graphics.Color.WHITE,
    val showTime: Boolean = true,
    val showPlaybackControls: Boolean = true,
    val artistSize: Float = 17f,
    val artistColor: Int = android.graphics.Color.LTGRAY,
    val lyricSize: Float = 32f,
    val lyricColor: Int = android.graphics.Color.WHITE,
    val progressLength: Float = .86f,
    val progressColor: Int = android.graphics.Color.rgb(65, 223, 240),
    val coverSize: Float = 116f,
) {
    // Measure from the selected style so a new song or lyric cannot resize the card.
    val progressWidth get() = 300f * progressLength
    val textWidth get() = maxOf(276f, progressWidth + 8f, lyricSize * 8f, artistSize * 12f, titleSize * 12f)
    val height get() = PADDING * 2 + maxOf(coverSize, titleRow + artistRow + lyricRow + footerHeight)
    val width get() = PADDING * 2 + coverSize + COLUMN_GAP + textWidth
    val titleRow get() = titleSize * 1.5f + 1.5f
    val footerHeight get() = if (showTime || showPlaybackControls) FOOTER else 13f
    val artistRow get() = artistSize * 1.5f + 3f
    val lyricRow get() = lyricSize * 1.5f + 5f

    fun sanitized() = copy(
        titleSize = titleSize.takeIf(Float::isFinite)?.coerceIn(12f, 38f) ?: 19f,
        artistSize = artistSize.takeIf(Float::isFinite)?.coerceIn(12f, 24f) ?: 17f,
        lyricSize = lyricSize.takeIf(Float::isFinite)?.coerceIn(18f, 38f) ?: 32f,
        progressLength = progressLength.takeIf(Float::isFinite)?.coerceIn(.35f, 1f) ?: .86f,
        coverSize = coverSize.takeIf(Float::isFinite)?.coerceIn(64f, 288f) ?: 116f,
    )

    fun json() = JSONObject().put("titleSize", titleSize).put("titleColor", titleColor)
        .put("showTime", showTime).put("showPlaybackControls", showPlaybackControls).put("artistSize", artistSize).put("artistColor", artistColor)
        .put("lyricSize", lyricSize).put("lyricColor", lyricColor).put("progressLength", progressLength)
        .put("progressColor", progressColor).put("coverSize", coverSize)

    companion object {
        const val PADDING = 12f
        const val COLUMN_GAP = 18f
        const val FOOTER = 32f

        fun read(json: JSONObject?) = json?.let { OriginalMusicStyle(
            titleSize = it.optDouble("titleSize", 19.0).toFloat(),
            titleColor = it.optInt("titleColor", android.graphics.Color.WHITE),
            showTime = it.optBoolean("showTime", true),
            showPlaybackControls = it.optBoolean("showPlaybackControls", true),
            artistSize = it.optDouble("artistSize", 17.0).toFloat(),
            artistColor = it.optInt("artistColor", android.graphics.Color.LTGRAY),
            lyricSize = it.optDouble("lyricSize", 32.0).toFloat(),
            lyricColor = it.optInt("lyricColor", android.graphics.Color.WHITE),
            progressLength = it.optDouble("progressLength", .86).toFloat(),
            progressColor = it.optInt("progressColor", android.graphics.Color.rgb(65, 223, 240)),
            coverSize = it.optDouble("coverSize", 116.0).toFloat(),
        ).sanitized() } ?: OriginalMusicStyle()
    }
}

/** The same transform is used for rendering and inverse hit testing. */
internal fun originalTransform(bounds: RectF, p: OriginalClusterPlacement) = Matrix().apply {
    setScale(p.scale * if (p.flipHorizontal) -1f else 1f,
        p.scale * if (p.flipVertical) -1f else 1f, bounds.centerX(), bounds.centerY())
    postRotate(p.rotation, bounds.centerX(), bounds.centerY())
}
internal fun originalHit(bounds: RectF, p: OriginalClusterPlacement, x: Float, y: Float): Boolean {
    val inverse = Matrix()
    if (!originalTransform(bounds, p).invert(inverse)) return false
    val point = floatArrayOf(x, y); inverse.mapPoints(point)
    return p.visible && bounds.contains(point[0], point[1])
}

internal data class OriginalClusterLayout(val media: List<OriginalClusterMedia> = emptyList(), val items: Map<OriginalClusterComponent, OriginalClusterPlacement> =
    OriginalClusterComponent.entries.associateWith { OriginalClusterPlacement(x = it.left, y = it.top, backgroundOpacity = it.defaultBackgroundOpacity) },
    val layerOrder: List<String> = emptyList(), val musicStyle: OriginalMusicStyle = OriginalMusicStyle()) {
    val orderedLayers: List<String> get() = normalizeLayerOrder(layerOrder, media)

    operator fun get(component: OriginalClusterComponent) = items[component]
        ?: OriginalClusterPlacement(x = component.left, y = component.top, backgroundOpacity = component.defaultBackgroundOpacity)

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
        val left = it.x * WIDTH
        val top = it.y * HEIGHT
        val width = if (component == OriginalClusterComponent.MUSIC) musicStyle.width else component.width * WIDTH
        val height = if (component == OriginalClusterComponent.MUSIC) musicStyle.height else component.height * HEIGHT
        RectF(left, top, left + width, top + height)
    }

    fun update(component: OriginalClusterComponent, placement: OriginalClusterPlacement) =
        copy(items = items + (component to placement.sanitized()))
    fun updateMedia(id: String, placement: OriginalClusterPlacement) =
        copy(media = media.map { if (it.id == id) it.copy(placement = placement.sanitized(OriginalClusterMedia.MIN_SCALE, OriginalClusterMedia.MAX_SCALE)) else it })
    fun stretchMediaFullScreen(id: String) = copy(media = media.map {
        if (it.id == id) it.copy(stretchFullScreen = true, placement = it.placement.copy(
            x = 0f, y = 0f, scale = 1f, rotation = 0f)) else it
    })
    fun moveLayer(id: String, direction: Int): OriginalClusterLayout {
        val order = orderedLayers.toMutableList()
        val index = order.indexOf(id)
        val target = (index + direction.coerceIn(-1, 1)).coerceIn(0, order.lastIndex)
        if (index < 0 || target == index) return this
        order.add(target, order.removeAt(index))
        return copy(layerOrder = order)
    }
    fun moveLayerToEdge(id: String, front: Boolean): OriginalClusterLayout {
        if (id !in orderedLayers) return this
        val order = orderedLayers.filterNot { it == id }.toMutableList()
        order.add(if (front) order.size else 0, id)
        return copy(layerOrder = order)
    }
    fun layerIndex(id: String) = orderedLayers.indexOf(id)
    fun updateMusicStyle(style: OriginalMusicStyle): OriginalClusterLayout {
        val sanitized = style.sanitized()
        val placement = get(OriginalClusterComponent.MUSIC)
        val oldBounds = bounds(OriginalClusterComponent.MUSIC)
        // Keep an originally on-screen card visible as its contents grow; preserve deliberate off-screen placement.
        val x = if (oldBounds.left >= 0f && oldBounds.right <= WIDTH)
            placement.x.coerceAtMost(((WIDTH - sanitized.width) / WIDTH).coerceAtLeast(0f)) else placement.x
        val y = if (oldBounds.top >= 0f && oldBounds.bottom <= HEIGHT)
            placement.y.coerceAtMost(((HEIGHT - sanitized.height) / HEIGHT).coerceAtLeast(0f)) else placement.y
        return copy(musicStyle = sanitized, items = items + (OriginalClusterComponent.MUSIC to placement.copy(x = x, y = y)))
    }
    fun mediaBounds(item: OriginalClusterMedia) = RectF(item.placement.x * WIDTH, item.placement.y * HEIGHT,
        (item.placement.x + item.width) * WIDTH, (item.placement.y + item.height) * HEIGHT)

    fun encode(): String = JSONObject().apply {
        put("version", 6)
        if (layerOrder.isNotEmpty()) put("layerOrder", JSONArray().apply { orderedLayers.forEach(::put) })
        if (musicStyle != OriginalMusicStyle()) put("musicStyle", musicStyle.json())
        OriginalClusterComponent.entries.forEach { put(it.key, get(it).json()) }
        put("media", JSONArray().apply { media.forEach { item -> put(JSONObject()
            .put("id", item.id).put("type", item.type.name).put("uri", item.uri)
            .put("stretchFullScreen", item.stretchFullScreen).put("placement", item.placement.json())) } })
    }.toString()

    companion object {
        const val WIDTH = 2048f
        const val HEIGHT = 768f
        fun decode(json: String?): OriginalClusterLayout {
            val root = runCatching { JSONObject(json.orEmpty()) }.getOrNull() ?: return OriginalClusterLayout()
            if (root.optInt("version", 1) !in listOf(1, 3, 4, 5, 6)) return OriginalClusterLayout()
            var result = OriginalClusterLayout()
            OriginalClusterComponent.entries.forEach { component ->
                root.optJSONObject(component.key)?.let { p -> result = result.update(component,
                    OriginalClusterPlacement.read(p, component.left, component.top,
                        if (root.optInt("version", 1) == 3) 1f else component.defaultBackgroundOpacity).let { placement ->
                        if (root.optInt("version", 1) == 3) placement.copy(
                            backgroundOpacity = placement.backgroundOpacity * component.defaultBackgroundOpacity) else placement
                    })
                    if (root.optInt("version", 1) == 1) result = result.place(component, result[component].x, result[component].y)
                }
            }
            val media = mutableListOf<OriginalClusterMedia>()
            val array = root.optJSONArray("media")
            for (i in 0 until (array?.length() ?: 0)) {
                val entry = array?.optJSONObject(i) ?: continue
                val id = entry.optString("id"); val uri = entry.optString("uri")
                val type = runCatching { OriginalMediaType.valueOf(entry.optString("type")) }.getOrNull() ?: continue
                if (id.isBlank() || uri.isBlank() || media.any { it.id == id }) continue
                val placement = entry.optJSONObject("placement")?.let { OriginalClusterPlacement.read(it, .35f, .2f, minScale = OriginalClusterMedia.MIN_SCALE, maxScale = OriginalClusterMedia.MAX_SCALE) }
                    ?: OriginalClusterPlacement(x = .35f, y = .2f)
                media += OriginalClusterMedia(id, type, uri, placement, entry.optBoolean("stretchFullScreen", false))
            }
            val savedOrder = root.optJSONArray("layerOrder")?.let { array ->
                buildList { for (index in 0 until array.length()) array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
            }
            return result.copy(media = media, layerOrder = savedOrder ?: emptyList(),
                musicStyle = OriginalMusicStyle.read(root.optJSONObject("musicStyle")))
        }

        private fun normalizeLayerOrder(order: List<String>, media: List<OriginalClusterMedia>): List<String> {
            val valid = buildList {
                add(OriginalClusterComponent.MAP.key)
                OriginalClusterComponent.entries.filterNot { it == OriginalClusterComponent.MAP }.forEach { add(it.key) }
                media.forEach { add(it.id) }
            }
            val preferred = if (order.isEmpty()) buildList {
                add(OriginalClusterComponent.MAP.key)
                media.forEach { add(it.id) }
                OriginalClusterComponent.entries.filterNot { it == OriginalClusterComponent.MAP }.forEach { add(it.key) }
            } else order
            return (preferred.filter { it in valid }.distinct() + valid.filterNot { it in preferred }).toList()
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
