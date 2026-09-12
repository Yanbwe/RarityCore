# RarityCore Changelog

## [1201.14.2]

### Fixed
- Fixed NBT matching rules becoming inactive after restarting the client or re-entering a world: the rule sync packet stringified numeric and boolean `equals` values, so values such as `1` or `true` could no longer match vanilla `ByteTag` storage (`getAsString()` returns `1b`/`0b`) on the client. The packet now preserves the original value types, and packets in the old stringified format are still tolerated and match correctly (no reconnect required)
- Equality comparison is now type-aware: numeric conditions only compare against numeric tags (so a numeric expectation can no longer match the text of a string tag), and boolean conditions accept both `1b`/`0b` and `1`/`0`

### Diagnostics
- `EqualsCondition` now logs each comparison in debug mode (path, actual tag type and content, expected type and value, result) to make "why does my rule not apply" diagnosable

### Network
- Protocol version raised to `1.2.0` (condition value typing in the sync packet changed). Multiplayer requires both server and client on 1201.14.2 or later; mismatched versions are rejected

## [1201.14.1] - 2026-08-10

### Added
- Edit-mode panel can now be dragged with the mouse (position is not persisted; resets on restart)
- Added FTB Library item border rendering compatibility (Mixin + compatibility manager registration)
- Added `enableIronSpellsAdapter` config option to disable Iron Spells rarity integration (issue #14)

### Fixed
- Fixed broken rendering integration with Refined Storage
- Fixed collapsible panel click accidentally triggering mode switch; restored right-click editing of item slots
- Fixed dragging outside the panel causing unintended panel movement (added press-guard inside the panel)
- Panel position is now clamped during rendering to keep it within the screen bounds

### Build & Other
- Updated build and release workflow with version parsing and publishing support
- Updated .gitignore to exclude new build files
