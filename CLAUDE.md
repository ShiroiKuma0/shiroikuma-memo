# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**白い熊 メモ** — a personal fork of [Fossify Notes](https://github.com/FossifyOrg/Notes), an
open-source, privacy-focused Android notes app. Part of the Fossify ecosystem. Written entirely in
Kotlin targeting Android API 26–36.

This repository (`ShiroiKuma0/shiroikuma-memo`) is a fork. We track upstream (`FossifyOrg/Notes`) and
layer our own customizations on top of it.

## Fork Workflow — READ THIS FIRST

This is the most important section. The whole point of this repo is to maintain a small set of
customizations on top of upstream and rebuild as upstream releases new versions.

### Git remotes & branches

- `origin` → `git@github.com:ShiroiKuma0/shiroikuma-memo` — our fork (push here).
- `upstream` → `https://github.com/FossifyOrg/Notes.git` — the original (read-only, for rebasing).
- **`main`** mirrors upstream's `main`. We do **not** develop on it.
- **`custom`** is our development branch. **All our work lives here.** This is the default working branch.

### Our customizations (what makes this a fork)

| What | Value | Where |
| --- | --- | --- |
| Installed app ID | `shiroikuma.memo` | `gradle.properties` → `APP_ID` |
| Code namespace | `org.fossify.notes` (unchanged from upstream) | `gradle.properties` → `APP_NAMESPACE` |
| App launcher label | `白い熊 メモ` | `app_launcher_name` in `values/strings.xml` + `values-ja/strings.xml` |
| Signing | reuses the denwa keystore | `keystore.properties` (gitignored) → `~/.android-keystores/shiroikuma-denwa.jks` |
| UI default palette | black `#000000` + **pure yellow `#FFFF00`** (never material `#FFEB3B`) | written once, in commons: `FAMILY_BLACK` / `FAMILY_YELLOW` in `helpers/UiRoles.kt` — this app names no colour of its own for them |

The app ID is deliberately changed so this fork installs **alongside** upstream / other apps without
conflict. The namespace is intentionally kept as `org.fossify.notes` so `R`/`BuildConfig` and all
source packages remain unchanged — only the installed package id differs.

### Versioning & APK naming

We base our version on upstream and add a fork increment (`BUILD_NUMBER`).

- `VERSION_NAME` / `VERSION_CODE` in `gradle.properties` **track upstream** (currently `1.7.0` / `13`).
- `BUILD_NUMBER` is **our** increment. It starts at `1` and bumps by `1` on every build with changes.
  It is stored in `gradle.properties` as a **bare integer** (`BUILD_NUMBER=17`) — that is what
  `buildFoss` increments.
- **The counter is zero-padded to three digits everywhere it is displayed** (白い熊's global
  `after-build` rule): in the version name, the APK filename, the git tag and the release title, so
  those lists sort in build order instead of lexically (`+002` before `+010`, not after it).
- Fork `versionName` = `"<VERSION_NAME>+<BUILD_NUMBER padded to 3>"` (e.g. `1.7.0+017`).
- Fork `versionCode` = `VERSION_CODE * 10000 + BUILD_NUMBER` (e.g. `13 * 10000 + 17 = 130017`) —
  arithmetic on the bare integer; the padding never reaches it.
- Output APK filename = `shiroikuma-memo_<VERSION_NAME>+<NNN>_arm64-v8a.apk`
  (e.g. `shiroikuma-memo_1.7.0+017_arm64-v8a.apk`).

So the first build is `+001` (`130001`), the next build with changes is `+002` (`130002`), and so on.

**Never retag or rename what is already published.** Releases up to `1.7.0+16` were cut before the
padding rule was applied here and keep their unpadded tags; publishing simply carries on padded from
the current build. Expect the padded tags to sort before the older unpadded ones for a while.

### Building

