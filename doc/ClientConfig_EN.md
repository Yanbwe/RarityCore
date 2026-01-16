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
- **Description**: Controls the style of item borders
  - `0`: Hollow border
  - `1`: Solid fill

### How to Apply Configuration
#### Method 1: Restart the game

#### Method 2: Using Command
Send the following command in-game to immediately reload the client configuration:
```
/raritycore-client
```

### Notes
If the configuration file becomes corrupted, you can delete the configuration file and restart the game to regenerate a default configuration file.