# Client Configuration Guide

The client configuration file is located at:

`config/raritycore/client.json`

## Configuration Options

### 1. enableItemBorderRendering
- **Type**: Boolean
- **Default**: true
- **Description**: Whether to enable the item border feature

### 2. itemBorderStyle
- **Type**: Integer
- **Default**: 1
- **Description**: Controls the style of item borders (only effective when texture border is disabled)
  - `0`: Hollow border
  - `1`: Solid fill

### 3. useTextureBorder
- **Type**: Boolean
- **Default**: true
- **Description**: Whether to use texture borders, will use custom textures to render borders when enabled

### 4. enableItemNameColor
- **Type**: Boolean
- **Default**: true
- **Description**: Whether to enable item name color feature, changes item name color according to item rarity

### 5. enableTooltipInsert
- **Type**: Boolean
- **Default**: true
- **Description**: Whether to enable tooltip insertion feature, displays rarity information in item tooltips

### 6. enableCacheSystem
- **Type**: Boolean
- **Default**: true
- **Description**: Whether to enable the cache system. Disable this if you encounter conflicts with other optimization mods

### How to Apply Configuration
#### Method 1: Restart the game

#### Method 2: Using Command
Send the following command in-game to immediately reload the client configuration:
```
/raritycore-client reload
```

### Texture Border Configuration
If texture borders are enabled (useTextureBorder=true), you need to prepare the corresponding texture files:
- Texture file path: `assets/raritycore/textures/border/`
- Texture file naming: `rarity_1.png` to `rarity_7.png`, corresponding to 7 rarity levels
- Texture size: 16x16 pixels

You can use the following command to toggle the texture border enable/disable state:
```
/raritycore-client texture toggle
```

### Cache System Control Commands
The cache system can be controlled through the following commands:

```
/raritycore-client cache enable    # Enable cache system
/raritycore-client cache disable   # Disable cache system
/raritycore-client cache toggle    # Toggle cache system state
```

**When to disable cache system:**
- When experiencing conflicts with other optimization mods
- When encountering memory issues
- When cache performance statistics show poor hit rates
- For troubleshooting rendering issues

### Configuration Version Management
The mod automatically manages configuration versions. When new configuration options are added, the system will automatically upgrade existing configuration files to the latest version, preserving existing settings while adding new options with their default values.

### Notes
If the configuration file becomes corrupted, you can delete the configuration file and restart the game to regenerate a default configuration file.

### Star Display
Star symbols are now permanently enabled in tooltips and cannot be configured. The default star emoji "⭐" will always be displayed.