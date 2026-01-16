# 稀有度配置指南

## **稀有度说明**

在开始为物品赋予稀有度前，你得知道本模组的稀有度是个什么东西。

正如名字表示，稀有度是一个物品的特征，它表示物品的稀有程度，物品越难获取，稀有度就越高。

而很多人将稀有度理解为物品的品质，这是不对的！**稀有度≠品质**！

一个物品在要花很大的代价，或要有很好的运气才能获得的情况下，哪怕这个物品没有任何实质性作用，他也配得上高稀有度。

而另一个物品，在用一个泥土合成，却能有比金苹果还厉害的效果的情况下，他也配不上高稀有度。

## **开始!**
在了解稀有度的含义后，你可以正式开始添加或修改物品的稀有度，可以用以下几种方法来进行：

### 1. 通过资源包添加
如果你是模组开发者，可以在资源包中添加`data/<命名空间>/rarity/任意名称.json`文件，内容格式如下：
```json
{
  "minecraft:item_id": rarity_value,
  "another_mod:item_id": rarity_value,
  ...
}
```

如果你想快速且直观地编写配置文件，可以使用下面方法4介绍的命令，先用命令修改物品稀有度，然后用命令导出符合格式的稀有度信息。

如果你需要覆盖本模组自带的稀有度信息，请在mods.toml中添加依赖项，以保证覆盖自带的稀有度信息：
```toml
[[dependencies.your_mod]]
   modId="raritycore"
   mandatory=false # 设置为可选依赖
   versionRange="[1.0,)"
   ordering="AFTER"  # 在raritycore之后加载
   side="BOTH"
```

### 2. 通过代码添加
如果你是模组开发者，可以通过调用`RarityRegistry.register(Item item, int rarity)`此公共方法直接注册，该方法在raritycore模组加载后的任意时机均可以使用，建议用于世界内临时修改稀有度。

### 3. 通过游戏的config添加
如果你是普通玩家或整合包开发者，可通过`config\raritycore\FinalRarity.json`注册或修改稀有度，内容格式与上述的资源包文件一致
需要注意的是，稀有度信息加载器会在最后加载此文件，因此在这里填写的稀有度信息会覆盖资源包中的稀有度信息。

### 4. 通过游戏命令添加
你可以在游戏内使用`/raritycore`命令添加或修改稀有度，目前包含以下命令：
1. `/raritycore sethand <rarity>` 设置当前手持物品的稀有度
2. `/raritycore setrarity <item> <rarity>` 设置指定物品的稀有度
3. `/raritycore reload` 重新加载所有稀有度配置，用于修改`FinalRarity.json`后的应用
4. `/raritycore export all/mod` 导出当前已注册的稀有度数据到文件

其中`/raritycore export all`会导出所有已注册的稀有度数据，而`/raritycore export mod <modid>`会导出指定模组的稀有度数据，文件会保存到 `config/raritycore/` 目录下，带有时间戳

5. `/raritycore details` 打印当前已注册的稀有度信息，如有多少个模组和物品拥有稀有度配置