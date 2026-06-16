package com.nickbether.pebbletasker.tasker.vars

import android.content.Intent
import android.os.Bundle
import net.dinglisch.android.tasker.TaskerPlugin

/**
 * Reads Tasker's inbound "relevant variables" suggestion list and merges it with the plugin's own
 * upstream outputs, for use by [VariableFieldBinder] (FINAL DESIGN §4.1 manner 3).
 *
 * IMPORTANT (FIX C1): `relevantVariables` on TaskerPluginConfigHelper is a READ-ONLY val, auto-derived
 * from the incoming intent — never assign it. Here we read it directly off the activity intent via
 * TaskerPlugin.getRelevantVariableList, which already returns a non-null (possibly empty) array.
 *
 * A brand-new profile passes NO relevant vars (empty array); the picker dialog then offers only the
 * plugin's own outputs (and the user can always type a %var manually) — never a crash (FIX #7).
 */
object RelevantVars {

    /**
     * Tasker-provided suggestions for this config session. Pass `config.getIntent()?.extras`.
     * Returns a possibly-empty, never-null array of "%"-prefixed variable names.
     */
    fun fromHost(extras: Bundle?): Array<String> =
        runCatching { TaskerPlugin.getRelevantVariableList(extras) }.getOrNull() ?: emptyArray()

    /** Convenience overload taking the activity intent directly. */
    fun fromIntent(intent: Intent?): Array<String> = fromHost(intent?.extras)

    /** True if the host advertised that it supports relevant-variable exchange at all. */
    fun hostSupports(extras: Bundle?): Boolean =
        runCatching { TaskerPlugin.hostSupportsRelevantVariables(extras) }.getOrDefault(false)

    /**
     * The full suggestion set for a field dialog: Tasker's inbound vars first (most relevant to the
     * user's task), then the plugin's own `%pb_*` outputs, de-duplicated, order-preserving.
     */
    fun suggestionsFor(hostVars: Array<String>, includePbVars: Boolean = true): Array<String> {
        if (!includePbVars) return hostVars
        val merged = LinkedHashSet<String>(hostVars.size + PbVars.ALL.size)
        merged.addAll(hostVars)
        merged.addAll(PbVars.ALL)
        return merged.toTypedArray()
    }
}
