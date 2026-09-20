# PebbleTasker

**Make your watch part of your Tasker automations.** Version **0.9.0** connects Tasker to the automation bridge in the companion app.

## Support development

**[Buy me a coffee](https://buymeacoffee.com/nbetcher)** — help support development, maintenance and testing.

[Download PebbleTasker](https://github.com/nbetcher/PebbleTasker/releases) · [Required companion app](https://github.com/nbetcher/mobileapp) · [Companion downloads](https://github.com/nbetcher/mobileapp/releases) · [Builds](https://github.com/nbetcher/PebbleTasker/actions)

## How it works

Install both this plugin and the [forked companion app](https://github.com/nbetcher/mobileapp). Tasker talks to the plugin; the plugin binds to the companion's authenticated local service. The companion handles the watch connection and asks you to approve automation access. The standard companion app does not include this bridge.

Profiles use **events** for individual changes and **states** for current conditions. Tasks use **actions** to send commands. Inputs support Tasker variables; outputs use the `%pbl_*` namespace.

## Features

- Watch connection, disconnection and battery events.
- Developer connection, firmware, running-app and watchface information where supported.
- Notification, call, media, health and error events, subject to companion permissions.
- Watch selection, app/watchface launching, ping responses and watch information.
- Notifications, preferences, quick launch and developer-connection actions.
- Typed AppMessage dictionaries and per-watch subscriptions for watchapp integrations.
- Explicit consent, per-category access, command tiers and notification-content privacy.
- Ordered condition evaluation, bounded event replay and recovery after transient binding failures.
- Subscription restoration when Tasker initializes restored profiles.

Availability depends on the companion's advertised capabilities. Unsupported configurations are rejected rather than silently accepted. Dangerous commands require a separate companion toggle.

## Get started

1. Use Android 8.0 or newer, with Tasker installed.
2. Install the matching [companion fork](https://github.com/nbetcher/mobileapp/releases) and connect your watch there.
3. Install the **release** PebbleTasker APK from [Releases](https://github.com/nbetcher/PebbleTasker/releases).
4. Open PebbleTasker, then create a Tasker profile or action using one of its plugin entries.
5. Review the access request in the companion app. Approve the categories and command tier you need, then enable its automation master switch.
6. Start with a simple watch-connected or watch-disconnected event and a Tasker Flash action displaying the provided watch variables.

An ignored request remains pending without repeated alerts. A denied request stays denied until you reconsider it in the companion. Actions report access failures through Tasker's error result. Conditions cannot return native action errors; configure a **Pebble Bridge/Watch Error** event to handle those failures.

For a current condition, use a state profile. For individual transitions, use an event profile. Tasker controls execution of the resulting tasks; very rapid changes are not a promise of exactly-once task execution.

## AppMessage profiles and upgrades

Update the companion and plugin together. The plugin restores its desired subscriptions when a session becomes ready or Tasker initializes a profile. When editing a copied/imported subscription, choose whether to replace the previous configuration or keep both.

After deleting obsolete profiles, use **Diagnostics → Clear AppMessage subscriptions**; active profiles can register again. Tasker does not provide a reliable per-profile deletion callback. See [compatibility notes](docs/automation-compatibility.md) for restore behavior, legacy filters, privacy and recovery details.

Install updates signed with the same release key. CI debug artifacts share the release signing identity so they can replace a release install for testing; ordinary local debug builds use a separate development key. Debug APKs are debuggable and should only be installed for testing. A changed plugin certificate requires renewed companion consent. Do not blindly retry an action after a timeout: its remote operation may already have completed.

## Architecture

```text
Tasker profiles and tasks
        |
        v
Plugin configuration and runners
        |
        +-- ordered condition queue
        +-- event cache, replay cursor and subscription registry
        |
        v
Authenticated Binder / AIDL connection
        |
        v
Companion automation bridge
        +-- consent and command policy
        +-- ordered event journal and per-watch collectors
        |
        v
Watch connection and protocol services
```

- `bridge/`: host certificate pinning, binding, handshake, bounded IPC and reconnects.
- `cache/`: immutable event delivery, deduplication, replay and durable state.
- `tasker/`: configuration screens, state/event runners, action runners and variables.
- `ui/`: onboarding, access guidance and existing maintenance tools.
- `app/src/main/aidl/`: service and callback contracts shared with the companion.
- `app/src/test/`: lifecycle, consent/readiness, event delivery and action regressions.

The companion owns Bluetooth and watch operations. The plugin uses local IPC; it does not create another watch connection. Trust is pinned on the device and excluded from backup. Authorization revisions invalidate cached delivery when permissions change.

## Build locally

Use JDK 17 and Android SDK 36. Clone the theme alongside the project, or provide its location explicitly:

```sh
git clone https://github.com/nbetcher/neon_grid_theme.git ../neon_grid_theme
bash gradlew :app:testDebugUnitTest :app:assembleDebug \
  -PneonGridDir=../neon_grid_theme/android -PLOCAL_RELEASE_BUILD=true
bash gradlew :app:assembleRelease \
  -PneonGridDir=../neon_grid_theme/android -PLOCAL_RELEASE_BUILD=true
```

The local release command exercises R8 and resource optimization with local signing. Distribution builds use `keystore.jks` and the `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEYSTORE_ALIAS` and `RELEASE_KEY_PASSWORD` environment variables. Keep the signing identity unchanged between releases.

Trusted builds may pass `-PRELEASE_SIGN_DEBUG=true` to sign the debug variant with that same distribution key. This is opt-in; normal local debug builds use the AGP-managed debug keystore. Keep distribution credentials out of untrusted pull-request builds.

Release builds enable R8 full mode, code optimization/obfuscation, resource shrinking and optimized resource shrinking. Keep rules preserve the reflection, serialization and Binder contracts required at runtime. Debug builds remain debuggable and unminified. Neither successful compilation nor unit tests replace testing the optimized APK on a device.

## Automated builds and releases

The Android workflow builds **debug and release**, runs unit tests, checks release lint, verifies APK signatures and uploads APKs with SHA-256 checksums. The theme checkout is pinned to a tested revision. R8 mappings and test reports are retained as workflow artifacts.

Set these repository Actions secrets before running a release build:

- `APP_KEYSTORE_B64`: base64-encoded release keystore.
- `APP_KEYSTORE_PASSWORD`: keystore password.
- `APP_KEY_ALIAS`: signing alias.
- `APP_KEY_PASSWORD`: optional; defaults to the keystore password.

Pushes to `main` run both builds. A `v0.9.0` tag, or a manual run with **publish** enabled, publishes a release after both builds pass. The workflow checks the tag against the application version. Release assets contain the optimized signed APK and its checksum; debug APKs remain available from the workflow artifacts. Both CI variants use the release keystore, so missing signing secrets fail either build explicitly.

## Help and feedback

Report reproducible problems in [Issues](https://github.com/nbetcher/PebbleTasker/issues), including plugin/companion versions, Android version, watch model and the relevant Tasker configuration. Exclude personal notification contents, credentials and signing material.

[Support PebbleTasker development](https://buymeacoffee.com/nbetcher).
