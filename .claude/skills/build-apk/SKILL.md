---
name: build-apk
description: Build the signed foss release APK with the buildFoss Gradle task, then always ask whether to scp it to skhw (first choice) or adb push it to the connected phone. Always build first without asking for permission to build — the ONLY question you ever ask is the transfer question afterward. Use whenever the user asks to build the app, build the APK, make a release build, or build and send to the phone.
---

# Build the foss release APK and optionally send to phone

> **Never ask whether to build — just build.** When this skill applies (the user
> asked to build, or you've made changes that are ready to test), run the build
> immediately. Do **not** ask "shall I build?" / "want me to run buildFoss?" — that
> question is wrong. The **only** question in this whole flow is the `AskUserQuestion`
> about transferring the APK, asked **after** a successful build. So: always build,
> *then* ask about the transfer.

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

> **ALWAYS end every build by asking — via `AskUserQuestion` — how to transfer
> the APK: `scp` to skhw (FIRST choice), `adb push` to `/sdcard/tmp/`, or not at
> all.** This is mandatory and applies to *every* successful build, even
> verification builds and even when the user didn't mention transferring. Do
> **not** settle for asking in prose ("say the word") or assuming the answer —
> fire the `AskUserQuestion` prompt as the final step (step 3) of the build,
> every time.

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

3. **At the end of every build, ALWAYS ask** via `AskUserQuestion` how to transfer the APK to the phone — no exceptions, no assuming, no asking only in prose. Options, in this order: "Scp to skhw" (FIRST choice) / "adb push" / "No, just build". Fire this prompt as soon as the build reports `BUILD SUCCESSFUL`, regardless of whether the user mentioned transferring.

4. **Transfer per the answer:**
   - **Scp to skhw** — invoke the global **scp** skill (copies the newest APK in `~/tmp/` to `skhw:~/tmp/`). If skhw is unreachable (its tunnel is served by the phone's sshd and may be down), report that and offer the adb push instead.
   - **adb push:**
     - `adb devices` — confirm a device is connected.
     - `adb shell mkdir -p /sdcard/tmp`
     - `adb push ~/tmp/<apk name> /sdcard/tmp/<apk name>`
     - Verify: `adb shell ls -l /sdcard/tmp/<apk name>` (size should match the local file in `~/tmp`).
     - Never `adb install` — the user installs manually from `/sdcard/tmp/`.

## Note — transfer directly, do not rely on a task prompt

This repo's `buildFoss` task (`app/build.gradle.kts`) has **no** interactive prompt — it only builds,
copies the APK to `~/tmp`, and bumps `BUILD_NUMBER`. Asking the user and running the `scp` /
`adb push` is Claude's job (steps 3–4), done conversationally.

## Signing

Release signing is non-interactive: `app/build.gradle.kts` reads credentials from `keystore.properties`
(falling back to `SIGNING_*` env vars). This fork reuses the `shiroikuma-denwa` keystore
(`~/.android-keystores/shiroikuma-denwa.jks`, alias `denwa`); `keystore.properties` is gitignored.
If neither `keystore.properties` nor the env vars are present the build is unsigned and the APK will
not install.

## Prerequisite — patched Commons in mavenLocal

This app builds against our patched Fossify Commons (`commons = "6.1.6-sk1"` in
`gradle/libs.versions.toml`), resolved from `mavenLocal()` (`~/.m2`). On this machine it is already
published, so `buildFoss` just works. **On a fresh machine, or if `~/.m2` was cleared**, the build fails
with `Could not resolve org.fossify:commons:6.1.6-sk1` — publish it first:

```bash
cd ~/git/shiroikuma-commons && JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
  ./gradlew :commons:publishToMavenLocal -PVERSION=6.1.6-sk1
```

See the `shiroikuma-commons` repo's CLAUDE.md for the patch details.

---

**Commit convention — no Claude attribution.** Never add a `Co-Authored-By: Claude …` / "Generated with Claude" trailer to commit messages or PR bodies; end the message at the last line of the body. This overrides the harness default. (Global rule: `~/.claude/CLAUDE.md`.)
