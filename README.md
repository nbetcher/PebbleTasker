# Pebble × Tasker Plugin — `com.nickbether.pebbletasker`

A first-class Android **Tasker plugin** for Pebble watches. It binds to the authenticated
**bridge** AIDL service embedded in the forked Pebble app (`coredevices.coreapp`) and exposes
Tasker **event / state / action** plugins with full Tasker variable support, themed with the
**Neon Grid** Material-3 dark theme.

This is a **standalone Gradle project** (its own wrapper, `settings.gradle.kts`,
`local.properties`). It is independent of the Pebble app and the NeonGrid repo.

> **STATUS: SCAFFOLD ONLY.** This commit creates the build foundation, theme, manifest
> skeleton, AIDL, and package tree. No feature plugins are implemented yet. The authoritative
> build spec is the **FINAL MASTER DESIGN** (kept with the project planning docs); implementers
> follow its §0 corrections and §1–§8 structure verbatim.

---

## Build

- **Toolchain:** AGP **8.13.2**, Gradle **8.14.4** (wrapper pinned), Kotlin **2.0.21**, JDK **17**.
- **SDK:** `compileSdk=36`, `targetSdk=36`, `minSdk=26`.
- **App id / package:** `com.nickbether.pebbletasker`.
- **Build host:** WSL `openSUSE-Leap-15.6`, Android SDK at `/home/nbetcher/android-sdk`
  (`local.properties` → `sdk.dir`). **A human verifies the build in WSL** — do not run
  Gradle from this scaffolding environment.

```bash
# from the project root (path has a space — quote it):
./gradlew :app:assembleDebug
```

A **debug `signingConfig`** is wired (standard `~/.android/debug.keystore`) so the project
produces an installable debug APK without external key setup. The release variant is also
debug-signed for now — swap in a real release keystore before publishing. The plugin is
**independently signed**; per FINAL DESIGN §3.6 / FIX D2 the signer must **never rotate the
key without re-consent** (the bridge TOFU-pins the plugin cert with exact `contentEquals`)
and the manifest must **never** set `android:sharedUserId`.

---

## What's in this scaffold

| Area | Files |
|---|---|
| Gradle | `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `local.properties`, wrapper (`gradlew`, `gradlew.bat`, `gradle/wrapper/*`), `.gitignore` |
| `:app` | `app/build.gradle.kts`, `app/proguard-rules.pro` |
| Manifest | `app/src/main/AndroidManifest.xml` (application + UI activities + WakeReceiver + library-patch notes + placeholders for the 39 plugin config activities) |
| AIDL | `app/src/main/aidl/coredevices/coreapp/automation/IBridgeService.aidl` (bridge verbatim **+ `execute()` appended last**), `IBridgeEventListener.aidl` (verbatim) |
| Theme | `app/src/main/res/{values,color,drawable}/` — Neon Grid theme copied from the NeonGrid repo (sample-only files excluded) |
| Icons / strings | adaptive launcher icon (placeholder, Neon-Grid cyan-on-void), `ic_insert_variable`, `strings.xml` (incl. the two mandatory FGS justification strings) |
| Code | `PebbleTaskerApp.kt` (stub) + the full package tree under `com.nickbether.pebbletasker` (each package has a self-documenting `.gitkeep` marker) |

### AIDL note
The plugin ships a **byte-identical** copy of the bridge AIDL package so the generated
`Stub`/`Proxy` match the bridge process. The bridge's running AIDL has **5 methods, no
`execute()`** — `execute(in String clientToken, in String commandJson)` is appended **strictly
last** (transaction code 6) so codes 1–5 stay stable. Until the bridge implements it, the
plugin's `CommandSender` maps the absent transaction to `UNSUPPORTED_COMMAND` via a
`RemoteException` backstop (FINAL DESIGN §3.4 / §6).

### Theme note
Copied from `NeonGrid/android/theme/src/main/res/`: `values/{colors,attrs,themes,styles,type,shapes}.xml`
(**`arrays.xml` excluded** — sample-only), all 31 `color/` CSLs, and the **theme drawables only**
(checkbox/radio/spinner/popup/window/star chains + `ng_grid_overlay`/`ng_divider_gradient`).
Excluded: `layout/ng_showcase.xml`, `arrays.xml`, and all sample drawables
(`ng_gradient_*`, `ng_accent_line_*`, `ng_status_dot_green`, etc.). The copied set is
**self-contained** — every `@drawable`/`@color` reference resolves within the copied files
(verified during scaffolding).

---

## What's NOT here (next steps for implementers)

1. **bridge/** — `BridgeClient`, `BridgeConnection`, `BridgeListener`, `BridgeSession`,
   envelope-aware `BridgeCodec`, `CertPinner`, `CommandSender`, `WakeReceiver`, `BridgeResult`,
   and the `dto/` serialization mirrors (FINAL DESIGN §3).
2. **cache/** — `EventCache`, `EventRouter`, `CachedEvent`.
3. **tasker/base + vars** — `PebbleConfigActivity` (AppCompatActivity re-impl of
   `TaskerPluginConfig`), runner/helper bases, `VariableFieldBinder`, `PbVars`, `ErrCodes`.
4. **tasker/event (15) · state (6) · action (18)** — the plugin catalog + their manifest
   config-activity entries (FINAL DESIGN §2 / §8).
5. **ui/** — Main, Onboarding (master-switch-first), Diagnostics, ConsentGuidance, pickers.
6. **util/** — TimeoutScope, SecureWindow, BatteryOpt.
7. `res/layout/*` for every config/UI activity + `view_tasker_var_field.xml`.

The bridge now advertises the full capability set — `events.core`, `events.notifications`,
`events.health`, `commands.core`, `commands.sensitive`, `commands.dangerous`, `appmessages` — and
serves `execute`, so the whole event catalog (E1–E15) and all command tiers are exercisable. Event
delivery is bounded by the host app's master switch + per-category consent toggles (notification
content is off + redacted by default); command **tier** is granted per client at approval, and the
DANGEROUS tier additionally needs the app's "allow dangerous commands" toggle. Capability gating
remains the forward-compat mechanism — anything the bridge doesn't advertise stays dark, without
orphaning saved configs (the `%pb_*` names and input-field keys are a frozen, append-only contract).
