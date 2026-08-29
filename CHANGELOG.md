# RarityCore 更新日志

## [1211.14.6] - 2026-08-28

### 新增
- FTB Library 兼容（issue #22）：FTB Quests 任务界面中的任务图标、奖励图标、奖励表图标现在会渲染稀有度边框
  - 新增软依赖 mixin `ftblibrary.ItemIconMixin`（`@Pseudo`，仿照 Refined Storage 兼容方案），挂载 FTB Library `ItemIcon#draw / #drawStatic`
  - 边框按图标实际绘制尺寸缩放渲染（支持非 16x16 尺寸，如任务面板 12x12 / 32x32）
  - 渲染期间抑制 `GuiGraphicsMixin` 内部钩子，避免边框重复绘制
  - 名称颜色经由 `ItemStack#getHoverName` 已全局生效，FTB 界面无需额外处理

### 配置
- `client.json` 新增 `enableFtbLibraryAdapter`（默认开启），可禁用 FTB Library 稀有度边框联动

### 构建与其他
- 更新模组版本至 1211.14.6

## [1211.14.5] - 2026-08-10

### 修复
- 修复缓存"组件感知"问题：带组件稀有度控制（`raritycore.Level`）的物品堆改为按 NBT 哈希走组件缓存，不再被 ID 缓存互相污染（修复"所有枪械一起变"根因）

### 调整
- 组件稀有度控制改为"半开"：检测到 ModularRarity 后，仅对 ModularShoot 提供的枪械（`modularshoot:gun`）与插件（`modularshoot:plugin`）物品执行组件稀有度读取，其他物品 O(1) 短路跳过，消除 `copyTag` 全量 NBT 复制的热路径开销（绑定物品稀有度由 `registerRarity` 提供，不受影响）

### 构建与其他
- 更新模组版本至 1211.14.5

## [1211.14.4] - 2026-08-10

### 新增
- 支持 TacZ 物品的编辑模式，自动生成子物品匹配配置
- 编辑模式面板支持鼠标拖拽移动（位置不持久化，重启后复位）
- 新增 `enableIronSpellsAdapter` 配置开关，可禁用铁魔法（Iron Spells）稀有度联动（issue #14）

### 修复
- 修复面板外按下拖动时误触面板移动（增加面板内按下守卫）
- 渲染时钳制面板位置，面板外按下复位拖动状态

### 构建与其他
- 更新模组版本至 1211.14.4，修复相关配置
- 修改发布工作流以支持版本标签推送