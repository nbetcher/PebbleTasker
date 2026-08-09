package com.nickbether.pebbletasker.cache

import android.app.Activity
import android.content.Context
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.nickbether.pebbletasker.log.PLog
import java.util.concurrent.ConcurrentHashMap

/**
 * Fan-out from an ingested event `type` to the Tasker config activities that should re-evaluate
 * (FINAL DESIGN §3.7).
 *
 * Feature-plugin implementers REGISTER their config activity class against the bridge event type(s)
 * that should trigger it (both event plugins and the state plugins backed by that event). When
 * [EventCache] reports newly-routed events, [routeAll] calls Tasker's `requestQuery` for every
 * registered activity, so matching profiles evaluate immediately — no polling.
 *
 * Registration is a simple multimap so the same event type can drive several plugins (e.g.
 * `watch.connected` drives both the E1 event and the S1 state). The Tasker pass-through `update`
 * is intentionally omitted by default: runners read [EventCache] as the source of truth, which is
 * reliable regardless of host pass-through support (FINAL DESIGN §0 FIX C9). Implementers MAY pass
 * an `update` payload when [com.joaomgcd.taskerpluginlibrary.config.HostCapabilities].event
 * .supportsPassThroughData is true, but the cache path must always work.
 *
 * Dedupe: [EventCache] only emits NEW (bootId,seq) events, so routing here is already edge-correct.
 */
object EventRouter {

    /** event type -> set of config activity classes to requestQuery. */
    private val routes = ConcurrentHashMap<String, MutableSet<Class<out Activity>>>()

    /** Register [configActivity] to be queried whenever an event of [eventType] is ingested. */
    fun register(eventType: String, configActivity: Class<out Activity>) {
        routes.getOrPut(eventType) { ConcurrentHashMap.newKeySet() }.add(configActivity)
    }

    /** Register one activity for several event types at once. */
    fun register(configActivity: Class<out Activity>, vararg eventTypes: String) {
        eventTypes.forEach { register(it, configActivity) }
    }

    fun unregister(eventType: String, configActivity: Class<out Activity>) {
        routes[eventType]?.remove(configActivity)
    }

    /** All distinct event types that currently have at least one registered consumer. */
    fun registeredTypes(): Set<String> = routes.keys.toSet()

    /**
     * Route every event in [routedEvents] (already deduped/new by [EventCache]) to Tasker.
     * Safe to call from any thread; `requestQuery` dispatches via the Android intent system.
     */
    fun routeAll(context: Context, routedEvents: List<CachedEvent>) {
        if (routedEvents.isEmpty()) return
        for (e in routedEvents) {
            route(context, e.type)
        }
    }

    /** Trigger a query for every config activity registered against [eventType]. */
    fun route(context: Context, eventType: String) {
        val consumers = routes[eventType]
        if (consumers.isNullOrEmpty()) {
            PLog.d { "router: no consumers for '$eventType'" }
            return
        }
        PLog.i { "router: requestQuery '$eventType' -> ${consumers.size} consumer(s): ${consumers.map { it.simpleName }}" }
        for (activity in consumers) {
            runCatching { activity.requestQuery(context) }
                .onFailure { PLog.w(it) { "router: requestQuery failed for ${activity.simpleName}" } }
        }
    }

    /**
     * Re-query EVERY registered condition once. Called when the bridge becomes Ready so states/events
     * reflect the CURRENT bridge snapshot immediately — crucial when the change that would normally
     * push them (e.g. the watch connecting) happened BEFORE this session existed, so no event will ever
     * arrive to re-query them. A requestQuery for a condition not used in any profile is a harmless no-op.
     */
    fun requestQueryAll(context: Context) {
        val activities = routes.values.flatten().toHashSet()
        PLog.i { "router: requestQueryAll -> ${activities.size} distinct condition(s): ${activities.map { it.simpleName }}" }
        for (activity in activities) {
            runCatching { activity.requestQuery(context) }
                .onFailure { PLog.w(it) { "router: requestQueryAll failed for ${activity.simpleName}" } }
        }
    }
}
