package com.nickbether.pebbletasker.tasker.vars

/**
 * FROZEN CONTRACT (FINAL DESIGN "CONTRACTS TO FREEZE" #1).
 *
 * The stable `%pbl_*` Tasker output-variable names. These flow into user Tasker tasks the moment a
 * plugin ships — RENAMING ANY OF THESE ORPHANS USER PROFILES. Treat this list as append-only.
 * (The one-off `pb_` -> `pbl_` rename predates any public release; profiles built against `%pb_*`
 * must be re-pointed. The `@field:TaskerInputField("pb_...")` keys are a SEPARATE contract that
 * identifies saved config bundles — those stay `pb_` and must not be renamed with these.)
 *
 * Conventions:
 *  - Names are passed to @get:TaskerOutputVariable(...) WITHOUT the leading "%" (the library and
 *    Tasker add it). They MUST be lowercase + start with a lowercase letter (Tasker local-var rule,
 *    enforced by TaskerPlugin.variableNameValid).
 *  - [JSON] is the one structured blob every plugin emits; Tasker 5.12+ reads `%pbl_json.field`.
 *    Document the `.field` sub-paths in each plugin's blurb (Tasker's picker only suggests the
 *    top-level `%pbl_json`, not sub-fields).
 *  - Array outputs use these same names on Array<String>?/List<String>? fields; Tasker renders the
 *    `()` suffix itself — the constant has no parens.
 *  - %err / %errmsg are NOT here: the library auto-declares them, and TaskerPluginResultError sets
 *    them automatically. Do not re-declare them as outputs.
 *
 * "pbl_" prefix is mandatory so every plugin's outputs share one namespace and downstream pickers can
 * be seeded with [ALL].
 */
object PbVars {

    // --- universal ---
    const val JSON = "pbl_json"               // structured blob; %pbl_json.field (Tasker 5.12+)
    const val EVENT_TYPE = "pbl_event_type"   // bridge event type string
    const val SEQ = "pbl_seq"                 // event seq (monotonic per boot)
    const val BOOT_ID = "pbl_boot_id"         // bridge boot id

    // --- watch identity block (1:1 with WatchRef) ---
    const val SERIAL = "pbl_serial"
    const val NAME = "pbl_name"
    const val NICKNAME = "pbl_nickname"
    const val MODEL = "pbl_model"
    const val FW = "pbl_fw"
    const val BATTERY = "pbl_battery"
    const val ADDRESS = "pbl_address"

    // --- battery / connection ---
    const val DIRECTION = "pbl_direction"
    const val REASON = "pbl_reason"
    const val CONNECTED = "pbl_connected"
    const val CONNECTED_COUNT = "pbl_connected_count"

    // --- notifications ---
    const val PKG = "pbl_pkg"
    const val APP_NAME = "pbl_app_name"
    const val TITLE = "pbl_title"
    const val TEXT = "pbl_text"
    const val CHANNEL_ID = "pbl_channel_id"
    const val ACTIONS = "pbl_actions"             // array
    const val ACTIONS_COUNT = "pbl_actions_count"
    const val ACTION = "pbl_action"
    const val ACTION_ID = "pbl_action_id"
    const val REPLY_TEXT = "pbl_reply_text"

    // --- apps / appmessage ---
    const val UUID = "pbl_uuid"
    const val APP_TYPE = "pbl_app_type"
    const val PREV_UUID = "pbl_prev_uuid"
    const val DICT_JSON = "pbl_dict_json"
    const val KEYS = "pbl_keys"                   // array
    const val VALUES = "pbl_values"               // array
    const val RUNNING_APP = "pbl_running_app"
    const val WATCHFACE = "pbl_watchface"
    const val WATCHFACE_UUID = "pbl_watchface_uuid"
    const val WATCHFACE_NAME = "pbl_watchface_name"

    // --- timeline ---
    const val PIN_UUID = "pbl_pin_uuid"

    // --- media / calls ---
    const val MUSIC_ACTION = "pbl_music_action"
    const val CALL_STATE = "pbl_call_state"
    const val CALL_ACTION = "pbl_call_action"
    const val NUMBER = "pbl_number"
    const val CALLER_NAME = "pbl_caller_name"
    const val HANDLED_ON_WATCH = "pbl_handled_on_watch"

    // --- firmware ---
    const val FW_STATUS = "pbl_fw_status"
    const val FW_VERSION = "pbl_fw_version"
    const val FW_PROGRESS = "pbl_fw_progress"
    const val FW_AVAILABLE = "pbl_fw_available"
    const val PROGRESS = "pbl_progress"

    // --- health ---
    const val STEPS_TODAY = "pbl_steps_today"
    const val LATEST_HR = "pbl_latest_hr"
    const val SLEEP_MIN = "pbl_sleep_min"
    const val RESTING_HR = "pbl_resting_hr"
    const val HR_ZONES_JSON = "pbl_hr_zones_json"

    // --- dev / bluetooth / dnd / system ---
    const val DEV_ENABLED = "pbl_dev_enabled"
    const val TRANSPORT = "pbl_transport"
    const val BT_ENABLED = "pbl_bt_enabled"
    const val DND = "pbl_dnd"
    const val ERROR_TYPE = "pbl_error_type"
    const val ERROR_MSG = "pbl_error_msg"
    const val GAP_FROM = "pbl_gap_from"
    const val GAP_TO = "pbl_gap_to"

    // --- action result block (success-with-ok=false model) ---
    const val OK = "pbl_ok"
    const val ERR = "pbl_err"
    const val ERRMSG = "pbl_errmsg"
    const val ITEM_ID = "pbl_item_id"
    const val DELIVERED = "pbl_delivered"
    const val FILE = "pbl_file"
    const val RTT_MS = "pbl_rtt_ms"

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
