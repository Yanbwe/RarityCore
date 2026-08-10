# RarityCore 更新日志

## [1201.14.1] - 2026-08-10

### 新增
- 编辑模式面板支持鼠标拖拽移动（位置不持久化，重启后复位）
- 新增 FTB Library 物品边框渲染兼容（Mixin + 兼容管理器登记）
- 新增 `enableIronSpellsAdapter` 配置开关，可禁用铁魔法（Iron Spells）稀有度联动（issue #14）

### 修复
- 修复与精致存储（Refined Storage）的渲染联动失效
- 修复编辑模式折叠面板点击误触发模式切换，恢复右键物品槽编辑
- 修复面板外按下拖动时误触面板移动（增加面板内按下守卫）
- 渲染时钳制面板位置，避免面板拖出屏幕边界

### 构建与其他
- 更新构建和发布工作流，添加版本解析与发布功能
- 更新 .gitignore 以排除新增构建文件

[1201.14.1]: https://github.com/YanbweMod/RarityCore/releases/tag/1201.14.1
