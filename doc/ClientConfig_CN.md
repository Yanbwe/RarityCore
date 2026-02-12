# 客户端配置说明

客户端配置文件位于：

`config/raritycore/client.json`

## 配置选项说明

### 1. enableItemBorderRendering
- **类型**: 布尔值
- **默认值**: true
- **说明**: 是否启用物品边框功能

### 2. itemBorderStyle
- **类型**: 整数
- **默认值**: 1
- **说明**: 控制物品边框的样式（仅在未启用纹理边框时生效）
  - `0`: 空心边框
  - `1`: 实心填充

### 3. useTextureBorder
- **类型**: 布尔值
- **默认值**: true
- **说明**: 是否使用纹理边框，启用后将使用自定义纹理渲染边框

### 4. enableItemNameColor
- **类型**: 布尔值
- **默认值**: true
- **说明**: 是否启用物品名称变色功能，根据物品稀有度改变物品名称颜色

### 5. enableTooltipInsert
- **类型**: 布尔值
- **默认值**: true
- **说明**: 是否启用工具提示插入功能，在物品提示中显示稀有度信息

### 6. enableCacheSystem
- **类型**: 布尔值
- **默认值**: true
- **说明**: 是否启用缓存系统。如果与其他优化模组发生冲突，请禁用此选项

### 如何应用配置
#### 方法1：重启游戏

#### 方法2：用命令
在游戏里发送以下命令可以立即重新加载客户端配置：
```
/raritycore-client reload
```

### 纹理边框配置
如果启用纹理边框（useTextureBorder=true），需要准备对应的纹理文件：
- 纹理文件路径：`assets/raritycore/textures/border/`
- 纹理文件命名：`rarity_1.png` 至 `rarity_7.png`，对应7个稀有度等级
- 纹理尺寸：16x16像素

可以使用以下命令切换纹理边框的启用/禁用状态：
```
/raritycore-client texture toggle
```

### 缓存系统控制命令
可以通过以下命令开关缓存系统：

```
/raritycore-client cache enable    # 启用缓存系统
/raritycore-client cache disable   # 禁用缓存系统
/raritycore-client cache toggle    # 切换缓存系统状态
```

**何时禁用缓存系统：**
- 与其他优化模组发生冲突时
- 遇到内存问题时
- 缓存性能统计显示命中率较低时
- 排查渲染问题时

### 配置版本管理
模组会自动管理配置版本。当添加新的配置选项时，系统会自动将现有的配置文件升级到最新版本，在保留现有设置的同时添加新选项及其默认值。

### 注意事项
如果配置文件损坏，可以把配置文件删了，然后重启游戏就可以重新生成一份默认配置
