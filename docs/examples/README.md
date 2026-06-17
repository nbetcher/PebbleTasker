# Pebble × Tasker — example profiles

Ready-to-import [Tasker](https://tasker.joaoapps.com/) profiles that demonstrate the
**Pebble Tasker plugin** (`com.nickbether.pebbletasker`). Each file is a standard Tasker
XML export (`.prf.xml`) you can import directly.

> These are **teaching examples**. The plugin's `<Bundle>` config keys shown in the XML are
> realistic but illustrative — **Tasker finalizes them when you re-open the plugin config after
> import** (see [Important: re-select the plugin config](#important-re-select-the-plugin-config)).

---

## What's in this folder

| File | Type | What it demonstrates |
|---|---|---|
| `01_disconnect_flash.prf.xml` | Event → Task | **Pebble Watch Disconnected** fires a **Flash** + **Notify** on the phone. Uses `%pb_serial`. |
| `02_low_battery_notify.prf.xml` | Event → Task | **Pebble Battery Level** (below 20%) posts a **Notify** — *"Watch battery low: %pb_battery%"*. Uses `%pb_battery`, `%pb_serial`. |
| `03_send_notification_with_action.prf.xml` | Standalone Task | **Pebble Send Watch Notification** action with title/body/subtitle and an `actions_json` one-button array. No trigger — run it manually or wire it to your own profile. |
| `04_connected_keep_screen_on.prf.xml` | State → Enter/Exit Tasks | **Pebble Watch Connected** keeps the phone display awake while connected (Display Timeout + Keep Device Awake), and restores normal behaviour on disconnect. Uses `%pb_connected`. |

### Plugin pieces these examples touch

| Example | Plugin component | Tasker config intent |
|---|---|---|
| 01 | Watch-disconnected **Event** | `net.dinglisch.android.tasker.ACTION_EDIT_EVENT` |
| 02 | Battery-level **Event** (`.tasker.event.battery.BatteryActivity`) | `net.dinglisch.android.tasker.ACTION_EDIT_EVENT` |
| 03 | Send-notification **Action** (`.tasker.action.sendnotif.SendNotifActivity`) | `com.twofortyfouram.locale.intent.action.EDIT_SETTING` |
| 04 | Watch-connected **State** (`.tasker.state.connected.S1WatchConnectedActivity`) | `com.twofortyfouram.locale.intent.action.EDIT_CONDITION` |

### Output variables

The plugin exposes Pebble data as `%pb_*` Tasker variables. The ones used in these examples:

- `%pb_serial` — the watch serial that produced the event.
- `%pb_battery` — battery percent (integer), from the Battery Level event.
- `%pb_connected` — `true`/`false`, from the Watch Connected state.
- `%pb_json` — the full event/result payload as a JSON string. With Tasker's structured
  output you can drill in, e.g. `%pb_json.watch.battery`.

---

## Prerequisites

1. **Tasker** installed (these files target the modern Tasker 6.x XML format,
   `<TaskerData ... tv="6.6">`).
2. The **Pebble app (forked bridge build)** and the **Pebble Tasker plugin**
   (`com.nickbether.pebbletasker`) installed, with the plugin's onboarding/approval flow
   completed so the bridge will answer it.

If the plugin isn't installed, Tasker will still import the profile but the plugin
event/state/action will show as **unconfigured / unknown plugin** until the app is present.

---

## How to import a `.prf.xml` into Tasker

You can use either method.

### Method A — Import from the Tasker UI (recommended)

1. Put the `.prf.xml` file somewhere on the device (Downloads is fine).
2. Open **Tasker**. Make sure **Beginner Mode is OFF**
   (*≡ / three-dot menu → Preferences → UI → Beginner Mode* unchecked) so the
   import option is visible.
3. On the **Profiles** tab, **long-press the "Profiles" tab title** at the top.
4. Choose **Import**, then browse to and select the `.prf.xml` file.
5. The profile (and its task) appear. Tap the profile to expand it.

> Importing a **standalone task** export (example 03, which has no profile): long-press the
> **"Tasks"** tab title instead and choose **Import**.

### Method B — Drop the file into Tasker's configs folder

1. Copy the `.prf.xml` file into Tasker's user configs directory, typically:
   `…/Android/data/net.dinglisch.android.taskerm/files/configs/`
   (older installs may use `/sdcard/Tasker/configs/`).
2. In Tasker, long-press the **Profiles** (or **Tasks**) tab title → **Import** → the file
   now shows up in that folder for selection.

> The exact configs path varies by Android version and scoped-storage rules. If you can't
> reach it, use **Method A** — it works regardless of where the file lives.

---

## Important: re-select the plugin config

**After importing, open each Pebble plugin entry and re-save its configuration.** This is the
one manual step that makes the examples actually run:

- Tasker binds a plugin action/state/event to the **currently installed plugin version** only
  when you open its config screen and confirm it. On a fresh import the saved `<Bundle>` is a
  snapshot from another device/version, so Tasker may show it as needing attention.
- Re-selecting also lets Tasker normalize/finalize the plugin's bundle keys for your installed
  build — the keys in these XML files are intentionally illustrative.

**Steps for each example:**

1. **01 / 02 (Events):** tap the profile → tap the event context
   (*Pebble Watch Disconnected* / *Pebble Battery Level*) → the plugin config screen opens →
   adjust if you like (e.g. serial, threshold) → tap the **system check-mark / back** to save.
2. **04 (State):** tap the profile → tap the state context (*Pebble Watch Connected*) →
   confirm/save the same way.
3. **03 (Action):** open the task **Send Pebble Notification** → tap the
   **Pebble Send Watch Notification** action → confirm/save its config.

If an entry shows a small **pencil / "configure"** prompt or a warning triangle, that's Tasker
telling you it still needs this re-select step.

Finally, ensure the imported **Profile is enabled** (the toggle on the Profiles tab is on).

---

## Notes & troubleshooting

- **Generic plugin code `1000`.** In the XML, every plugin event/state/action carries
  `<code>1000</code>` (Tasker's generic "Plugin" code). The *specific* Pebble plugin is
  resolved from the two trailing strings: `<Str sr="arg1">` = package
  (`com.nickbether.pebbletasker`) and `<Str sr="arg2">` = the plugin's config-activity class.
- **Variables read like text.** `%pb_battery` etc. are only populated **inside the task that
  the matching profile triggers**. Referencing them elsewhere yields empty values.
- **`actions_json` is a JSON string.** In example 03 the JSON's quotes/brackets are
  XML-escaped (`&quot;`, etc.) inside the `<Bundle>`. Decoded it is:
  `[{"id":"open","title":"Open","type":"generic"}]`. Watch button presses come back via a
  *Pebble Notification Action* event if you build a follow-up profile.
- **Nothing happens on trigger?** Confirm: the plugin app is installed and approved by the
  bridge; the profile is enabled; you completed the re-select step above; and (for background
  reliability) the Pebble app and the plugin are exempt from battery optimization.
- **XML is well-formed and commented.** Each file has a header comment explaining the example
  and inline comments on the non-obvious elements. They are safe to read before importing.
