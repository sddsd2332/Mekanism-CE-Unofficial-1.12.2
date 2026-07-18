# CraftTweaker 示例

将这两个 `.zs` 文件复制到实例的 `scripts` 目录。CraftTweaker 的 `mekanism` loader 脚本会在普通脚本之前执行，因此灌注类型会先于灌注物品映射注册。

文件说明：

- `01_mekanism_gas_registration.zs`：注册一个自定义气体和它的流体，并注册一个自定义灌注类型。
- `02_mekanism_infuse_registration.zs`：把物品注册为指定灌注类型的灌注物品。

`setFluid` 的示例调用全部保留为注释。取消注释前请确认目标流体已经存在，并且只保留需要的一种映射方式。
