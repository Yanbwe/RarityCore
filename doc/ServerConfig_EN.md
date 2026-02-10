# Server Configuration Instructions

## Configuration File Location

The server configuration file is located at:
```
config/raritycore/server.json
```

## Configuration Options

### checkVanillaRarity
- **Type**: Boolean
- **Default**: `true`
- **Description**: Controls whether to check the rarity of vanilla items and map them to the RarityCore rarity system
- **Effect**: When enabled, items with vanilla Rare/Epic quality will be automatically assigned a corresponding RarityCore rarity level

### skipUnconfiguredItems
- **Type**: Boolean
- **Default**: `false`
- **Description**: Controls whether to skip processing items without rarity configuration
- **Effect**: When enabled, items without explicit rarity configuration will be ignored during the rendering and synchronization processes

### checkApotheosisRarity
- **Type**: Boolean
- **Default**: `true`
- **Description**: Controls whether to check Apotheosis mod rarity and map it to the RarityCore rarity system
- **Effect**: When enabled, items with Apotheosis rarity will be automatically assigned a corresponding RarityCore rarity level

### prioritizeApotheosisRarity
- **Type**: Boolean
- **Default**: `false`
- **Description**: Controls whether Apotheosis mod rarity takes priority over RarityCore configuration
- **Effect**: When enabled, Apotheosis rarity will override local RarityCore configuration for the same items

## Configuration File Format

```json
{
  "config_version": 3,
  "mod_version": "raritycore-1201.4.1",
  "checkVanillaRarity": true,
  "skipUnconfiguredItems": false,
  "checkApotheosisRarity": true
}
```
### Reload Server Configuration
```
/reload
```
This command reloads all server configurations, including rarity data.

### View Configuration Version
```
/raritycore config version
```
This command displays the current configuration version information.

### Force Configuration Upgrade
```
/raritycore config upgrade
```
This command forces an upgrade of all configuration files to the latest version.
