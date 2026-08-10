# RarityCore Changelog

## [1211.14.5] - 2026-08-10

### Fixed
- Fixed cache component-awareness: stacks with component rarity control (`raritycore.Level`) are now cached per-stack via NBT hash, no longer polluted by the shared ID cache (fixes the "all guns change together" root cause)

### Adjusted
- Component rarity control is now "half-enabled": when ModularRarity is detected, component rarity is only read for ModularShoot's gun (`modularshoot:gun`) and plugin (`modularshoot:plugin`) items; all other items short-circuit in O(1), removing the hot-path cost of a full `copyTag` NBT copy (bound items keep working via `registerRarity`)

### Build & Other
- Bumped mod version to 1211.14.5

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