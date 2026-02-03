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

## Configuration File Format

```json
{
  "checkVanillaRarity": true,
  "skipUnconfiguredItems": false
}
```
### Reload Server Configuration
```
/reload
```
This command reloads all server configurations, including rarity data.
