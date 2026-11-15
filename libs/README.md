# 本地构建依赖

第三方 JAR 不提交到本仓库，也不会打包进本附属。请从各模组的官方发布页面获取 Minecraft 1.21.1 / NeoForge 对应版本，按下表放入本目录。

| 文件名 | 当前开发版本 / 来源 |
| --- | --- |
| `create.jar` | Create 6.0.10 |
| `pantographsandwires.jar` | Pantographs & Wires 1.21.1-beta-0.2.3-C6 |
| `electroenergetics.jar` | Create Electro Energetics 1.21.1-1.2.0-127 |
| `sable.jar` | Sable 2.0.6 |
| `veil.jar` | Veil 4.3.2 |
| `geckolib.jar` | GeckoLib 4.9.3 |
| `dragonlib.jar` | DragonLib 3.0.29 |
| `registrate.jar` | 从所用 Create JAR 的 `META-INF/jarjar/` 中提取 Registrate |
| `flywheel.jar` | 从所用 Create JAR 中提取 Flywheel 1.0.6 |
| `ponder.jar` | 从所用 Create JAR 中提取 Ponder 1.0.82 |
| `sable-companion.jar` | 从所用 Sable JAR 中提取 companion 1.6.0 |
| `sable-rapier.jar` | 从所用 Sable JAR 中提取 rapier |

支持的版本范围见 `src/main/templates/META-INF/neoforge.mods.toml`。依赖通过本地文件编译，只执行 `gradlew build` 不会自动下载这些模组。

准备完成后，在项目根目录执行 `./gradlew build`。需要 JDK 21。运行客户端/服务端测试时还应将原始运行依赖装入相应测试实例的 `mods/` 目录。
