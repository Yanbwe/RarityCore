# 服务端配置说明

## 配置文件位置

服务端配置文件位于：
```
config/raritycore/server.json
```

## 配置选项

### checkVanillaRarity
- **类型**: 布尔值
- **默认值**: `true`
- **说明**: 控制是否检查原版物品稀有度并映射到 RarityCore 的稀有度系统
- **效果**: 启用时，具有原版稀有/史诗品质的物品将自动分配对应的 RarityCore 稀有度等级

### skipUnconfiguredItems
- **类型**: 布尔值
- **默认值**: `false`
- **说明**: 控制是否跳过处理没有稀有度配置的物品
- **效果**: 启用时，在渲染和同步过程中将忽略没有显式稀有度配置的物品

### checkApotheosisRarity
- **类型**: 布尔值
- **默认值**: `true`
- **说明**: 控制是否检查神化模组物品稀有度并映射到 RarityCore 的稀有度系统
- **效果**: 启用时，具有神化稀有度的物品将自动分配对应的 RarityCore 稀有度等级

## 配置文件格式

```json
{
  "config_version": 3,
  "mod_version": "raritycore-1201.4.1",
  "checkVanillaRarity": true,
  "skipUnconfiguredItems": false,
  "checkApotheosisRarity": true
}
```
### 重新加载服务端配置
```
/reload
```
此命令重新加载所有服务端配置，包括稀有度数据。

### 查看配置版本
```
/raritycore config version
```
此命令显示当前配置版本信息。

### 强制配置升级
```
/raritycore config upgrade
```
此命令强制将所有配置文件升级到最新版本。
