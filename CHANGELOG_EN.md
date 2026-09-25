# RarityCore Changelog

## [1201.14.4]

### Fixed
- Fixed new data never being pushed to clients after `/raritycore reload`: `NetworkRetryManager` dispatched packets with `channel instanceof IncrementalSyncPacket` / `instanceof RaritySyncPacket`, but the arguments are actually `IncrementalSyncPacket.INSTANCE` / `RaritySyncPacket.INSTANCE`, whose declared type is `SimpleChannel` — unrelated to the packet classes. Both checks were therefore always false and the method always returned `false`, so no packet was ever sent. Callers discarded the return value and the "sent successfully" log only fired on a retry, so the failure was completely silent. The impact went beyond hand-written configs: **all 75 bundled datapack JSONs (74 mods) register through `RarityRegistry.register(item, rarity)`, which syncs by default**
  - The channel argument is now used directly instead of being type-checked, success is judged by the return value rather than "no exception was thrown", and failures retry per-player (only the players that actually failed) with an error logged when all attempts are exhausted
- Fixed the per-level `tooltip` block being lost when `RarityStyle.json` is written: the `rarities` loop in `buildCurrentConfigJson` only wrote `color`, `itemNameColor` and `border` and **never wrote `tooltip`**, even though `parseLevelOverride` reads it. As a result every save (including those triggered by `setTooltipContent` / `setStarMode` / `setStarRepeatChar` and their KubeJS bindings) erased that block from the file: setter changes were lost on restart and hand-written per-level tooltip/star config could never be read back
  - In the same loop `border` was written with all four fields unconditionally, so on reload `parseBorder` marked them all as explicitly specified — **"unspecified means inherit from the lower level" silently stopped working after the first save**. Per-level `border.fallback` was never written either
  - Two new helpers, `writeBorder` / `writeTooltip`, now write only the fields marked `*Specified`; the `defaults` block is still written in full since it is the single source of default values
- Fixed the batch sync buffer **silently dropping change operations** under backlog: `SyncBatchManager.addOperation` returned `true` without enqueueing once the pending count reached 1000. The "send immediately" promised by the comment only happened if callers honoured the return value, and every caller ignored it — so operations past the threshold were dropped until the next login or reload
  - Operations are now always enqueued and the threshold merely requests an immediate flush; the constant was renamed from `MAX_PENDING_OPERATIONS` to `URGENT_FLUSH_THRESHOLD`, which reflects what it always was
  - The three change registrations in `RarityRegistry` now go through a shared `enqueueChange`, which calls `DelayedSyncManager.flushPendingOperations()` when a flush is requested (that method only submits to a single-threaded executor — it does not block and cannot re-enter the batch lock)
- Fixed the Iron's Spellbooks switch causing **the server and client to compute different rarities**: `RarityRegistry.checkIronSpellbooksRarity` read `ClientConfigManager.isEnableIronSpellsAdapter()` during rarity resolution, but that key lives in the per-installation `config/raritycore/client.json`. In multiplayer, a server admin disabling their own copy (while investigating issue #14) made the server skip the Iron's Spellbooks branch while clients still took it, producing different results for the same item with no way for users to align them
  - The switch is now explicitly a **client-side display switch**: both sides resolve spell level identically, and disabling it only means "do not display locally". Public API (`RarityCoreAPI.getRarity()` and friends) still returns the true resolved value, so integration mods and KubeJS see identical data on either side
  - Compatibility adapter initialization is no longer gated on that switch (`CompatibilityManager`) — otherwise the side that disabled it would not even initialize the adapter and would resolve nothing, which is worse
  - Added `client.IronSpellsDisplaySwitch` as the single home for the switch; when it is on (the default) it returns on the first check, so the rendering hot path pays nothing
- Fixed Apotheosis and Iron's Spellbooks items always rendering at the lowest tier in the **border path**: `DualCacheManager` deliberately reports a miss for these items (to force live computation), and `ItemBorderRenderer` fell straight back to `defaults.noRarity.defaultRarity` on a miss. It now consults the per-item ID cache first and only then the default

### Other
- `ClientConfigManager` class documentation now states its role as **pure client-side display configuration**: its switches may only affect local display and must not take part in rarity resolution

## [1201.14.3]

### Fixed
- Fixed config options in `client.json` being silently deleted, which made manually added switches ineffective: the V14 migration routine `handleLegacyFiles()` rewrote the whole file as "`enableCacheSystem` only", wiping every other key (including the `enableIronSpellsAdapter` option added in 1201.14.1) on each startup and also swallowing keys added by hand, so the dynamic spell-level mapping of Iron's Spells could never be turned off
  - The routine now removes only the two dead keys `enableItemBorderRendering` and `enableTooltipInsert` (no remaining readers, identical defaults) and leaves every other key in the file — mod switches and user-defined keys alike — untouched
  - Initialization order is now "clean legacy keys → load RarityStyle → reload client config", so a newly added switch takes effect on the same startup
- Fixed `client.json` files generated by older versions missing newly added options: missing known keys are now written back with their default values on load, with a log entry, while everything else in the file is preserved. Existing installations no longer have to edit the config by hand to see the switch, and values the user set explicitly are never overwritten

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
