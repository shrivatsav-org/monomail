package com.shrivatsav.monomail.ui.screens.inbox
import com.shrivatsav.monomail.data.model.EmailThread
import java.util.Calendar
sealed class InboxDisplayItem {
    abstract val key: String
    data class DateHeader(val title: String) : InboxDisplayItem() {
        override val key: String get() = "header_$title"
    }
    data class GroupHeader(
        val groupName: String,
        val count: Int,
        val unreadCount: Int,
        val latestDate: Long,
        val isExpanded: Boolean,
        val avatarUrl: String?
    ) : InboxDisplayItem() {
        override val key: String get() = "group_$groupName"
    }
    data class SingleThread(val thread: EmailThread) : InboxDisplayItem() {
        override val key: String get() = thread.threadId
    }
    data class NestedThread(val thread: EmailThread, val groupName: String) : InboxDisplayItem() {
        override val key: String get() = "${groupName}_${thread.threadId}"
    }
}
sealed class TempItem {
    abstract val date: Long
    data class Single(val thread: EmailThread) : TempItem() {
        override val date: Long get() = thread.date
    }
    data class Group(val name: String, val threads: List<EmailThread>) : TempItem() {
        override val date: Long get() = threads.maxOf { it.date }
    }
}
data class InboxStructure(
    val groups: List<TempItem.Group>,
    val singles: List<TempItem.Single>
)
fun computeInboxStructure(
    threads: List<EmailThread>,
    useGrouping: Boolean,
    recentOnly: Boolean
): InboxStructure {
    if (threads.isEmpty()) return InboxStructure(emptyList(), emptyList())
    if (!useGrouping) {
        val sortedSingles = threads.sortedByDescending { it.date }.map { TempItem.Single(it) }
        return InboxStructure(emptyList(), sortedSingles)
    }
    val MIN_GROUP_SIZE = 3
    val groupingRules = mapOf(
        "Reddit" to listOf("reddit.com", "redditmail.com"),
        "GitHub" to listOf("github.com"),
        "Twitter / X" to listOf("twitter.com", "x.com"),
        "LinkedIn" to listOf("linkedin.com"),
        "Google" to listOf("google.com"),
        "Amazon" to listOf("amazon.com", "amazon.in")
    )
    val now = System.currentTimeMillis()
    val oneDayMillis = 24L * 60 * 60 * 1000
    val threeDaysMillis = 3L * oneDayMillis
    val groupedThreadsMap = mutableMapOf<String, MutableList<EmailThread>>()
    val remainingThreads = mutableListOf<EmailThread>()
    for (thread in threads) {
        var matchedGroup: String? = null
        val isWithin24Hrs = thread.date >= (now - oneDayMillis)
        val isWithin3Days = thread.date >= (now - threeDaysMillis)
        val canGroup = if (recentOnly) isWithin24Hrs else isWithin3Days
        if (canGroup) {
            for ((groupName, domains) in groupingRules) {
                if (domains.any { thread.fromEmail.contains(it, ignoreCase = true) }) {
                    matchedGroup = groupName
                    break
                }
            }
        }
        if (matchedGroup != null) {
            groupedThreadsMap.getOrPut(matchedGroup) { mutableListOf() }.add(thread)
        } else {
            remainingThreads.add(thread)
        }
    }
    val groups = mutableListOf<TempItem.Group>()
    val singles = mutableListOf<TempItem.Single>()
    singles.addAll(remainingThreads.map { TempItem.Single(it) })
    for ((groupName, groupThreads) in groupedThreadsMap) {
        if (groupThreads.size >= MIN_GROUP_SIZE) {
            groups.add(TempItem.Group(groupName, groupThreads.sortedByDescending { it.date }))
        } else {
            singles.addAll(groupThreads.map { TempItem.Single(it) })
        }
    }
    groups.sortByDescending { it.date }
    singles.sortByDescending { it.date }
    return InboxStructure(groups, singles)
}
fun flattenDisplayItems(
    structure: InboxStructure,
    expandedGroups: Set<String>
): List<InboxDisplayItem> {
    val displayItems = mutableListOf<InboxDisplayItem>()
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    for (group in structure.groups) {
        val unreadCount = group.threads.count { !it.isRead }
        val allUnread = unreadCount == group.threads.size
        val isExpanded = expandedGroups.contains(group.name) || allUnread
        displayItems.add(
            InboxDisplayItem.GroupHeader(
                groupName = group.name,
                count = group.threads.size,
                unreadCount = unreadCount,
                latestDate = group.date,
                isExpanded = isExpanded,
                avatarUrl = null
            )
        )
        if (isExpanded) {
            for (thread in group.threads) {
                displayItems.add(InboxDisplayItem.NestedThread(thread, group.name))
            }
        }
    }
    var currentHeader = ""
    for (single in structure.singles) {
        val cal = Calendar.getInstance().apply { timeInMillis = single.date }
        val dateHeader = when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
            else -> "Earlier"
        }
        if (dateHeader != currentHeader) {
            displayItems.add(InboxDisplayItem.DateHeader(dateHeader))
            currentHeader = dateHeader
        }
        displayItems.add(InboxDisplayItem.SingleThread(single.thread))
    }
    return displayItems
}
fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
