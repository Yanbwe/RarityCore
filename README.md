
 [>Chinese中文版点这里查看点这里点这里<](doc/README_CN.md)
---
# RarityCore

A simple mod that provides seven rarity levels and reflects item rarity in item name colors, tooltips, and item slot backgrounds.

This mod comes with built-in rarity configurations for vanilla and some modded items. You can add or modify item rarities through commands, configurations, and mods.

If you are not a mod developer but have good rarity configurations, please send your configuration files to me, and I may add them to the mod.

## **Build Instructions**

If you need to export the mod as a JAR file usable outside the development environment, use `./gradlew build -PenableReobf=true`. This will enable re-obfuscation when exporting the JAR file, otherwise it will cause the game to crash.

## **Configuration Guide**

For detailed configuration instructions, please refer to the following documents:

### Configuration Guides
- [Rarity Configuration Guide (English)](doc/RarityConfig_EN.md) - Detailed instructions for configuring item rarities
- [Client Configuration Guide (English)](doc/ClientConfig_EN.md) - Client-specific configuration options
- [Server Configuration Guide (English)](doc/ServerConfig_EN.md) - Server-side configuration options

### Reference Documents
- [Supported Mods List (English)](doc/SupportedModsList.md) - List of mods with pre-configured rarity information

### API Documentation
- [API Documentation (English)](doc/API_Documentation_EN.md) - Complete API reference for developers
