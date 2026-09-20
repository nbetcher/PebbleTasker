# September 2026 automation compatibility

Update the plugin and Pebble host together. AppMessage event setup now requires the host capability `appmessages.replace_subscriptions`, so an older host is rejected at configuration time with an update message rather than silently retaining stale subscriptions.

The plugin replaces its complete desired AppMessage set on readiness or host initialization. Copied/imported opaque IDs no longer replace different configurations. When changing an existing app/watch/ownership configuration, choose **Replace previous** for an edit or **Keep both** if another profile still uses the previous settings. Cancel leaves the previous subscription intact. Unchanged settings keep their ID. Unknown watch selectors are deferred while other subscriptions are installed; the desired set retries on initialization/readiness and watch connection.

For deleted profiles, use **Diagnostics → Clear AppMessage subscriptions** after removing the unwanted Tasker profiles. Active profiles can request their subscriptions again through Tasker's initialization queries. There is no reliable per-profile deletion callback; cleanup remains explicit.

Saved call filters from older profiles/backups are normalized automatically (`RingingCall` to `ringing`, and likewise dialing, active, holding, and ended). No manual resave is required.

Pebble's body-only notification redaction preserves the title output while withholding body/text. Disabling all notification content removes both. The new `title_shared` flag is consumed independently from `redacted`.

Denied/pending condition diagnostics use a local decision ID, so a bridge reconnect no longer invalidates them by itself. Actual watch events retain authorization checks. Conditions have no native Tasker action-error channel; configure a **Pebble Bridge/Watch Error** event task for notifications. Actions use Tasker's native error result for access denial.

Rejected command policy and server command timeouts preserve healthy event listeners. Genuine transport/session failures trigger bounded recovery. An action timeout still means the remote operation may have completed; do not blindly retry side effects.

Queued calls are checked again before remote execution, preventing a stopped or replaced plugin session from starting an obsolete command. Pebble also revalidates grants at command dispatch. These checks cannot undo a backend operation that has already started.

Condition service and broadcast requests share one bounded serial worker through SDK evaluation and result handoff. The queue budget is eight seconds including waiting; expired/overflow requests return Unknown. The adapter uses the pinned Tasker SDK 0.4.10 JVM result method; revalidate it when upgrading that dependency. Legacy exported SDK condition components are removed to prevent bypassing ordering. Hosts caching those old explicit component names must rediscover the plugin after updating (reopen/reselect the condition if necessary).

Transient recovery preserves pending event payloads only after revalidating the same server boot and authorization revision. Revocation, privacy/grant changes, or a host without the new `authorityId` proof expire older payloads. Replay is paged below the Binder transaction limit and drains without requiring another live event. Cache persistence coalesces obsolete snapshots under storage backpressure while retaining the final cursor and revocation.

The four follow-up findings are repaired and covered by regressions. This is not a device qualification: installed Tasker task scheduling, upgrade discovery, cross-process IPC, backup import/edit UI, consent prompts and rapid physical watch reconnects still need on-device verification. The plugin preserves query arrival/evaluation/result order; it cannot control Tasker's task scheduler or reconstruct transitions absent from upstream collectors.
