Chinese
---
---

# 稀有度核心 Raritycore

一个简单的模组，提供七个稀有度等级，并在物品名称颜色、工具提示框、物品槽位背景体现物品的稀有度。

本模组自带原版和部分模组物品的稀有度配置，您可以通过指令、配置和模组等形式来添加或修改物品的稀有度。

如果您不是模组开发者，但是有好的稀有度配置 ，请将您的配置文件发送给我，我可能会将其添加到模组中。

## **构建说明**

如果您需要将模组导出为开发环境外可用的jar文件，请使用`./gradlew build -PenableReobf=true`，这将会在导出jar文件时启用重混淆，否则会导致游戏崩溃。

##  **配置说明**

如果您想添加或修改物品的稀有度，可以用以下几种方法来进行：
 ### 1. 通过资源包添加
如果您是模组开发者，可以在资源包中添加`data/<命名空间>/rarity/任意名称.json`文件，内容格式如下：
```json
{

  "minecraft:item_id": rarity_value,
  "another_mod:item_id": rarity_value,
  ...
}
```
如果您想快速且直观地编写配置文件，可以使用下面方法4介绍的命令，先用命令修改物品稀有度，然后用命令导出符合格式的稀有度信息

如果您需要覆盖本模组自带的稀有度信息，请在mods.toml中添加依赖项，以保证覆盖自带的稀有度信息：
```Toml
[[dependencies.your_mod]]
   modId="raritycore"
   mandatory=false # 设置为可选依赖
   versionRange="[1.0,)"
   ordering="AFTER"  # 在raritycore之后加载
   side="BOTH"
```
 ### 2. 通过代码添加
如果您是模组开发者，可以通过调用`RarityRegistry.register(Item item, int rarity)`此公共方法直接注册，该方法在raritycore模组加载后的任意时机均可以使用，建议用于世界内临时修改稀有度
 ### 3. 通过游戏的config添加
如果您是普通玩家或整合包开发者，可通过`config\raritycore\FinalRarity.json`注册或修改稀有度，内容格式与上述的资源包文件一致
需要注意的是，稀有度信息加载器会在最后加载此文件，因此在这里填写的稀有度信息会覆盖资源包中的稀有度信息
 ### 4. 通过游戏命令添加
您可以在游戏内使用`/raritycore`命令添加或修改稀有度，目前包含以下命令：
1. `/raritycore sethand <rarity> ` 设置当前手持物品的稀有度
2. `/raritycore setrarity <item> <rarity>`设置指定物品的稀有度
3. `/raritycore reload`重新加载所有稀有度配置，用于修改`FinalRarity.json`后的应用
4. `/raritycore export`导出当前所有已注册的稀有度数据到文件，文件会保存到 config/raritycore/ 目录下，带有时间戳

English
---
---
# Raritycore

A simple mod that offers seven rarity levels and reflects the rarity of items in the item name color, tool tip box, and item slot background.

This mod comes with pre-configured rarity settings for vanilla and some modded items. You can add or modify the rarity of items through commands, configurations, and mods.

If you are not a mod developer but have a good rarity configuration, please send me your configuration file, and I may add it to the mod.

## **Building Instructions**

If you need to export the mod as a jar file usable outside the development environment, please use `./gradlew build -PenableReobf=true`. This will enable reobfuscation during the export of the jar file; otherwise, it may cause the game to crash.

## **Configuration Instructions**

If you wish to add or modify the rarity of an item, you can do so using the following methods:
### 1. Addition via Resource Pack
If you are a mod developer, you can add a `data/<namespace>/rarity/any_name.json` file to the resource pack, with the content format as follows:
```json
{

  "minecraft:item_id": rarity_value,
  "another_mod:item_id": rarity_value,
  ...
}
```
If you want to quickly and intuitively write configuration files, you can use the command introduced in Method 4 below. First, use the command to modify the rarity of items, and then use the command to export the rarity information in the correct format

If you need to override the built-in rarity information of this mod, please add a dependency in mods.toml to ensure that the built-in rarity information is overridden:
```Toml
[[dependencies.your_mod]]
   modId="raritycore"
   mandatory=false # Set as an optional dependency
   versionRange="[1.0,)"
   ordering="AFTER"  # Load after raritycore
   side="BOTH"
```
### 2. Adding via Code
If you are a mod developer, you can directly register by calling the public method `RarityRegistry.register(Item item, int rarity)`. This method can be used at any time after the raritycore mod is loaded, and is recommended for temporarily modifying rarity within the world
### 3. Add through the game's config
If you are a regular player or a mod developer, you can register or modify rarity through `config\raritycore\FinalRarity.json`, with the content format consistent with the aforementioned resource pack file
It should be noted that the Rarity Information Loader will load this file last, so the rarity information entered here will overwrite the rarity information in the resource pack
### 4. Adding via game commands
You can use the `/raritycore` command in-game to add or modify rarity. Currently, the following commands are available:
1. `/raritycore sethand <rarity>` Set the rarity of the currently held item
2. `/raritycore setrarity <item> <rarity>` sets the rarity of the specified item
3. `/raritycore reload` reloads all rarity configurations, intended for use after modifying `FinalRarity.json`
4. `/raritycore export` exports all currently registered rarity data to a file, which will be saved to the config/raritycore/ directory with a timestamp
