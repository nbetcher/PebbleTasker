package com.nickbether.pebbletasker.cache

import android.app.Activity
import android.content.Context
import com.joaomgcd.taskerpluginlibrary.extensions.requestQuery
import com.nickbether.pebbletasker.log.PLog
import java.util.concurrent.ConcurrentHashMap

/** Separate edge delivery (immutable SDK updates) from current-state refresh. */
object EventRouter {

    /** event type -> set of config activity classes to requestQuery. */
    private val stateActivities = ConcurrentHashMap.newKeySet<Class<out Activity>>()

    private val routes = ConcurrentHashMap<String, MutableSet<Class<out Activity>>>()

    /** Register [configActivity] to be queried whenever an event of [eventType] is ingested. */
    fun register(eventType: String, configActivity: Class<out Activity>) {
        routes.getOrPut(eventType) { ConcurrentHashMap.newKeySet() }.add(configActivity)
    }

    fun registerState(configActivity: Class<out Activity>, vararg eventTypes: String) {
        stateActivities.add(configActivity)
        register(configActivity, *eventTypes)
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
        if (routedEvents.any { it.type == "watch.connected" })
            com.nickbether.pebbletasker.tasker.event.appmsg.AppMessageSubscriptions.requestRestore(context.applicationContext)
        for (e in routedEvents) {
            for (activity in routes[e.type].orEmpty()) {
                runCatching { activity.requestQuery(context, if (activity in stateActivities) null else EventDelivery.from(e, EventCache.get(context).deliveryEpoch)) }
                    .onFailure { PLog.w(it) { "router: failed ${activity.simpleName}" } }
            }
        }
    }

    /** Trigger a query for every config activity registered against [eventType]. */
    fun route(context: Context, eventType: String) {
        val consumers = routes[eventType]?.filter { it in stateActivities }
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

    /** Handshakes and recovery refresh current states only, never historical event profiles. */
    fun requestQueryAll(context: Context) {
        for (activity in stateActivities) {
            runCatching { activity.requestQuery(context) }
                .onFailure { PLog.w(it) { "router: state refresh failed ${activity.simpleName}" } }
        }
    }
}
