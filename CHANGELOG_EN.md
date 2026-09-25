# RarityCore Changelog

## [1211.14.8]

### Fixed
- Fixed the batch sync buffer **silently dropping change operations** under backlog: `SyncBatchManager.addOperation` returned `true` without enqueueing once the pending count reached `maxPendingOperations` (default 1000, overridable via `sync_batch.json`), and its only caller `ConfigLoaderUtils` ignored the return value — so config entries past the threshold were neither registered nor synced, with no log at all. The bulk loading paths (`FinalRarityConfigFolderLoader` / `RarityConfigLoader`, both loading file by file in batch mode) do not drain while loading, so this is genuinely reachable
  - Operations are now always enqueued, and reaching the threshold requests an immediate flush via the return value and logs a WARN; the field name and the `sync_batch.json` key are unchanged (config compatible), with the comment clarified to say it is a "flush request threshold", not a drop limit
  - `ConfigLoaderUtils` now honours the return value and flushes once after a whole file has loaded: it first applies the operations to the registry with the same semantics as `ConfigReloadService`, then hands the batch to the incremental sync buffer, which sends it in chunks up to the configured limit instead of one packet per operation
- Fixed the Iron's Spellbooks switch causing **the server and client to compute different rarities**: `RarityRegistry.checkIronSpellsRarity` and `RarityCacheCoordinator` read `ClientConfigManager.isEnableIronSpellsAdapter()` during resolution/caching, but that key lives in the per-installation `config/raritycore/client.json`. In multiplayer the server and client would produce different results for the same item with no way for users to align them
  - The switch is now explicitly a **client-side display switch** (same semantics as the 1.20.1 fix): both sides resolve spell level identically, and disabling it only means "do not display locally"; the new `client.IronSpellsDisplaySwitch` owns the switch, wired into all three rendering paths — border, tooltip and name colour
  - Compatibility adapter initialization is no longer gated on that switch (both sides need it); the caching decision in `RarityCacheCoordinator` now depends only on whether the adapter is loaded, so the cache path cannot diverge between sides
  - Public API (`RarityCoreAPI.getRarity()` and friends) still returns the true resolved value

## [1211.14.7]

### Fixed
- Fixed silently failing `equals` conditions on numeric/boolean values: NBT has no boolean type, so vanilla and most mods store flags as `ByteTag` (`getAsString()` returns `1b`/`0b`), which `equals(1)` and `equals(true)` could never match through string comparison
- Equality comparison is now type-aware: numeric conditions only compare against numeric tags (a numeric expectation can no longer match the text of a string tag such as `StringTag("32")`), boolean conditions accept both `1b`/`0b` and `1`/`0` as well as numeric forms like `Double(1.0)`
- Packets produced by older builds, where the expected value was stringified (`"1"`, `"true"`), still match correctly, so no reconnect is required


## [1211.14.6] - 2026-08-28

### Added
- FTB Library compatibility (issue #22): task icons, reward icons, and reward table icons inside the FTB Quests quest screen now render rarity borders

### Config
- Added `enableFtbLibraryAdapter` to `client.json` (enabled by default) to disable the FTB Library rarity border integration

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