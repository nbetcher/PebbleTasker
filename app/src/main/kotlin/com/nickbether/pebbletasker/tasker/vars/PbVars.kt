package com.nickbether.pebbletasker.tasker.vars

/**
 * FROZEN CONTRACT (FINAL DESIGN "CONTRACTS TO FREEZE" #1).
 *
 * The stable `%pb_*` Tasker output-variable names. These flow into user Tasker tasks the moment a
 * plugin ships — RENAMING ANY OF THESE ORPHANS USER PROFILES. Treat this list as append-only.
 *
 * Conventions:
 *  - Names are passed to @get:TaskerOutputVariable(...) WITHOUT the leading "%" (the library and
 *    Tasker add it). They MUST be lowercase + start with a lowercase letter (Tasker local-var rule,
 *    enforced by TaskerPlugin.variableNameValid).
 *  - [JSON] is the one structured blob every plugin emits; Tasker 5.12+ reads `%pb_json.field`.
 *    Document the `.field` sub-paths in each plugin's blurb (Tasker's picker only suggests the
 *    top-level `%pb_json`, not sub-fields).
 *  - Array outputs use these same names on Array<String>?/List<String>? fields; Tasker renders the
 *    `()` suffix itself — the constant has no parens.
 *  - %err / %errmsg are NOT here: the library auto-declares them, and TaskerPluginResultError sets
 *    them automatically. Do not re-declare them as outputs.
 *
 * "pb_" prefix is mandatory so every plugin's outputs share one namespace and downstream pickers can
 * be seeded with [ALL].
 */
object PbVars {

    // --- universal ---
    const val JSON = "pb_json"               // structured blob; %pb_json.field (Tasker 5.12+)
    const val EVENT_TYPE = "pb_event_type"   // bridge event type string
    const val SEQ = "pb_seq"                 // event seq (monotonic per boot)
    const val BOOT_ID = "pb_boot_id"         // bridge boot id

    // --- watch identity block (1:1 with WatchRef) ---
    const val SERIAL = "pb_serial"
    const val NAME = "pb_name"
    const val NICKNAME = "pb_nickname"
    const val MODEL = "pb_model"
    const val FW = "pb_fw"
    const val BATTERY = "pb_battery"
    const val ADDRESS = "pb_address"

    // --- battery / connection ---
    const val DIRECTION = "pb_direction"
    const val REASON = "pb_reason"
    const val CONNECTED = "pb_connected"
    const val CONNECTED_COUNT = "pb_connected_count"

    // --- notifications ---
    const val PKG = "pb_pkg"
    const val APP_NAME = "pb_app_name"
    const val TITLE = "pb_title"
    const val TEXT = "pb_text"
    const val CHANNEL_ID = "pb_channel_id"
    const val ACTIONS = "pb_actions"             // array
    const val ACTIONS_COUNT = "pb_actions_count"
    const val ACTION = "pb_action"
    const val ACTION_ID = "pb_action_id"
    const val REPLY_TEXT = "pb_reply_text"

    // --- apps / appmessage ---
    const val UUID = "pb_uuid"
    const val APP_TYPE = "pb_app_type"
    const val PREV_UUID = "pb_prev_uuid"
    const val DICT_JSON = "pb_dict_json"
    const val KEYS = "pb_keys"                   // array
    const val VALUES = "pb_values"               // array
    const val RUNNING_APP = "pb_running_app"
    const val WATCHFACE = "pb_watchface"
    const val WATCHFACE_UUID = "pb_watchface_uuid"
    const val WATCHFACE_NAME = "pb_watchface_name"

    // --- timeline ---
    const val PIN_UUID = "pb_pin_uuid"

    // --- media / calls ---
    const val MUSIC_ACTION = "pb_music_action"
    const val CALL_STATE = "pb_call_state"
    const val CALL_ACTION = "pb_call_action"
    const val NUMBER = "pb_number"
    const val CALLER_NAME = "pb_caller_name"
    const val HANDLED_ON_WATCH = "pb_handled_on_watch"

    // --- firmware ---
    const val FW_STATUS = "pb_fw_status"
    const val FW_VERSION = "pb_fw_version"
    const val FW_PROGRESS = "pb_fw_progress"
    const val FW_AVAILABLE = "pb_fw_available"
    const val PROGRESS = "pb_progress"

    // --- health ---
    const val STEPS_TODAY = "pb_steps_today"
    const val LATEST_HR = "pb_latest_hr"
    const val SLEEP_MIN = "pb_sleep_min"
    const val RESTING_HR = "pb_resting_hr"
    const val HR_ZONES_JSON = "pb_hr_zones_json"

    // --- dev / bluetooth / dnd / system ---
    const val DEV_ENABLED = "pb_dev_enabled"
    const val TRANSPORT = "pb_transport"
    const val BT_ENABLED = "pb_bt_enabled"
    const val DND = "pb_dnd"
    const val ERROR_TYPE = "pb_error_type"
    const val ERROR_MSG = "pb_error_msg"
    const val GAP_FROM = "pb_gap_from"
    const val GAP_TO = "pb_gap_to"

    // --- action result block (success-with-ok=false model) ---
    const val OK = "pb_ok"
    const val ERR = "pb_err"
    const val ERRMSG = "pb_errmsg"
    const val ITEM_ID = "pb_item_id"
    const val DELIVERED = "pb_delivered"
    const val FILE = "pb_file"
    const val RTT_MS = "pb_rtt_ms"

    /**
     * Every output name, "%"-prefixed, for seeding downstream variable pickers via a config/helper's
     * `relevantVariables` override (FINAL DESIGN §4.1 manner 4). Built reflectively from the consts
     * above so it can never drift out of sync.
     */
    val ALL: Array<String> by lazy {
        PbVars::class.java.declaredFields
            .filter { it.type == String::class.java }
            .mapNotNull { f -> f.isAccessible = true; (f.get(this) as? String)?.let { "%$it" } }
            .distinct()
            .sorted()
            .toTypedArray()
    }

    /** Add a "%" prefix for use in a relevant-variable list. */
    fun pct(name: String): String = if (name.startsWith("%")) name else "%$name"
}
