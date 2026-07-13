package com.shrivatsav.monomail.util

/** Strips the first "Re:", "Fwd:", or "Fw:" prefix from an email subject (case-insensitive). */
fun String.cleanSubject(): String {
    return replaceFirst(Regex("^(Re|Fwd|Fw):\\s*", RegexOption.IGNORE_CASE), "")
}
