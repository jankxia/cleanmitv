# CleanMiTV · 小米电视的干净桌面

> 基于 [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) 二次开发，针对**国行小米电视**（实测 MiTV S65 Mini LED / MiTV-MFTR0，Android 14，小米 OS3）做了本地化和视觉体验改造。

![CleanMiTV 演示](docs/cleanmitv/demo-preview.gif)

## 致敬原项目

CleanMiTV 的底子来自 **[CleanYourTV](https://github.com/Gathin23/cleanyourtv)**，作者是 [@Gathin23](https://github.com/Gathin23) 和社区贡献者们。原项目把「给安卓电视换上一个干净、无广告、可恢复的桌面」做成了一套完整、可复现的方案：

- 轻量桌面 APK（控制在 512 KiB 以内）；
- 停用应用而不是卸载，每一步都有恢复记录和一键还原；
- 覆盖 290+ 款电视机型、80+ 品牌的实机配方，还有主题、插件和完善的测试。

没有原项目的这些工作，就不会有这个 fork。本仓库同样使用 [MIT 许可证](LICENSE)，并保留原项目的全部代码、文档和历史提交。如果你的电视不是小米，请直接使用原项目。

## 我们改了什么

| 方向 | 改进 |
|---|---|
| 🎨 **APP 主色渐变背景** | 背景跟着焦点 APP 的品牌主色变化，约 680 ms 平滑过渡；快速连按以当前已显示的颜色为起点继续过渡，不会闪黑 |
| 🧠 **更准的取色** | 优先采样横幅，其次采样图标；降低角标权重，忽略透明和近灰像素。白底应用（如云视听极光）取中性银灰，不再偏蓝 |
| 🪟 **玻璃焦点与大图标** | 卡片改为更通透的浅色玻璃框，图标从约 48% 放大到 78%，应用名称移到卡片下方 |
| 🌗 **日间 / 夜间 / 自动** | 「隐藏设置 → 外观模式」可选日间、夜间或自动（07:00 切日间，19:00 切夜间） |
| 🖼️ **16 张本地壁纸** | 中文名称的缩略图网格，用遥控器即可选择；也可以选「跟随主题」或「无壁纸」 |
| ⚡ **动态背景性能优化** | 慢速背景漂移降到 20 fps 节拍，并复用颜色和几何缓存。实机静置 CPU 从约 32–35% 降到 9–12%；关闭「动态背景」后为 0%，APP 切换时的颜色过渡仍然保留 |
| 🎛️ **精简设置** | 删除「高级动态效果」（额外上浮、缩放和转场），保留普通焦点动画；新增「动态背景」独立开关 |
| 🇨🇳 **国行小米适配** | 底栏预设飞牛TV、Nova、云视听极光、云视听小电视；「设置」大齿轮直达小米原生设置；补上原厂「文件管理」入口；直播使用小米 `tvplayer` |
| 📦 **更小的包** | 只保留简体中文和英文资源，APK 约 360 KB |
| 👧 **儿童与老人模式** | 移植 [aerial-tv](https://github.com/pengchujin/aerial-tv) 的 Kids Mode：儿童/老人模式、4 位 PIN 锁、时长/作息/休息限制、应用白名单、全屏锁定，详见下方 [儿童与老人模式](#儿童与老人模式kids--elders-mode) |
| 🏞️ **必应每日精选壁纸** | 「壁纸 · 使用已选图片」下新增「壁纸 · 必应每日精选」：拉取 [bing.wdbyte.com/zh-cn/](https://bing.wdbyte.com/zh-cn/) 的当日 4K 图（Bing 官方接口兜底）保存为单图壁纸，并跟随网站每日自动更新，详见下方 [必应每日精选壁纸](#必应每日精选壁纸bing-daily-wallpaper) |

同时保留了小爱同学、投屏、HDMI / CEC、幻彩随影、米家和系统更新等原厂功能，只停用了统计和诊断组件。

## 儿童与老人模式（Kids & Elders Mode）

移植自 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 的 Kids & Elders mode。
原项目用 Kotlin + Jetpack Compose 实现，本移植按 cleanmitv 的 Java + 原生 View + XML 技术栈完整重写，不引入任何新依赖。

**功能**

- **儿童模式 / 老人模式**：桌面只显示你选择的应用，按选择顺序排列，没有你没选的任何东西。
- **时间限制（仅儿童）**：工作日 / 周末每日时长（不限 / 30 / 60 / 90 / 120 分钟）；最晚看到（不限 / 20:00 / 21:00 / 22:00）；休息间隔（关 / 30 / 45 / 60 分钟，看满间隔休息 5 分钟）。
- **家长 PIN**：4 位 PIN（SHA-256 存储）。退出模式、锁屏后给更多时间（15 / 30 / 60 分钟 / 今天不限）都需 PIN；支持遥控器数字键和屏幕数字键盘。
- **全屏锁定**：今日时长用完 / 超过最晚时间 / 休息中都会全屏锁定，按返回键无效。
- **防绕过**：家长用 PIN 退出后，电视熄屏或屏保出现时自动恢复模式；儿童模式下隐藏顶栏设置按钮、屏蔽遥控器设置键，孩子无法进入设置关闭模式。
- **阻止其他应用（仅儿童，默认开启）**：锁定时任何应用都进不去；未锁定且「阻止」开启时，非允许应用也会被拉回桌面（无障碍服务为主，UsageStats 兜底）。

**开启方法**

1. 桌面顶栏点「设置」→「儿童与老人模式」（或在遥控器设置键面板中找到入口）。
2. 右侧选择要给孩子用的应用，左侧设置对象（儿童/老人）、时长、最晚时间、休息间隔。
3. 点「设置 PIN」设一个 4 位家长 PIN（必须设置，否则无法退出）。
4. 点「启动儿童模式」/「启动老人模式」。

**新增文件**

```
launcher/app/src/main/java/com/gathin/tvhome/
  Kids.java              核心状态：模式开关、允许应用、时间规则、观看用量、PIN（SHA-256）
  KidsWatch.java         计时守护：每 10 秒记账、警告、锁定时把 Home 拉回前台
  PinDialog.java         家长 PIN 键盘（创建 / 验证两种模式）
  KidsSetupActivity.java 儿童/老人模式设置界面
  KidsLockActivity.java  全屏锁定界面

launcher/app/src/main/res/layout/
  activity_kids_setup.xml   设置界面布局（左：规则，右：应用选择列表）
  activity_kids_lock.xml    锁定界面布局
  item_kids_app.xml         应用选择列表项

launcher/app/src/main/res/drawable/
  kids_chip_bg.xml     选择胶囊背景（聚焦/选中态）
  kids_button_bg.xml   操作按钮背景
  kids_app_bg.xml      应用列表项背景
  kids_order_badge.xml 应用序号圆形徽标
```

**与 aerial-tv 的差异**

| 项目 | aerial-tv | 本移植 |
|---|---|---|
| UI 技术 | Jetpack Compose | 原生 View + XML（与 cleanmitv 一致） |
| 老人模式大图标 | 图标放大 + 显示名称 | 保持 cleanmitv 原有图标样式，仅过滤应用 |
| 方向键 PIN | 支持 4 方向 PIN | 仅数字 PIN（屏幕键盘 + 遥控数字键） |
| 阻止其他应用 | 无障碍服务 + UsageStats 双保险 | 无障碍服务为主，UsageStats 为辅（需授权） |

## 必应每日精选壁纸（Bing Daily Wallpaper）

在「设置 → 背景风格」的「壁纸 · 使用已选图片」之下新增第三项「**壁纸 · 必应每日精选**」，让桌面背景跟随 [bing.wdbyte.com/zh-cn/](https://bing.wdbyte.com/zh-cn/) 站内的必应每日精选图片。

**行为**

- 选中即下载当天图片（站点页内直接提取 4K 直链，站点不可达时自动回退到 Bing 官方 `HPImageArchive` JSON），保存为桌面单图壁纸后立即生效；失败时保留前一天图片不中断。
- **每日自动更新**：桌面每次回到前台都会检查日期，跨天后自动拉取当日新图并热刷新壁纸，无需手动干预（同一天内不重复下载）。
- 需要联网权限；下载在后台线程完成，不阻塞首帧。

**新增文件**

```
launcher/app/src/main/java/com/gathin/tvhome/
  BingDaily.java   必应每日精选：解析 wdbyte 页面 4K 直链 / Bing 官方接口兜底，
                   后台下载并落盘为 wallpaper.jpg，记录拉取日期
```

**涉及修改**：`AndroidManifest.xml` 新增 `INTERNET` 权限；`LauncherSettingsActivity.java` 背景风格单选新增第三项；`MainActivity.java` 每次回前台检查日期并热重建壁纸播放器；`WallpaperPlayer.java` 关闭时摘下图片视图以支持原地刷新。

**编译验证**：已通过 GitHub Actions 云端 `./gradlew assembleRelease` 编译（含 R8 压缩），
产物以 `tvhome-app-release` artifact 上传至 [Actions 页面](https://github.com/jankxia/cleanmitv/actions/workflows/build-apk.yml)。

## 实机截图

所有截图都用 adb 直接从电视上截取，没有修图。

| 蓝色 · 飞牛TV | 红色 · 网易云音乐 |
|---|---|
| ![](docs/cleanmitv/01-home-blue.png) | ![](docs/cleanmitv/02-home-red.png) |
| **绿色 · 米家** | **粉色 · 云视听小电视** |
| ![](docs/cleanmitv/03-home-green.png) | ![](docs/cleanmitv/04-home-pink.png) |

| 完整应用网格 | 隐藏设置 |
|---|---|
| ![](docs/cleanmitv/05-app-grid.png) | ![](docs/cleanmitv/06-settings-top.png) |
| **背景风格：APP 主色渐变 / 壁纸** | **动态效果与动态背景开关** |
| ![](docs/cleanmitv/07-background-picker.png) | ![](docs/cleanmitv/08-motion-settings.png) |

![16 张本地精选壁纸](docs/cleanmitv/09-wallpaper-gallery.png)

### 视频

- 横屏演示（1920×1080，27 秒）：[cleanmitv-demo-landscape.mp4](docs/cleanmitv/cleanmitv-demo-landscape.mp4)
- 竖屏版（1080×1920，适合小红书 / 抖音）：[cleanmitv-xiaohongshu-vertical.mp4](docs/cleanmitv/cleanmitv-xiaohongshu-vertical.mp4)
- 双画面竖屏版（1080×1920，8 秒，上下两段实机录屏）：[cleanmitv-dual-vertical.mp4](docs/cleanmitv/cleanmitv-dual-vertical.mp4)

## 下载与安装

**安装包：** 到 [Releases](https://github.com/sqlsec/cleanmitv/releases/latest) 下载 `CleanMiTV-2.7.5-local-MiTV-OS3.apk`，或直接用仓库里的 [`release/CleanMiTV-2.7.5-local-MiTV-OS3.apk`](release/CleanMiTV-2.7.5-local-MiTV-OS3.apk)（版本 2.7.5-local）

> ⚠️ 这个包针对小米 OS3 国行固件构建。该固件只允许白名单内的包名成为第三方 Home，所以用 `-PlauncherApplicationId=com.ts.launcher` 编译。Java 代码的命名空间仍然是 `com.gathin.tvhome`，没有修改系统、固件或签名权限。如果你的电视已经装了同包名的其他应用，会出现签名冲突。其他机型请按下方步骤从源码编译默认包名版本。

1. 在电视的「设置 → 账号与安全」中打开 ADB 调试，让电视和电脑处于同一局域网。
2. 安装 APK，然后按原项目的流程切换 Home、停用原桌面。完整步骤、恢复方法和注意事项见 [原项目 CleanYourTV](https://github.com/Gathin23/cleanyourtv) 和 [docs/TROUBLESHOOTING.md](docs/TROUBLESHOOTING.md)。
3. 想恢复原厂桌面时，重新启用 `com.mitv.tvhome` 并把 Home 设回原桌面即可。停用的应用都没有被卸载。

### 从源码编译

```bash
cd launcher
# 通用包名（com.gathin.tvhome）
./gradlew assembleRelease testReleaseUnitTest lintRelease
# 国行小米 OS3（需要白名单包名）
./gradlew assembleRelease -PlauncherApplicationId=com.ts.launcher
```

底栏顺序、隐藏应用、HDMI 映射等设置都在 [`launcher/app/src/main/res/values/config.xml`](launcher/app/src/main/res/values/config.xml)，可以按自己的电视修改。

## 验证情况

- 255 项单元测试通过，`lintRelease` 通过，APK 低于原项目 512 KiB 的上限。
- 已在 MiTV-MFTR0 上实机验证：Home 接管、重启后仍保持、日夜模式、16 张壁纸、APP 主色切换、文件管理和小米设置入口，以及 HDMI 输入切换。
- 这台电视的应用显示模式只有 1080p 60 Hz，所以本项目不宣称桌面能跑到 120 / 144 Hz。CPU 数据来自电视 `top` 的短时采样，只用于同一台机器、同一场景下的前后对比。

## 许可证

MIT，与原项目一致。版权归原作者及贡献者所有，本 fork 的改动同样以 MIT 发布。
