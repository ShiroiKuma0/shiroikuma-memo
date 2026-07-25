<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 メモ icon" />

# 白い熊 メモ

**Fossify Notes in black and pure yellow — themed down to every single element.**

A fork of [Fossify Notes](https://github.com/FossifyOrg/Notes) with **major additions**: a granular per-element theming page (every color, every font), one-ZIP Export/Import of settings **and notes**, headless backup driven by a sister app, per-element custom fonts, a 設定 quick-access double-button, and a black/pure-yellow (`#FFFF00`) default look.

Installs **side-by-side** with Fossify Notes (app id `shiroikuma.memo`).

**📥 Latest release: [`1.7.0+14`](https://github.com/ShiroiKuma0/shiroikuma-memo/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-memo/releases)

</div>

---

## 🎨 白い熊 メモ UI — theme every element

A dedicated **UI page** (long-tap the top-right hamburger, or tap 設 in the top bar) with granular, per-element theming: background, primary/accent, text and secondary text, main-screen menu icons and text, the 設定 button characters, sub-page titles and back arrows, note text, the counter, checklist items (text / done / checkbox), and tab titles. Every slot inherits sensibly from the foundation colors and can be overridden individually — with an alpha-capable color picker and recent colors. The page itself is styled in the kxkb convention: bold text-wide-underlined headings, hairline section separators, and a clean indent ladder.

---

## 📦 Export / Import — everything, in one ZIP

The first section of the UI page exports and imports the whole app by category — **UI colours**, **Fonts** (including the imported font files themselves), **App settings**, and **the notes themselves** (every note's text, including the content of file-backed ones, plus its type and lock state). It is one archive, never a settings file next to a data file: `shiroikuma-memo_<yyyy-MM-dd_HH-mm-ss>.zip`, the naming every 白い熊 app shares so a single backup folder sorts and reads uniformly. Set an export directory once and exports become one tap; the page shows the timestamp of the latest export in that directory. Import merges (never wipes) — notes are matched by title, so a restore updates them instead of duplicating them — rejects foreign files, and offers an in-place app restart when done.

---

## 🗄️ Headless backup, together with every sister app (保存復元)

The same export runs **without any UI**, driven by [白い熊 自由作業盤](https://github.com/ShiroiKuma0/shiroikuma-jiyusagyoban): a token-gated `EXPORT_STATE` broadcast makes this app write its ZIP and reply with the real path and byte size, so one task backs up every 白い熊 app in a single run. A companion `LIST_CATEGORIES` action feeds the caller's checkbox picker with this app's own categories, `items` narrows a run to a subset, and progress comes back as **real counts, never a percentage** (「Notes 128/342」). The gate is a per-app **24-byte token**, generated on the device, compared in constant time, **off by default**, and deliberately excluded from every export — so it can never travel inside a backup. Switch and token live right below the Export/Import row; the token copies to the clipboard on tap.

---

## 🔤 Per-element fonts

Import your own `.ttf`/`.otf` fonts and assign **family, weight, and size per element** — the note text, checklist items, tab titles, headers, and the 設定 button each get their own typography, with a live sample line while you tune it.

---

## 🖤💛 Black & pure yellow

The default look is solid black `#000000` with **pure yellow `#FFFF00`** — the launcher icon included. Bordered black/yellow dialogs, pill-shaped dialog buttons, and a matching Exporting/Importing flash keep the look consistent everywhere.

---

## 設定 Quick access

A 設定 double-button sits in the main top bar: **設** opens the 白い熊 メモ UI page, **定** the regular settings. A **long-tap on the hamburger** opens the UI page too.

---

## 🔓 No nags

Built against a patched Fossify Commons (`6.1.6-sk1`) with the "fake version" / sideloading checks removed — no modded-app dialog, and "Customize colors" works even though the app id isn't `org.fossify.*`.

## Built on Fossify Notes

A fork of [Fossify Notes](https://github.com/FossifyOrg/Notes) (app id `shiroikuma.memo`, so it coexists with the official build). Fossify builds excellent open-source, privacy-focused, ad-free apps — all credit for the underlying notes app goes to them. The code remains under the [GPL-3.0 license](LICENSE).

## Building

```bash
git clone git@github.com:ShiroiKuma0/shiroikuma-memo.git
cd shiroikuma-memo
# needs JDK 17+ and the Android SDK; publish the patched commons 6.1.6-sk1 to mavenLocal first
./gradlew buildFoss   # signed foss release → ~/tmp/shiroikuma-memo_<version>_arm64-v8a.apk
```

Versioning: `<upstream version>+<fork build>` (e.g. `1.7.0+14`), versionCode `upstream*10000+build`.
