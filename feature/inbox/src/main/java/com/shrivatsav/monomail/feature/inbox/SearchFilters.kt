package com.shrivatsav.monomail.feature.inbox

import com.shrivatsav.monomail.core.data.repository.SearchField

data class SearchFilters(
    val query: String = "",
    val searchField: SearchField = SearchField.ALL,
    val dateFrom: Long? = null,
    val dateTo: Long? = null,
    val hasAttachments: Boolean = false
) {
    val isEmpty: Boolean get() = query.isBlank() && dateFrom == null && dateTo == null && !hasAttachments
}
