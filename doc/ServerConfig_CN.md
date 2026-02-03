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

## 配置文件格式

```json
{
  "checkVanillaRarity": true,
  "skipUnconfiguredItems": false
}
```
### 重新加载服务端配置
```
/reload
```
此命令重新加载所有服务端配置，包括稀有度数据。
