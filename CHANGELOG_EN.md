# RarityCore Changelog

## [2601.13.7] - 2026-08-16

### Added
- V14 config integration: added `RarityStyle.json` to centralize color, border, tooltip, star and no-rarity fallback settings; `client.json` is trimmed to only the cache and Sophisticated Core adapter switches; legacy `RarityClientConfig.json` is automatically removed on startup
- Removed the 7-level rarity cap: rarities 8+ now use the same inheritance/fallback path as levels 1-7, supporting higher-level tooltips, borders and colors
- Public API upgraded to V14: added global switches, per-level visual read/write, `RarityConfigReloadEvent` / `RarityStyleChangedEvent` / `RarityRegistryChangedEvent`, collection queries, batch registration, style snapshots and version awareness
- Level names can now be shown through translation keys (default `rarity.core.1` ~ `rarity.core.7`)

### Fixed
- Fixed tooltips showing the raw Arabic numeral as the level name: `@{level}` now resolves to localized text via the `translationKey` in `RarityStyle.json`, falling back to `fallback` when the translation is missing

### Changed
- Removed legacy `RarityClientConfig`, `StarDisplayConfigManager` and named rarity constants
- Unified command/reload flows with `RarityStyleConfigManager` and `RarityConfigReloadEvent`

### Build & Other
- 26.1 and 26.2 share the same source and build separately as `raritycore-2601.13.7.jar` and `raritycore-2602.13.6.jar`

## [2602.13.6] - 2026-08-16

### Added
- V14 config integration: added `RarityStyle.json` to centralize color, border, tooltip, star and no-rarity fallback settings; `client.json` is trimmed to only the cache and Sophisticated Core adapter switches; legacy `RarityClientConfig.json` is automatically removed on startup
- Removed the 7-level rarity cap: rarities 8+ now use the same inheritance/fallback path as levels 1-7, supporting higher-level tooltips, borders and colors
- Public API upgraded to V14: added global switches, per-level visual read/write, `RarityConfigReloadEvent` / `RarityStyleChangedEvent` / `RarityRegistryChangedEvent`, collection queries, batch registration, style snapshots and version awareness
- Level names can now be shown through translation keys (default `rarity.core.1` ~ `rarity.core.7`)

### Fixed
- Fixed tooltips showing the raw Arabic numeral as the level name: `@{level}` now resolves to localized text via the `translationKey` in `RarityStyle.json`, falling back to `fallback` when the translation is missing

### Changed
- Removed legacy `RarityClientConfig`, `StarDisplayConfigManager` and named rarity constants
- Unified command/reload flows with `RarityStyleConfigManager` and `RarityConfigReloadEvent`

### Build & Other
- 26.1 and 26.2 share the same source and build separately as `raritycore-2601.13.7.jar` and `raritycore-2602.13.6.jar`
