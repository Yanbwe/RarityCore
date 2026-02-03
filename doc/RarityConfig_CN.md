# 稀有度配置指南
# RarityCore 稀有度配置文档

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
```
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
如果你是普通玩家或整合包开发者，可通过以下两种方式注册或修改稀有度：

**方式一：FinalRarityConfig文件夹**
在`config\raritycore\FinalRarityConfig\`文件夹中放置JSON配置文件，文件名任意，内容格式与资源包文件一致。模组会按文件名字母顺序加载所有JSON文件。

**方式二：FinalRarity.json文件**
通过`config\raritycore\FinalRarity.json`注册或修改稀有度，内容格式与资源包文件一致。

**加载优先级说明：**
模组按照以下顺序加载稀有度配置，后加载的会覆盖先加载的同名物品配置：
1. 数据包中的稀有度配置
2. FinalRarityConfig文件夹中的JSON文件（按文件名字母顺序）
3. FinalRarity.json文件

因此FinalRarity.json中的配置具有最高优先级。

### 4. 通过游戏命令添加
你可以在游戏内使用命令添加或修改稀有度，分为OP命令和客户端命令：

**OP专用命令 (`/raritycore`)：**
1. `/raritycore sethand <rarity>` 设置当前手持物品的稀有度
2. `/raritycore removehand` 删除当前手持物品的稀有度配置
3. `/raritycore setrarity <item> <rarity>` 设置指定物品的稀有度
4. `/raritycore removerarity <item>` 删除指定物品的稀有度配置
5. `/raritycore reload` 重新加载所有稀有度配置
6. `/raritycore export all` 导出所有稀有度数据到单个文件
7. `/raritycore export mod <modid>` 导出指定模组的稀有度数据
8. `/raritycore export all-mod` 导出所有模组的稀有度数据到独立文件
9. `/raritycore details` 显示当前已注册的稀有度统计信息
10. `/raritycore perf stats` 显示性能统计信息
11. `/raritycore perf optimize` 触发手动优化
12. `/raritycore edit enable/disable/toggle/status` 编辑模式控制
13. `/raritycore server reload` 重新加载服务端配置
14. `/raritycore server status` 显示服务端配置状态

**客户端命令 (`/raritycore-client`)：**
1. `/raritycore-client reload` 重新加载客户端配置
2. `/raritycore-client cache stats` 显示缓存统计信息
3. `/raritycore-client cache clear` 清除渲染缓存
4. `/raritycore-client cache health` 显示缓存健康状态
5. `/raritycore-client cache smart-optimize` 触发智能缓存优化
6. `/raritycore-client texture toggle` 切换纹理边框启用状态

**命令使用说明：**
- 所有导出文件都会保存到 `config/raritycore/` 目录下，并带有时间戳
- 编辑模式下可以通过 Ctrl+数字键快速修改物品稀有度
- 客户端命令可供所有玩家使用，无需OP权限

   其中`/raritycore export all`会导出所有已注册的稀有度数据，而`/raritycore export mod <modid>`会导出指定模组的稀有度数据，文件会保存到 `config/raritycore/` 目录下，带有时间戳

5. `/raritycore details` 打印当前已注册的稀有度信息，如有多少个模组和物品拥有稀有度配置

## **编辑模式详细使用说明**

### 启用编辑模式

1. **开启编辑模式**
   ```bash
   /raritycore edit enable
   ```
   执行后会在聊天框显示"编辑模式已启用"的提示信息。

2. **确认状态**
   ```bash
   /raritycore edit status
   ```
   可以随时检查当前编辑模式是否处于启用状态。

### 使用编辑模式

启用编辑模式后，您可以按以下方式修改物品稀有度：

#### 基本操作流程
1. 在任意游戏界面中（物品栏、箱子、工作台等）
2. 点击想要修改稀有度的物品
3. 按住 `Ctrl` 键同时按下对应的数字键来切换稀有度等级，也可以开启删除模式来删除稀有度。

#### 稀有度等级对应关系
- `Ctrl + 1` - 等级 1
- `Ctrl + 2` - 等级 2
- `Ctrl + 3` - 等级 3
- `Ctrl + 4` - 等级 4
- `Ctrl + 5` - 等级 5
- `Ctrl + 6` - 等级 6
- `Ctrl + 7` - 等级 7
- `Ctrl + 0` - 删除模式

#### 其他说明

- 编辑模式下的修改会像命令那样立即应用并自动保存到 `config/raritycore/FinalRarity.json` 文件中；
- 
  完成编辑后记得关闭编辑模式~
```bash
/raritycore edit disable
```


### 5. 最后
说实话，基本所有模组作者肯定不会多花力气去在自己的模组里编写稀有度配置，只能靠本模组自带配置去支持。

所以如果你愿意的话，你可以通过b站，mc百科，github等地方联系我，把写好的配置发给我，谢谢！

即使你不想编写配置，你也可以给我留言你想让本模组支持哪些模组，我会尽快去编写的！
