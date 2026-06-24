package com.shrivatsav.monomail.ui.theme
import android.util.LruCache
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.shrivatsav.monomail.R
private val fontFamilyCache = LruCache<String, FontFamily>(10)
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
val GoogleSansFamily = googleSansFlex(0f)
val GoogleSansRoundedFamily = googleSansFlex(100f)
private fun variableFontFamily(regularResId: Int, italicResId: Int? = null): FontFamily {
    val key = "vf_${regularResId}_$italicResId"
    fontFamilyCache.get(key)?.let { return it }
    val fonts = mutableListOf<Font>()
    for (weight in 100..900 step 100) {
        fonts.add(Font(regularResId, FontWeight(weight), FontStyle.Normal))
        if (italicResId != null) {
            fonts.add(Font(italicResId, FontWeight(weight), FontStyle.Italic))
        }
    }
    val family = FontFamily(fonts)
    fontFamilyCache.put(key, family)
    return family
}
val InterFamily = variableFontFamily(R.font.inter, R.font.inter_italic)
val ManropeFamily = variableFontFamily(R.font.manrope)
val SpaceGroteskFamily = variableFontFamily(R.font.space_grotesk)
val IBMPlexSansFamily = variableFontFamily(R.font.ibm_plex_sans, R.font.ibm_plex_sans_italic)
