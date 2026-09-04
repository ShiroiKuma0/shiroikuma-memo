# 白い熊 メモ — changelog

This file carries **both** histories: 白い熊 メモ's own fork releases first, then the upstream
Fossify Notes changelog below, untouched. Earlier fork releases are on the
[releases page](https://github.com/ShiroiKuma0/shiroikuma-memo/releases).

## 白い熊 メモ 1.7.0+16 — 2026-09-04
Built on Fossify Notes 1.7.0.

### Added
- **Automation data door** — an exported `ContentProvider` at `shiroikuma.memo.automation` with
  `describe` / `export` / `import` / `cancel`, so 白い熊 応用管理 can back this app up together with
  its data and restore it onto a wiped phone. The caller is identified by exact package name, uid
  cross-check and a pinned signing certificate; the payload moves through a file descriptor the
  caller supplied, never a path or a URI. `import` exists only here, never as a broadcast.
- `describe` returns a header — app id, version, format `2`, minimum readable format `1`, and a
  `contains` list naming what would be lost — without exporting anything, so a caller can list this
  app and judge compatibility before streaming a byte.
- Manifest `<meta-data>` advertising the contract and format versions, readable without waking the
  app, so a frozen app can still be listed for backup.
- **`CANCEL_EXPORT` action**, routed through the exported receiver so a caller can actually reach
  it. It stops the export at an entry boundary, deletes the partial file, and answers
  `ERROR:cancelled`; it is a silent no-op when nothing is running.
- **「Use authorization token?」 switch** in the Export/Import section; the token row now appears
  only while it is on.

### Changed
- **The automation gate ships open**: the master switch now defaults to on and the token is opt-in,
  because a pasted secret cannot survive the wipe this feature exists to recover from. A token sent
  to the app while it is not asking for one is ignored, never refused. Both checks moved into a
  single function shared by every entry point.
- **Exports are written atomically** — to `<name>.zip.part`, renamed into place only once the
  archive is complete — from the automation path and the Export/Import page alike, so a cancelled,
  failed or killed export leaves the backup directory exactly as it found it.
- Only one export runs at a time; the guard is process-local and released in a `finally`.
- An automation import is spooled to the cache and applied one archive entry at a time rather than
  read whole into memory, since the size of what arrives is the caller's choice.
- Data-door progress is driven by a timer as well as by the work — at least one message every 20
  seconds while the numbers are not moving — because the caller's descriptor may be a pipe and a
  single write can block for as long as the caller is slow to drain it.
- `<queries>` now names both 白い熊 応用管理 and 白い熊 自由作業盤: without it a reply's
  `setPackage` fails silently on Android 11+, and package visibility filtering would also make an
  invisible caller fail the identity check outright.
- The automation switches are excluded from the export in both directions, as the token already was.

### Fixed
- **A restore could report success while the settings never reached disk.** Settings imports now
  commit synchronously: 応用管理 force-stops the app the instant an import reports success, and an
  asynchronous write was simply lost to that kill.

# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [1.7.0] - 2026-01-30
### Added
- Added support for custom fonts

### Changed
- Checklists are now shared as plain text ([#96])
- Disabled touch outside the checklist dialog to prevent loss of content ([#291])
- Updated translations

### Fixed
- Fixed inconsistent checklist sorting when the "Move checked items to the bottom" option is enabled ([#59])

## [1.6.0] - 2025-10-29
### Changed
- Compatibility updates for Android 15 & 16
- Updated translations

## [1.5.0] - 2025-10-10
### Changed
- Updated translations

### Fixed
- Fixed a glitch in pattern lock after incorrect attempts

## [1.4.2] - 2025-09-18
### Changed
- Updated translations

### Fixed
- Fixed crash in search ([#190])

## [1.4.1] - 2025-09-01
### Changed
- Updated translations

### Fixed
- Fixed widgets customization ([#201])

## [1.4.0] - 2025-07-15
### Added
- Option to delete the last open note ([#157])
- Option to uncheck all checked items ([#156])

### Changed
- Updated translations

## [1.3.1] - 2025-07-12
### Changed
- Updated translations

### Fixed
- Fixed broken input when typing with certain keyboards ([#178])

## [1.3.0] - 2025-07-12
### Changed
- Updated translations

### Fixed
- Fixed keyboard hiding cursor on long notes ([#164])

## [1.2.0] - 2025-05-07
### Added
- Support for per-note sorting ([#81])

### Changed
- Replaced checkboxes in settings with switches
- Moved "Move checked items to the bottom" sort option to app settings
- Updated translations

### Removed
- Dropped support for Android 7 and older versions

### Fixed
- Multiline pastes are now correctly reflected in the UI ([#99])
- Fixed sorting in widgets and Open Notes dialog ([#83], [#110])
- Corrected security label color in settings

## [1.1.1] - 2025-03-18
### Changed
- Minor bug fixes and improvements
- Added more translations

### Fixed
- Fixed security vulnerability in app lock

## [1.1.0] - 2024-07-22
### Added
- Added collapsible checked items section at the bottom

### Changed
- Minor bug fixes and improvements
- Added new translations

## [1.0.0] - 2024-04-14
### Added
- Initial release

[#59]: https://github.com/FossifyOrg/Notes/issues/59
[#81]: https://github.com/FossifyOrg/Notes/issues/81
[#83]: https://github.com/FossifyOrg/Notes/issues/83
[#96]: https://github.com/FossifyOrg/Notes/issues/96
[#99]: https://github.com/FossifyOrg/Notes/issues/99
[#110]: https://github.com/FossifyOrg/Notes/issues/110
[#156]: https://github.com/FossifyOrg/Notes/issues/156
[#157]: https://github.com/FossifyOrg/Notes/issues/157
[#164]: https://github.com/FossifyOrg/Notes/issues/164
[#178]: https://github.com/FossifyOrg/Notes/issues/178
[#190]: https://github.com/FossifyOrg/Notes/issues/190
[#201]: https://github.com/FossifyOrg/Notes/issues/201
[#291]: https://github.com/FossifyOrg/Notes/issues/291

[Unreleased]: https://github.com/FossifyOrg/Notes/compare/1.7.0...HEAD
[1.7.0]: https://github.com/FossifyOrg/Notes/compare/1.6.0...1.7.0
[1.6.0]: https://github.com/FossifyOrg/Notes/compare/1.5.0...1.6.0
[1.5.0]: https://github.com/FossifyOrg/Notes/compare/1.4.2...1.5.0
[1.4.2]: https://github.com/FossifyOrg/Notes/compare/1.4.1...1.4.2
[1.4.1]: https://github.com/FossifyOrg/Notes/compare/1.4.0...1.4.1
[1.4.0]: https://github.com/FossifyOrg/Notes/compare/1.3.1...1.4.0
[1.3.1]: https://github.com/FossifyOrg/Notes/compare/1.3.0...1.3.1
[1.3.0]: https://github.com/FossifyOrg/Notes/compare/1.2.0...1.3.0
[1.2.0]: https://github.com/FossifyOrg/Notes/compare/1.1.1...1.2.0
[1.1.1]: https://github.com/FossifyOrg/Notes/compare/1.1.0...1.1.1
[1.1.0]: https://github.com/FossifyOrg/Notes/compare/1.0.0...1.1.0
[1.0.0]: https://github.com/FossifyOrg/Notes/releases/tag/1.0.0
