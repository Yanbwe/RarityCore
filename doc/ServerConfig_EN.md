# Server Configuration Guide

## Overview

This document explains the server-side configuration options for RarityCore mod. Server configurations control behaviors that affect how rarity data is processed and synchronized on the server side.

## Configuration File Location

The server configuration file is located at:
```
config/raritycore/server.json
```

## Configuration Options

### checkVanillaRarity
- **Type**: Boolean
- **Default**: `true`
- **Description**: Controls whether the mod checks vanilla item rarities and maps them to RarityCore's rarity system
- **Effect**: When enabled, items with vanilla rare/epic qualities will be automatically assigned corresponding RarityCore rarity levels

### skipUnconfiguredItems
- **Type**: Boolean
- **Default**: `false`
- **Description**: Controls whether to skip processing items that have no rarity configuration
- **Effect**: When enabled, items without explicit rarity configuration will be ignored during rendering and synchronization

## Configuration File Format

```json
{
  "checkVanillaRarity": true,
  "skipUnconfiguredItems": false
}
```

## Loading Priority

Server configurations are loaded in the following order:
1. Built-in defaults
2. `server.json` file (if exists)

## Commands

Server configuration can be managed through commands:

### Reload Server Configuration
```
/reload
```
This command reloads all server configurations including rarity data.

### View Current Configuration
Server configuration status can be viewed through various mod commands that display current settings.

## Best Practices

1. **Performance Considerations**: 
   - Enable `skipUnconfiguredItems` in large modpacks to improve performance
   - Disable `checkVanillaRarity` if you want full control over all rarity assignments

2. **Multiplayer Servers**:
   - Server configuration affects all connected clients
   - Changes require server restart or reload to take effect
   - Consider player experience when adjusting these settings

3. **Configuration Management**:
   - Backup configuration files before making changes
   - Test changes in single-player first
   - Document any custom configurations for server administrators

## Troubleshooting

### Common Issues

1. **Configuration Not Taking Effect**
   - Ensure the server has been restarted or reloaded
   - Check file permissions on the configuration file
   - Verify JSON syntax is correct

2. **Performance Problems**
   - Try enabling `skipUnconfiguredItems` 
   - Monitor server logs for configuration-related warnings

3. **Unexpected Behavior**
   - Check that configuration values are valid booleans
   - Verify the configuration file is in the correct location
   - Ensure no conflicting mods are modifying rarity behavior

## Migration Notes

If upgrading from older versions where these settings were in client configuration:
- The settings have been moved from `client.json` to `server.json`
- Old client configuration files will be automatically cleaned of these settings
- New server configuration file will be created with default values