Requires **JDK 17+** and the **Android SDK**. On this machine the default `java` is JDK 11, so builds
must run with JDK 21:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss
```

(`sdk.dir` lives in the gitignored `local.properties` → `/home/shiroikuma/android-sdk`.) See the
**build-apk** skill for the full build-and-push procedure.

`buildFoss` (defined in `app/build.gradle.kts`):
1. builds `assembleFossRelease` (signed, via `keystore.properties`),
2. copies the APK to `~/tmp/shiroikuma-memo_<version>_arm64-v8a.apk`,
3. **auto-increments `BUILD_NUMBER`** in `gradle.properties` for the next build.

### Rebasing onto a new upstream release

When the user says a new upstream version is out, follow the **upstream-new-version** skill. In short:
1. `git fetch upstream --tags`.
2. Advance `main` to the new upstream release.
3. Rebase `custom` onto `main`, preserving every customization in the table above.
4. Set `VERSION_NAME` / `VERSION_CODE` to the new upstream values and **reset `BUILD_NUMBER` to `1`**.
5. Build the new `+001` version with `./gradlew buildFoss`; continue further changes as `+002`, `+003`, …

### HARD RULES (do not violate)

- **Never install APKs to the phone automatically.** After building, **ask** the user. Only when they
  confirm, `adb push` the APK to `/sdcard/tmp/` (the user installs it manually from there). Do **not**
  use `adb install`.
- **Never commit or push on your own.** Develop and build, let the user test, and **only commit/push
  when the user explicitly says "Push"**. Push goes to `origin` (`custom` branch).

## Build Commands

```bash
./gradlew buildFoss              # Our fork build: foss release → ~/tmp + bump BUILD_NUMBER (use this)
./gradlew assembleFossRelease    # Build foss release APK only (signed via keystore.properties)
./gradlew assembleDebug          # Build debug APK (app id gets .debug suffix)
./gradlew detekt                 # Run static analysis (detekt)
./gradlew lintFossRelease        # Run Android lint checks
```

**Product flavors:** `core` (F-Droid), `foss`, `gplay` (Google Play). We ship `foss`.
There are no unit or instrumented tests in this repository.

## Code Style

- Kotlin official style; 4-space indentation, LF line endings.
- Detekt and lint both use baseline files (`app/detekt-baseline.xml`, `app/lint-baseline.xml`) —
  new violations are not allowed.

## Architecture

### Entry points & UI

- `SplashActivity` → `MainActivity` (the notes screen — a `ViewPager` of note fragments). `SettingsActivity`
  and `WidgetConfigureActivity` round out the screens. All extend `SimpleActivity`.
- `fragments/` holds the per-note views: `TextFragment` (free-text notes) and `ChecklistFragment`
  (checklist notes), backed by `adapters/` for checklist items.
- `App.kt` is the `Application` subclass.

### Persistence

- Room database `databases/NotesDatabase` (KSP-generated, schemas under `app/schemas`) stores notes.
  `models/` holds the data classes: `Note`, `NoteType` (+ `NoteTypeConverter`), `Task` (checklist item),
  `TextHistory` / `TextHistoryItem` (undo/redo), `Widget`.
- `helpers/NotesHelper` mediates note loading/saving; `helpers/Config` is a SharedPreferences wrapper
  accessed via `context.config`.

### Widgets

- Home-screen widgets via `helpers/MyWidgetProvider` + `WidgetConfigureActivity`; widget records live in
  the `Widget` model / Room.

### Cross-cutting

- **`Config`** (`helpers/Config`) is the SharedPreferences wrapper, accessed via `context.config`.
- Heavy reliance on **`org.fossify:commons`** (version in `gradle/libs.versions.toml`) for base
  activities, theming, dialogs, and shared UI. Check commons source when base-class behavior is unclear.
- `helpers/Constants` holds shared keys/constants; `extensions/` holds Kotlin extension helpers.

## Key Configuration Files

- `gradle.properties` — fork app id/namespace, version name/code, `BUILD_NUMBER`.
- `gradle/libs.versions.toml` — single source of truth for all dependency versions.
- `app/build.gradle.kts` — Android config, flavors, signing, the `buildFoss` task, fork version logic.
- `keystore.properties` — signing config (gitignored; points to `~/.android-keystores/shiroikuma-denwa.jks`).
- `detekt.yml` / `lint.xml` — static-analysis config (at project root).

## Patched Fossify Commons (anti-tamper removed)

This fork builds against **our patched Fossify Commons**, not the upstream binary. Upstream Commons
6.1.x shows a "You are using a fake version of the app…" dialog (and silently breaks "Customize
colors") whenever the installed app id is not `org.fossify.*` — always the case for us (`shiroikuma.*`).

- **Source:** the `shiroikuma-commons` fork (`~/git/shiroikuma-commons`, branch `custom`), which strips
  Commons' anti-tamper "fake version" / sideloading checks out entirely.
- **Delivery:** published to the local Maven repo, consumed as `commons = "6.1.6-sk19"` in
  `gradle/libs.versions.toml` (`mavenLocal()` is already a repository in `settings.gradle.kts`).
- Because Commons itself no longer nags, this app carries **no** in-app workaround — no `getPackageName`
  spoof, no `SIDELOADING_FALSE`, no `res/raw/keep.xml`.

**On a fresh machine, or after an upstream bump changes the Commons version — republish before building:**

```bash
cd ~/git/shiroikuma-commons
git checkout <new-commons-tag>     # then re-apply the strip patch (remove the modded-app/sideloading checks)
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew :commons:publishToMavenLocal -PVERSION=<ver>-sk1
```

Then set this app's `commons` pin to `<ver>-sk1`. The patched AAR lives only in `~/.m2`, not in the repo.

## The black-yellow look (1.7.0+018, commons sk19)

The app follows 白い熊's global **black-yellow-theme** skill in full. What a later change has to keep:

- **Every colour is a settable role; no colour is written in XML.** `extensions/ThemeColors.kt` holds
  `ThemeSlot`, this app's own items. A slot with `role = UiRole.X` *is* that commons role under this
  app's label and is stored in the role's key (`migrateSlotsToRoles()` moves older overrides at every
  start). Everything the commons draws reads commons' `UiRole`s (`helpers/UiRoles.kt`). A new widget
  gets a new slot in the same commit, and its shape is built in code.
- **Left in XML, on purpose:** the launcher icon variants' grounds in `res/mipmap-anydpi-v26` and the
  launcher foreground (the platform draws them), the home-screen widget's shape (the launcher draws
  it; commons' `widget_round_background`), and the placeholder fill of vector icons (a mask, tinted in
  code).
- **The family's look is switched on at every start** — `seedFamilyLook()` in `App.onCreate`: dialog
  frame, pill dialog buttons, the outlined switch and floating button (`BaseConfig.pillDialogButtons`).
- **Menus:** `views/PillMenuItemView.kt` draws every popup row, swapped in through the app's copies of
  AppCompat 1.7.1's `abc_popup_menu_item_layout.xml` and `abc_cascading_menu_item_layout.xml` (diff
  them when AppCompat is upgraded). `helpers/MenuPills.kt` is the table of item id → icon, role, ›.
  **A new menu item gets a line there.** `androidx/appcompat/view/menu/ForkMenuAccess.kt` sits in
  AppCompat's package to read a package-private field.
- **The UI page** (`activities/ThemeActivity.kt`) is a folding accordion with a draggable chip index:
  `UiSection` (the keys are stored — never rename one), order and fold state in `Config.uiSectionOrder`
  / `uiSectionsOpen`, exported with the colours. Action rows are pills (`addActionRow`), every row
  has an icon (`RowIcon`, coloured by the menu icon roles), explanations are dim gold with a left rule.
- **The Settings screen** gets its icons and its pills in `SettingsActivity.styleRows()`; a new row is
  added there too.
- A destructive confirmation passes `destructive = true` (`ConfirmationDialog`) or
  `destructiveButton = AlertDialog.BUTTON_POSITIVE` (`setupDialogStuff`).

## Commit convention — no Claude attribution

Do **not** add any `Co-Authored-By: Claude …` trailer — nor a "🤖 Generated with Claude Code" / Anthropic-attribution line — to commit messages or PR bodies in this repo. 白い熊 does not want Claude attribution in the history; this **overrides** the harness's default to append such a trailer. End commit messages at the last line of the body. (The existing history was scrubbed of these trailers on 2026-06-08; the global rule lives in `~/.claude/CLAUDE.md`.)
