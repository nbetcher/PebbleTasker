package com.nickbether.pebbletasker.tasker.event

/**
 * Small filter-matching helpers shared by the COLLECTOR event runners (E3, E5-E15).
 *
 * All filters are blank-tolerant: a blank/empty filter value means "match any". This matches the
 * design's convention that an unset filter field is a wildcard.
 */
object FilterMatch {

    /** Equality (trimmed, case-insensitive), or true when [filter] is blank. */
    fun eq(filter: String?, actual: String?): Boolean {
        if (filter.isNullOrBlank()) return true
        return filter.trim().equals(actual?.trim(), ignoreCase = true)
    }

    /** Substring containment (case-insensitive), or true when [filter] is blank. */
    fun contains(filter: String?, actual: String?): Boolean {
        if (filter.isNullOrBlank()) return true
        if (actual == null) return false
        return actual.contains(filter.trim(), ignoreCase = true)
    }
}
