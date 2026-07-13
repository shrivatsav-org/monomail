package com.shrivatsav.monomail.feature.inbox
import com.shrivatsav.monomail.data.model.EmailThread
import java.util.Calendar
sealed class InboxDisplayItem {
    abstract val key: String
    data class DateHeader(val title: String, val tab: String = "") : InboxDisplayItem() {
        override val key: String get() = "${tab}_header_$title"
    }
    data class GroupHeader(
        val groupName: String,
        val count: Int,
        val unreadCount: Int,
        val latestDate: Long,
        val isExpanded: Boolean,
        val avatarUrl: String?,
        val tab: String = ""
    ) : InboxDisplayItem() {
        override val key: String get() = "${tab}_group_$groupName"
    }
    data class SingleThread(val thread: EmailThread, val tab: String = "") : InboxDisplayItem() {
        override val key: String get() = "${tab}_${thread.threadId}"
    }
    data class NestedThread(val thread: EmailThread, val groupName: String, val tab: String = "") : InboxDisplayItem() {
        override val key: String get() = "${tab}_${groupName}_${thread.threadId}"
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
    val minGroupSize = 3
    val now = System.currentTimeMillis()
    val oneDayMillis = 24L * 60 * 60 * 1000
    val threeDaysMillis = 3L * oneDayMillis
    val groupedThreadsMap = mutableMapOf<String, MutableList<EmailThread>>()
    val remainingThreads = mutableListOf<EmailThread>()
    for (thread in threads) {
        val isWithin24Hrs = thread.date >= (now - oneDayMillis)
        val isWithin3Days = thread.date >= (now - threeDaysMillis)
        val canGroup = if (recentOnly) isWithin24Hrs else isWithin3Days
        if (canGroup) {
            val senderName = extractSenderName(thread.from)
            groupedThreadsMap.getOrPut(senderName) { mutableListOf() }.add(thread)
        } else {
            remainingThreads.add(thread)
        }
    }
    val groups = mutableListOf<TempItem.Group>()
    val singles = mutableListOf<TempItem.Single>()
    singles.addAll(remainingThreads.map { TempItem.Single(it) })
    for ((groupName, groupThreads: List<EmailThread>) in groupedThreadsMap) {
        if (groupThreads.size >= minGroupSize) {
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
    expandedGroups: Set<String>,
    tabPrefix: String = ""
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
                avatarUrl = null,
                tab = tabPrefix
            )
        )
        if (isExpanded) {
            for (thread in group.threads) {
                displayItems.add(InboxDisplayItem.NestedThread(thread, group.name, tab = tabPrefix))
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
            displayItems.add(InboxDisplayItem.DateHeader(dateHeader, tab = tabPrefix))
            currentHeader = dateHeader
        }
        displayItems.add(InboxDisplayItem.SingleThread(single.thread, tab = tabPrefix))
    }
    return displayItems
}
fun isSameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)

private val senderNameRegex = Regex("""^"?([^"<]+?)"?\s*<""")
private fun extractSenderName(from: String): String {
    val nameMatch = senderNameRegex.find(from)
    val name = nameMatch?.groupValues?.get(1)?.trim() ?: from.substringBefore("<").trim()
    return name.takeIf { it.isNotBlank() } ?: "Unknown Sender"
}
