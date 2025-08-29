package dev.serhiiyaremych.lumina.ui.datelabel

import android.graphics.Path
import android.text.TextPaint

/** Simple cache for TextPaint to avoid recreating for each render */
internal object TextPaintCache {
    private val cache = mutableMapOf<Float, TextPaint>()
    fun getOrCreate(fontSize: Float): TextPaint = cache.getOrPut(fontSize) {
        TextPaint().apply {
            textSize = fontSize
            isFakeBoldText = true
        }
    }
}

internal fun textToPath(text: String, fontSize: Float): Path {
    val tp = TextPaintCache.getOrCreate(fontSize)
    val path = Path()
    tp.getTextPath(text, 0, text.length, 0f, 0f, path)
    return path
}

