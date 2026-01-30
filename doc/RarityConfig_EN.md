# Rarity Configuration Guide

## **Configuration Instructions**

Before starting to assign rarities to items, you need to understand what rarity means in this mod.

As the name suggests, rarity is a characteristic of an item that represents how rare the item is. The harder an item is to obtain, the higher its rarity should be.

Many people misunderstand rarity as representing item quality, which is wrong! **Rarity ≠ Quality**!

An item deserves a high rarity if it requires great effort or good luck to obtain, even if the item has no practical use.

On the other hand, an item that can be crafted with just dirt but has effects superior to a golden apple does not deserve a high rarity despite its utility.

After understanding the meaning of rarity, you can officially begin adding or modifying item rarities using the following methods:

### 1. Adding via Resource Pack
If you are a mod developer, you can add `data/<namespace>/rarity/any_name.json` files in your resource pack, with the following format:
```
{
  "minecraft:item_id": rarity_value,
  "another_mod:item_id": rarity_value,
  ...
}
```

If you want to quickly and intuitively write configuration files, you can use the commands introduced in method 4 below. First use commands to modify item rarities, then export the properly formatted rarity information using the export command.

If you need to override the built-in rarity information of this mod, add a dependency in your mods.toml to ensure it overrides the built-in rarity information:
```toml
[[dependencies.your_mod]]
   modId="raritycore"
   mandatory=false # Set as optional dependency
   versionRange="[1.0,)"
   ordering="AFTER"  # Load after raritycore
   side="BOTH"
```

### 2. Adding via Code
If you are a mod developer, you can directly register items by calling the public method `RarityRegistry.register(Item item, int rarity)`. This method can be used at any time after the raritycore mod is loaded, and is recommended for temporary in-world rarity modifications.

### 3. Adding via Game Config
If you are a regular player or modpack developer, you can register or modify rarities via two methods:

**Method 1: FinalRarityConfig Folder**
Place JSON configuration files in the `config\raritycore\FinalRarityConfig\` folder. File names can be arbitrary, and the format is consistent with resource pack files. The mod will load all JSON files in alphabetical order by filename.

**Method 2: FinalRarity.json File**
Register or modify rarities via `config\raritycore\FinalRarity.json`. The format is consistent with resource pack files mentioned above.

**Loading Priority:**
The mod loads rarity configurations in the following order, where later-loaded configurations will override earlier-loaded configurations for the same items:
1. Rarity configurations in datapacks
2. JSON files in the FinalRarityConfig folder (in alphabetical order by filename)
3. FinalRarity.json file

Therefore, configurations in FinalRarity.json have the highest priority.

### 4. Adding via Game Commands
You can use the `/raritycore` command in-game to add or modify rarities. Currently includes the following commands (OP only):
1. `/raritycore sethand <rarity>` Sets the rarity of the currently held item
2. `/raritycore setrarity <item> <rarity>` Sets the rarity of a specified item
3. `/raritycore reload` Reloads all rarity configurations, will load FinalRarityConfig folder and FinalRarity.json file in order
4. `/raritycore export all/mod` Exports currently registered rarity data to files
   - `all`: Exports all rarity data to a single file
   - `mod <modid>`: Exports rarity data for a specific mod
   - `all-mod`: Exports rarity data for all mods to separate files (filename format: {modid}_{mcversion}_{timestamp}.json)
5. `/raritycore edit <enable/disable/toggle/status>` Controls edit mode enable, disable, toggle and status viewing

Additionally, there are the following standalone commands (available to all players):
- `/raritycore-client` Reloads client configuration
- `/raritycore-texture toggle` Toggles texture border enable status

     The `/raritycore export all` command exports all registered rarity data, while `/raritycore export mod <modid>` exports rarity data for a specific mod. Files are saved to the `config/raritycore/` directory with timestamps.

5. `/raritycore details` Prints currently registered rarity information, such as how many mods and items have rarity configurations.