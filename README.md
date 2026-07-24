<div align="center">

<img src="app/src/main/ic_launcher-playstore.png" width="120" alt="白い熊 メモ icon" />

# 白い熊 メモ

**Fossify Notes in black and pure yellow — themed down to every single element.**

A fork of [Fossify Notes](https://github.com/FossifyOrg/Notes) with **major additions**: a granular per-element theming page (every color, every font), category-based Export/Import of all settings, per-element custom fonts, a 設定 quick-access double-button, and a black/pure-yellow (`#FFFF00`) default look.

Installs **side-by-side** with Fossify Notes (app id `shiroikuma.memo`).

**📥 Latest release: [`1.7.0+13`](https://github.com/ShiroiKuma0/shiroikuma-memo/releases/latest)** — [all releases & APK downloads »](https://github.com/ShiroiKuma0/shiroikuma-memo/releases)

</div>

---

## 🎨 白い熊 メモ UI — theme every element

A dedicated **UI page** (long-tap the top-right hamburger, or tap 設 in the top bar) with granular, per-element theming: background, primary/accent, text and secondary text, main-screen menu icons and text, the 設定 button characters, sub-page titles and back arrows, note text, the counter, checklist items (text / done / checkbox), and tab titles. Every slot inherits sensibly from the foundation colors and can be overridden individually — with an alpha-capable color picker and recent colors. The page itself is styled in the kxkb convention: bold text-wide-underlined headings, hairline section separators, and a clean indent ladder.

---

## 📦 Export / Import every setting

The first section of the UI page exports and imports **all** app settings by category — **UI colours**, **Fonts** (including the imported font files themselves), and **App settings** — as a single ZIP of typed JSON. Set an export directory once and exports become one tap; the page shows the timestamp of the latest export in that directory. Import merges (never wipes), rejects foreign files, and offers an in-place app restart when done.

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

Versioning: `<upstream version>+<fork build>` (e.g. `1.7.0+13`), versionCode `upstream*10000+build`.
