# RarityCore Changelog

## [1211.14.4] - 2026-08-10

### Added
- Edit mode support for TacZ items, with auto-generated sub-item matching configuration
- Edit-mode panel can now be dragged with the mouse (position is not persisted; resets on restart)
- Added `enableIronSpellsAdapter` config option to disable Iron Spells rarity integration (issue #14)

### Fixed
- Fixed dragging outside the panel causing unintended panel movement (added press-guard inside the panel)
- Panel position is now clamped during rendering; drag state resets when pressing outside the panel

### Build & Other
- Bumped mod version to 1211.14.4 and fixed related configuration
- Updated release workflow to support version tag pushes

[1211.14.4]: https://github.com/YanbweMod/RarityCore/releases/tag/1211.14.4
