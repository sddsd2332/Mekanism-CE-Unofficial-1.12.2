# GroovyScript 示例

将 `preInit/mekanism_gas_registration.groovy` 复制到实例的 `groovy/preInit` 目录，将 `postInit/mekanism_infusion_registration.groovy` 复制到实例的 `groovy/postInit` 目录。默认的 GroovyScript `runConfig.json` 已经包含这两个目录；如果整合包自定义了 `runConfig.json`，请确保它们仍然分别被加入 `preInit` 和 `postInit` loader。

GRS 的 Mekanism 气体注册表只允许在 `preInit` 修改。示例同时展示了创建带流体的气体，以及把已有气体映射到其他模组流体的两种写法。后两行是注释，避免复制后覆盖现有氧气配置。

灌注注册使用 GRS 上游 Mekanism 兼容层的 `mods.mekanism.infusion`，默认在 `postInit` 执行。CRT 的 `mods.mekanism.infuse` 是本仓库新增的原生接口，两者名称和加载阶段不同。

上游灌注注册表的重载状态为 `FLAWED`；修改自定义灌注类型后建议完整重启客户端和服务端，并确保自定义纹理在两端都能加载。
