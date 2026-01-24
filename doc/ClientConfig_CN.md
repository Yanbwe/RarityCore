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
- **说明**: 是否启用工具提示插入功能，在物品描述中插入稀有度信息

### 6. enableItemBackgroundRendering
- **类型**: 布尔值
- **默认值**: false
- **说明**: 是否启用物品背景渲染功能，为物品渲染背景颜色

### 如何应用配置
#### 方法1：重启游戏

#### 方法2：用命令
在游戏里发送以下命令可以立即重新加载客户端配置：
```
/raritycore-client
```

### 纹理边框配置
如果启用纹理边框（useTextureBorder=true），需要准备对应的纹理文件：
- 纹理文件路径：`assets/raritycore/textures/border/`
- 纹理文件命名：`rarity_1.png` 至 `rarity_7.png`，对应7个稀有度等级
- 纹理尺寸：16x16像素

可以使用以下命令切换纹理边框的启用/禁用状态：
```
/raritycore-texture toggle
```

### 注意事项
如果配置文件损坏，可以把配置文件删了，然后重启游戏就可以重新生成一份默认配置