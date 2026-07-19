package com.shrivatsav.monomail.core.data.repository

enum class SearchField(val displayName: String) {
    ALL("All"),
    SUBJECT("Subject"),
    BODY("Body"),
    FROM("From"),
    TO("To")
}
