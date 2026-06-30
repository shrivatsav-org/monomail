package com.shrivatsav.monomail.ui.theme
import android.util.LruCache
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.shrivatsav.monomail.R
private val fontFamilyCache = LruCache<String, FontFamily>(10)
@OptIn(ExperimentalTextApi::class)
fun googleSansFlex(rond: Float = 0f): FontFamily {
    val key = "GoogleSansFlex_rond_$rond"
    fontFamilyCache.get(key)?.let { return it }
    val fonts = mutableListOf<Font>()
    for (weight in 100..900 step 100) {
        fonts.add(
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight(weight),
                style = FontStyle.Normal,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.Setting("ROND", rond)
                )
            )
        )
        fonts.add(
            Font(
                resId = R.font.google_sans_flex,
                weight = FontWeight(weight),
                style = FontStyle.Italic,
                variationSettings = FontVariation.Settings(
                    FontVariation.weight(weight),
                    FontVariation.italic(1f),
                    FontVariation.Setting("ROND", rond)
                )
            )
        )
    }
    val family = FontFamily(fonts)
    fontFamilyCache.put(key, family)
    return family
}
val GoogleSansFamily by lazy { googleSansFlex(0f) }
val GoogleSansRoundedFamily by lazy { googleSansFlex(100f) }
