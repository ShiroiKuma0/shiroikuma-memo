---
name: build-apk
description: Build the signed foss release APK with the buildFoss Gradle task, then always ask whether to push it to the connected phone via adb. Use whenever the user asks to build the app, build the APK, make a release build, or build and push to the phone.
---

# Build the foss release APK and optionally push to phone

> **The push destination is ALWAYS `/sdcard/tmp/`.** Every `adb push` of the APK
> goes to `/sdcard/tmp/<apk name>` — **never** `/sdcard/Download/` or anywhere
> else. Create `/sdcard/tmp` if needed and push there.

> **Never run `adb install` (or `pm install`).** The build step may copy the APK
> to the phone with `adb push` — and only after confirming with the user — but
> **the user installs the APK themselves** from the phone's file manager. Do not
> install it for them under any circumstances.

> **Never `git commit` or `git push` on your own.** Building does not include
> committing. After building (and the optional `adb push`), the user tests the
> build themselves. **Only when the user explicitly says "Push"** do you then
> `git commit` the changes and `git push origin custom`. The user's **"Push"**
> means *commit-and-push-to-the-fork* — it is unrelated to the `adb push` file
> copy in step 4.

> **ALWAYS end every build by asking — via `AskUserQuestion` — whether to
> `adb push` the APK to `/sdcard/tmp/`.** This is mandatory and applies to
> *every* successful build, even verification builds and even when the user
> didn't mention pushing. Do **not** settle for asking in prose ("say the word")
> or assuming the answer — fire the `AskUserQuestion` prompt as the final step
> (step 3) of the build, every time.

## Steps

1. **Note the output filename.** Read the current version and build number:
   - `grep -E 'VERSION_NAME|VERSION_CODE|BUILD_NUMBER' gradle.properties`
   - The APK will be `shiroikuma-memo_<VERSION_NAME>+<BUILD_NUMBER>_arm64-v8a.apk`, using the `BUILD_NUMBER` value **before** the build (the task bumps it afterward).
   - versionCode for that build = `VERSION_CODE * 10000 + BUILD_NUMBER`.

2. **Build** (needs JDK 21 — the default `java` on this machine is JDK 11):
   - `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew buildFoss < /dev/null`
     (the `< /dev/null` guarantees it never blocks on stdin)
   - This runs `assembleFossRelease`, copies the signed APK to `~/tmp/<apk name>`, and auto-increments `BUILD_NUMBER` in `gradle.properties`.
   - The task prints `>>> <path>` and `>>> versionCode <n>`; use those to confirm the exact filename and code, and confirm `BUILD SUCCESSFUL`.

3. **At the end of every build, ALWAYS ask** via `AskUserQuestion` whether to push the APK to the phone — no exceptions, no assuming, no asking only in prose. Options: "Yes, push via adb" / "No, just build". Fire this prompt as soon as the build reports `BUILD SUCCESSFUL`, regardless of whether the user mentioned pushing.

4. **If yes, push directly yourself:**
   - `adb devices` — confirm a device is connected.
   - `adb shell mkdir -p /sdcard/tmp`
   - `adb push ~/tmp/<apk name> /sdcard/tmp/<apk name>`
   - Verify: `adb shell ls -l /sdcard/tmp/<apk name>` (size should match the local file in `~/tmp`).
   - Never `adb install` — the user installs manually from `/sdcard/tmp/`.

## Note — push directly, do not rely on a task prompt

This repo's `buildFoss` task (`app/build.gradle.kts`) has **no** interactive prompt — it only builds,
copies the APK to `~/tmp`, and bumps `BUILD_NUMBER`. Asking the user and running `adb push` is Claude's
job (steps 3–4), done conversationally.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads credentials from `keystore.properties`
(falling back to `SIGNING_*` env vars). This fork reuses the `shiroikuma-denwa` keystore
(`~/.android-keystores/shiroikuma-denwa.jks`, alias `denwa`); `keystore.properties` is gitignored.
If neither `keystore.properties` nor the env vars are present the build is unsigned and the APK will
not install.
