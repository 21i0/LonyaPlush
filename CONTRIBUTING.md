# 给 Lonya 的修改指南

这是可独立构建的 Minecraft 1.21.1 / NeoForge 模组，不需要原万象担架工程。

## 在 GitHub 上修改并下载新 JAR

1. 打开需要修改的文件，点铅笔编辑，保存到自己的分支或 Fork。没有仓库写入权限时，用 Fork 和 Pull Request 提交修改。
2. 打开你的仓库的 **Actions → Build Lonya plush**。Fork 首次使用可能需要先启用 Actions；也可以点 **Run workflow** 手动构建。
3. 等这次构建变绿，打开运行记录，在 **Artifacts** 下载 `lonya-plush-数字`。
4. 解压下载的 ZIP，里面的 `.jar` 就是可安装模组。把游戏 `mods` 目录中的旧 `lonya_plush` JAR 替换掉；联机时服务端和客户端一起更新。

GitHub 网页上下载 Actions artifact 通常需要登录。`dist/` 只保留本次交接的 1.1.2 成品，不会随每次 Actions 构建自动更新；以后下载最新构建请看 Actions。

## 改剧情与闲聊

主线文件为 `src/main/resources/assets/lonya_plush/story/chapter1.json` 至 `chapter5.json`；日常闲聊文件为同目录的 `causal.json`。每个主线 JSON 对应一章，直接编辑即可配置对白、选项的正负好感度变化、选项后独立路线与汇合对白，以及闲聊的表情和好感度范围。完整示例见 [剧情配置说明](docs/STORY_FORMAT.md)。修改后需重新打包，并同时更新客户端、服务端后重启。

主线已移除 `expression` 和 `minimum_ms`；自动播放时长由代码按文字长度计算，不再要求每章固定五分钟或固定段落数。现有五章、章节解锁顺序和 20／40／60／80／100 好感度门槛保留。第四章选项已配置为 JSON 分支，其他章节不额外添加结尾选项。

`tools/rewrite_story.py` 中保留第 2–5 章的双人对白，可用 Python 3 恢复这些章节的原始文本。它会保留各章 JSON 中的 id、选项、分支和追加的共同对白，但会覆盖原始段落位置的文字；已直接修改章节 JSON 或调整段落顺序时不要直接运行，运行前先备份。第一章以 `origin_story.md` 与 `chapter1.json` 为准；日常闲聊不由此脚本生成。

## 换皮肤与眨眼

资源目录：`src/main/resources/assets/lonya_plush/textures/entity/`。

同时替换 `skin.png`、`skin_blink_half.png`、`skin_blink_closed.png`、`skin.json`。图片使用 64×64 或同等整数倍高清方形 Steve/Alex 皮肤；原图、半闭眼和闭眼图的尺寸必须一致。三张 PNG 一起更新，避免睁眼换了皮肤、闭眼却仍使用旧皮肤。

可用独立的 DollSkinBlinkTool 图形工具选择眼区、修改闭眼像素后导出。该工具属于独立工程，不包含在本仓库；不使用工具也可用图片编辑器修改这几张 PNG。

`skin.json` 的 `blink_enabled` 可开启或关闭眨眼；`preview_variants` 指定半闭眼与闭眼 PNG。`source` 是导出来源说明，游戏不读取个人电脑上的路径。

## 改彩虹伞与 CG

伞面位于同一目录的 `umbrella.png`、`umbrella.json`；`panels` 中八个十六进制颜色对应八个扇区，`fabric_a`、`fabric_b`、`trim` 是配套色。工具导出时一起复制 PNG 和 JSON。

CG 位于 `src/main/resources/assets/lonya_plush/textures/story/chapter_1.png` 到 `chapter_5.png`。建议保持原图尺寸：前四章 1672×941，第五章 1536×1024。界面按比例裁剪铺满全屏；改变原图尺寸时还需同步 `LonyaScreen.cover` 的尺寸常量。

## 设置章节冷却

游戏首次启动后，编辑实例或服务器根目录的 `config/lonya_plush-common.toml`：

```toml
[chat]
cooldownEnabled = true
cooldownSeconds = 1200
```

`false` 关闭冷却；`1200` 为 20 分钟，`300` 为 5 分钟，`0` 无需等待。保存并重启游戏或服务器。此配置仅控制章节拜访，日常闲聊的一分钟好感度奖励规则不受影响。冷却由服务端按玩家保存，离线时间也计入；历史、CG 和回收不受影响。源码默认值在 `LonyaStoryConfig.java`，配置示例在 `config-examples/`。

## 本地构建与调试

安装 JDK 21，让 `java -version` 显示 21，并设置 `JAVA_HOME` 为你的 JDK 目录。无需预装 Gradle，仓库内有 Wrapper。第一次构建需要联网下载 NeoForge 和 Minecraft 依赖。

Windows：

```powershell
.\gradlew.bat build --console=plain
.\gradlew.bat runClient --console=plain
```

Linux/macOS：

```bash
./gradlew build --console=plain
./gradlew runClient --console=plain
```

输出 `build/libs/lonya_plush-版本.jar` 可直接安装；`-sources.jar` 是源码包。历史开发检查代码在 `src/checks/`，用 `-Pchecks` 启用；其中固定时长、旧选项和旧闲聊冷却的断言适用于旧版规则，不能作为本次 JSON 分支版本的验收依据。成品 JAR 会排除检查代码。

改动后建议递增 `gradle.properties` 的 `mod_version`，并同步 `customization.json`。改游戏显示名可编辑 `gradle.properties` 的 `mod_name` 及 `assets/lonya_plush/lang/` 下的语言文件。

只更新 Lonya 版本时保留 `lonya_plush` 和现有注册名，旧物品与进度才能继续识别。如果要另做一个能同时安装的定制版本，必须一起修改 mod ID、Java 包名、资源命名空间、注册名、网络标识及资源路径。
