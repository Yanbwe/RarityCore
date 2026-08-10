# RarityCore Changelog

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
