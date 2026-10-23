# 给 Lonya 的修改指南

这是可独立构建的 Minecraft 1.21.1 / NeoForge 模组，不需要原万象担架工程。

## 在 GitHub 上修改并下载新 JAR

1. 打开需要修改的文件，点铅笔编辑，保存到自己的分支或 Fork。没有仓库写入权限时，用 Fork 和 Pull Request 提交修改。
2. 打开你的仓库的 **Actions → Build Lonya plush**。Fork 首次使用可能需要先启用 Actions；也可以点 **Run workflow** 手动构建。
3. 等这次构建变绿，打开运行记录，在 **Artifacts** 下载 `lonya-plush-数字`。
4. 解压下载的 ZIP，里面的 `.jar` 就是可安装模组。把游戏 `mods` 目录中的旧 `lonya_plush` JAR 替换掉；联机时服务端和客户端一起更新。

GitHub 网页上下载 Actions artifact 通常需要登录。`dist/` 只保留本次交接的 1.1.2 成品，不会随每次 Actions 构建自动更新；以后下载最新构建请看 Actions。

## 改剧情

最终游戏文本在 `src/main/resources/assets/lonya_plush/story/chapters.json`。网页上可以直接修改 `text`、`title`、`summary`。只改这些文字时，保持 JSON 语法和原有行序号、结构、`minimum_ms` 不变即可。

更适合整章写作的源文件是 `tools/rewrite_story.py`。修改其中三引号里的对白后，在电脑的仓库目录执行：

```powershell
py -X utf8 tools/rewrite_story.py
```

Linux/macOS 使用 `python3 tools/rewrite_story.py`。将脚本与生成的 `chapters.json` 一起提交。运行脚本会重写 JSON，之前如果只在 JSON 中改过文字，需要先同步到脚本。

现版的五章行数是 61、60、59、59、64。脚本要求编号从 00 连续排列并保持这些行数；需要增减段落时，同步修改脚本的 `EXPECTED`。游戏加载器要求五章、每章至少 30 段，且该章 `minimum_ms` 合计为 300000（5 分钟的自动播放建议）。脚本会自动分配时长；手动翻页和快进没有句内强制等待。

现有剧情约定：对白只有 Lonya 和玩家，旁白用“旁白”；第四、第五章是手机通话。第四章第一项解锁第五章，另一项结束本次路线；在解锁前不展示第五章。若只改台词，保留这些章节关系即可。

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

`false` 关闭冷却；`1200` 为 20 分钟，`300` 为 5 分钟，`0` 无需等待。保存并重启游戏或服务器。冷却由服务端按玩家保存，离线时间也计入；历史、CG 和回收不受影响。源码默认值在 `LonyaStoryConfig.java`，配置示例在 `config-examples/`。

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

输出 `build/libs/lonya_plush-版本.jar` 可直接安装；`-sources.jar` 是源码包。开发检查代码在 `src/checks/`，用 `-Pchecks` 启用；成品 JAR 会排除检查代码。

改动后建议递增 `gradle.properties` 的 `mod_version`，并同步 `customization.json`。改游戏显示名可编辑 `gradle.properties` 的 `mod_name` 及 `assets/lonya_plush/lang/` 下的语言文件。

只更新 Lonya 版本时保留 `lonya_plush` 和现有注册名，旧物品与进度才能继续识别。如果要另做一个能同时安装的定制版本，必须一起修改 mod ID、Java 包名、资源命名空间、注册名、网络标识及资源路径。
