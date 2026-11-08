# CleanMiTV · 小米电视的干净桌面

> 基于 [Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv) 二次开发，针对**国行小米电视**（实测 MiTV S65 Mini LED / MiTV-MFTR0，Android 14，小米 OS3）做了本地化和视觉体验改造。

![CleanMiTV 主页演示](docs/tvhome/home-demo.gif)

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
| 🏞️ **必应滚动壁纸** | 「壁纸 · 使用已选图片」的选择弹窗里，「跟随主题」「无壁纸」旁边多一格「壁纸 · 必应滚动壁纸」：一次拉取 [bing.wdbyte.com/zh-cn/](https://bing.wdbyte.com/zh-cn/) 图集里最近 7 天的 4K 图（Bing 官方接口兜底），保留最近 7 天、最新在前，选完立刻能看到保留的图片，固定 30 分钟换一张并跟随网站每日自动更新，详见下方 [必应滚动壁纸](#必应滚动壁纸bing-rolling-wallpaper) |

同时保留了小爱同学、投屏、HDMI / CEC、幻彩随影、米家和系统更新等原厂功能，只停用了统计和诊断组件。

## 儿童与老人模式（Kids & Elders Mode）

移植自 [pengchujin/aerial-tv](https://github.com/pengchujin/aerial-tv) 的 Kids & Elders mode。
原项目用 Kotlin + Jetpack Compose 实现，本移植按 cleanmitv 的 Java + 原生 View + XML 技术栈完整重写，不引入任何新依赖。

**功能**

- **儿童模式 / 老人模式**：桌面只显示你选择的应用，按选择顺序排列，没有你没选的任何东西。**一个应用都不选也可以进入**——那是「隐藏所有程序」的用法：主页只剩一张「儿童模式」卡片。
- **时间限制（仅儿童）**：工作日每日时长（**不允许** / **10** / 30 / 60 / 90 / 120 分钟——选「不允许」＝当天整日锁定，任何时间、任何时段都打不开电视，只有家长 PIN 能临时放行）；周末每日时长（不限 / **10** / 30 / 60 / 90 / 120 分钟）；最晚看到（不限 / 20:00 / 21:00 / 22:00）；休息间隔（关 / 30 / 45 / 60 分钟，看满间隔休息 5 分钟）。
- **家长 PIN**：4 位 PIN（SHA-256 存储），可选两种输入方式：「4 位数字」（屏幕数字键盘或遥控数字键）、「4 个方向键」（遥控上按 ↑ ↓ ← → 组合；屏幕上只把 4 个圆点逐格填满，**不回显按了哪个方向**，防止旁边的人照着屏幕记下 PIN）。退出模式、锁屏后给更多时间（15 / 30 / 60 分钟 / 今天不限）都需 PIN。
- **全屏锁定**：今日时长用完 / 超过最晚时间 / 休息中都会全屏锁定，按返回键无效；锁定期间点任何应用都会直接弹出锁定界面（而不是看起来「点了没反应」）。
- **家长退出入口**：右上角按钮在三种模式下**保持原样**（同样的按钮、同样的位置、同样的文字，不会变成「家长」按钮）。正常桌面下，顶栏那颗胶囊叫「设置」（点开后页内大标题仍是「隐藏设置」），下拉菜单行序为：输入源 / 网络 / 隐藏设置 / 系统设置 / **儿童/老人模式** / 调整应用顺序；「儿童/老人模式」是一行按钮，点开二级列表再选「进入儿童模式」「进入老人模式」，模式开着时多一项「退出当前模式」。**一旦处在儿童/老人模式里，这个下拉只剩一行「退出当前模式」**：输入源、网络、两套设置、调整应用顺序全部收起，孩子点不到任何设置项。退出要输 PIN；主页里的「儿童模式」卡片、遥控器的设置键 / 菜单键也都是同一个 PIN 退出。
- **防绕过**：家长用 PIN 退出后，电视熄屏或屏保出现时自动恢复模式。
- **其他应用（两个模式共用一行，只有「允许打开」一个选项）**：设置页里「其他应用」下不再有「阻止打开」/「允许打开」两态可切，固定为「允许打开」。锁定时任何应用都进不去；未锁定时按「允许打开」放行其他应用，但非白名单应用仍会被拉回桌面（无障碍服务为主，UsageStats 兜底）。

**开启方法**

1. 桌面顶栏点「设置」→ 下拉菜单里点「儿童/老人模式」→ 选「进入儿童模式」/「进入老人模式」（也会打开同一个设置界面）。
2. 右侧选择要给孩子用的应用，左侧设置对象（儿童/老人）、时长、最晚时间、休息间隔。**不选任何应用也可以**：这时主页不显示任何程序，只剩一张「儿童模式」卡片。
3. 点「设置 PIN」设一个 4 位家长 PIN，弹窗里选输入方式：数字或方向键（未设置 PIN 时点「启动」会先要求设置，设完直接进模式）。
4. 点「启动儿童模式」/「启动老人模式」。
5. 想退出：再点「设置」→「儿童/老人模式」→「退出当前模式」，输 PIN 即可（主页那张「儿童模式」卡片、遥控设置键也是同一入口）。

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
| 方向键 PIN | 支持 4 方向 PIN | 支持，可在设置 PIN 时二选一（数字 / 方向键） |
| 阻止其他应用 | 无障碍服务 + UsageStats 双保险 | 无障碍服务为主，UsageStats 为辅（需授权） |

## 必应滚动壁纸（Bing Rolling Wallpaper）

在「壁纸 · 使用已选图片」那张选择弹窗里，「跟随主题」「无壁纸」旁边新增一格「**必应滚动壁纸**」，让桌面背景跟随 [bing.wdbyte.com/zh-cn/](https://bing.wdbyte.com/zh-cn/) 站内的必应每日精选图片。

**行为**

- **选中即可见**：点「必应滚动壁纸」后先拉图，随后立刻把这个图集弹出来——你能直接看到保留下来的每一张，标题写着「保留 N 张，最新在前」。
- **首次就补满 7 天**：一次抓取就解析站内图集里最近 7 天的 4K 直链（按 `data-date` 取日期、同一天只取一张），缺哪天补哪天，所以第一次选中看到的已经是 7 张，不用等一周才凑齐；已下载的日期会跳过，不会重复占流量。
- **7 天滚动**：每天一张，存 `files/bing/bing-<日期>.jpg`，最多保留最近 **7 天**，最新的排第一，第 8 天到时最旧那张自动删除。
- **30 分钟换一张**：这个模式下轮换固定为 30 分钟（不受主题自带轮换设置影响），7 张图循环播放。
- **自动拉新**：桌面每次回到前台都会检查日期，跨天自动下载当日新图并热刷新壁纸（同一天只下一次）；站点不可达时回退 Bing 官方 `HPImageArchive` JSON，拉不到也不影响已有图集。
- 弹窗里的日期格（`2026-11-06` 这种）只是给你确认「留了哪几天」，点它不会关掉滚动；想彻底换回手选图片，点「跟随主题」或切回「壁纸 · 使用已选图片」再挑一张。

**新增文件**

```
launcher/app/src/main/java/com/gathin/tvhome/
  BingDaily.java   必应滚动壁纸：解析 wdbyte 图集最近 7 天的 4K 直链 / Bing 官方接口兜底，
                   后台下载并按日期落盘到 files/bing/，只留最近 7 天、最新在前
```

**涉及修改**：`AndroidManifest.xml` 新增 `INTERNET` 权限；`WallpaperPicker.java` 新增第三格与「必应图集」视图；`LauncherSettingsActivity.java` 背景风格单选新增第三项、选中后立即展示保留图；`Config.java` 在该模式下把 `files/bing/` 的 7 天图作为壁纸集并固定 30 分钟轮换；`MainActivity.java` 每次回前台检查日期并热重建壁纸播放器；`WallpaperPlayer.java` 关闭时摘下图片视图以支持原地刷新。

## v2.7.7 修复记录（实机反馈）

小米电视（MiTV-ASTR0，包名 `com.ts.launcher`）上装机后反馈的 5 个问题：

| # | 问题 | 原因 | 修复 |
|---|---|---|---|
| 1 | 儿童模式不支持上下左右方向键做 PIN | `PinDialog` 里方向键分支是死代码：`show()` 每次都把 `arrows` 硬置 `false`，也没有任何入口把它设为 `true` | `PinDialog` 新增 `arrows` 构造参数并去掉硬置；设置 PIN 时先选「数字 / 方向键」，方向键模式屏幕上显示 ↑ ↓ ← → 已输入序列；锁屏验证按已存的类型打开 |
| 2 | 儿童模式能显示选中的 APP，但点击打不开 | 锁定状态下点应用后 `KidsWatch` 只把 Home 拉回前台，用户看不到任何提示；另外 `PinDialog` 收不到按键也放大了这个问题 | 锁定时 `bringBack` 直接打开 `KidsLockActivity`（看到「时间到 / 该休息」和家长按钮）；PIN 对话框改为在 `dispatchKeyEvent` 收键，不再被聚焦的按键吞掉 |
| 3 | 无法输入 PIN 退出儿童模式 | 两处：① 未锁定时主页没有任何退出入口，设置键被直接吞掉；② 允许不设 PIN 就启动模式，`pinMatches` 永远为 false | 主页顶栏在模式中只保留一个「家长」按钮（点击即 PIN → 退出），设置键 / 菜单键同样弹 PIN；未设 PIN 时「启动」会先要求设置 PIN |
| 4 | 壁纸选项「原来的方案」没有保存下来 | 装机版是 `com.gathin.tvhome`，与 TV 原有桌面 `com.ts.launcher` 的数据目录、`SharedPreferences` 完全独立，原设置当然不会跟过来 | 用 `com.ts.launcher` 包名构建并 `adb install -r` 覆盖升级（签名相同、versionCode 相同即可原地升级，设置与壁纸都保留） |
| 5 | 必应壁纸「选中的是一张 JPG 图片」 | 原实现把每日图覆盖写入 `files/wallpaper.jpg`，`files/wallpapers/` 目录为空时它就变成唯一一张可选图 | 必应图改存 `files/bing/bing-<日期>.jpg`（最新在前），`Config` 在该模式下把这一小组作为壁纸集；「壁纸」行不再把 `wallpaper.jpg` 混进手选列表 |

**编译验证**：已通过 GitHub Actions 云端 `./gradlew assembleRelease` 编译（含 R8 压缩），
产物以 `tvhome-app-release` artifact 上传至 [Actions 页面](https://github.com/jankxia/cleanmitv/actions/workflows/build-apk.yml)。

## v2.7.8 修复记录（实机反馈第二轮）

| # | 反馈 | 原因 | 修复 |
|---|---|---|---|
| 1 | 进入儿童模式必须先选一个应用，想「什么都不显示」也进不去 | `Kids.active()` 定义为 `enabled && !suspended && !allowed.isEmpty()`，允许列表为空直接被判成「模式未激活」；`KidsSetupActivity.startButton` 还在点「启动」时用 `kids_pick_first` 拦截 | `Kids.active()` 去掉空列表判断（空列表＝隐藏所有程序）；「启动」不再要求先选应用，未设 PIN 时先弹 PIN 设置、设完自动进模式，并提示「主页只显示模式卡片」 |
| 2 | 主页没有「儿童模式」入口，只能靠顶栏「家长」按钮退出 | 模式里只渲染 `Kids.allowed()` 里的应用，列表为空时首页没有可点的东西 | `AppAdapter` 新增 `Entry.KIDS_PARENT` 卡片（图标 `ic_kids_mode`），在模式中的主页永远排第一格；点击与「家长」按钮走同一个 `leaveKidsMode()`（弹 PIN → 校验 → 退出）。老人模式下卡片文案自动变成「老人模式」 |

**涉及文件**：`Kids.java`、`AppAdapter.java`、`MainActivity.java`、`KidsSetupActivity.java`、`res/drawable/ic_kids_mode.xml`、`values/strings.xml`、`values-zh-rCN/strings.xml`。

## v2.7.9 修改记录（顶栏按钮回到原样 + 设置下拉菜单）

| # | 反馈 | 原因 | 修复 |
|---|---|---|---|
| 1 | 儿童模式里右上角按钮被改成了「家长」，其它按钮全被隐藏，跟原版不一样 | 上一版为了「防孩子乱点」在 `applyKidsMode()` 里把顶栏按钮几乎全隐藏，并把齿轮文案 / 图标换成「家长」 | `applyKidsMode()` 不再改顶栏：三种模式下按钮、文字、图标、顺序与配置完全一致（`Config` 里的开关照旧生效） |
| 2 | 希望点「设置」后是下拉菜单，能选系统设置、也能进 / 出儿童或老人模式 | 原来齿轮点击是「模式中就弹 PIN、否则直接跳设置页」，没有菜单 | 新增 `showGearMenu()`：点齿轮弹玻璃下拉，行顺序为「输入源 / 网络 / 桌面设置 / 系统设置 / 进入（或退出）儿童模式 / 进入（或退出）老人模式 / 重排」。模式行按当前状态显示「进入…」或「退出…」，退出仍走 `leaveKidsMode()` 的 PIN 校验；「进入」时若该模式已设置过且只是被暂停，直接恢复，否则打开设置页并预选对应的儿童 / 老人 |

**涉及文件**：`MainActivity.java`（`applyKidsMode`、`showGearMenu`、`enterKidsMode`、齿轮两处点击）、`KidsSetupActivity.java`（新增 `EXTRA_KIND` 预选模式）、`values/strings.xml`、`values-zh-rCN/strings.xml`。

## v2.7.10 修改记录（必应滚动壁纸进选择弹窗）

| # | 反馈 | 原因 | 修复 |
|---|---|---|---|
| 1 | 要在「壁纸 · 使用已选图片」弹窗里点开后的「跟随主题 / 无壁纸」旁边，加一项「必应滚动壁纸」 | 之前只在「背景风格」单选里加了第三项，图片网格弹窗本身还是两格虚卡 + 本地图，必应这个选项在那张弹窗里根本看不见 | `WallpaperPicker` 增加第三格「必应滚动壁纸」（返回特殊值 `"bing"`），`LauncherSettingsActivity` 收到后切到该模式并拉当日图 |
| 2 | 选完之后要能看见「保留的图片」 | 原实现选中后弹个 Toast 就算结束，弹窗直接关了，看不到到底留了哪几天 | 选中后立刻用同一套弹窗打开「必应图集」视图：标题「必应滚动壁纸（保留 N 张，最新在前）」，每格标日期（`2026-11-06`），点图只提示不打断滚动；没有图时提示先联网 |
| 3 | 保留最近 7 天、最新在前、每 30 分钟换一张、自动拉新 | `BingDaily.KEEP` 是 10；`Config` 只在轮换间隔为空时才用 30 分钟，用户把主题轮换设成别的值时会被覆盖 | `KEEP = 7`（`prune()` 与 `wallpapers()` 同步）；`Config` 在该模式下把 `rotationMinutes` 强制设为 30；每日自动拉新维持「回到桌面即检查日期」（同一天只下载一次） |
| 4 | `adb install -r` 报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`，覆盖升级装不上 | release 构建用的是 `signingConfigs.debug`，而 GitHub 每次都是全新 runner，`~/.android/debug.keystore` 每次重新生成 —— 同一个 commit 两次构建出来的 APK 签名都不一样 | 新增仓库 secret `TVHOME_KEYSTORE_B64` 存一份固定 PKCS12 签名（2048 位 RSA、自签、有效期到 2054 年、alias `tvhome`）；workflow 构建前还原该 key 并以 `-PtvhomeStoreFile` 传给 Gradle，`build.gradle` 拿不到时回退 debug 签名。`versionCode` 9 → 10、`versionName` → `2.7.10-bing-rolling` |

**涉及文件**：`WallpaperPicker.java`、`LauncherSettingsActivity.java`、`Config.java`、`BingDaily.java`、`values/strings.xml`、`values-zh-rCN/strings.xml`、`app/build.gradle`、`.github/workflows/build-apk.yml`。

## v2.7.11 修改记录（实机验证 + 首次补满 7 天）

| # | 反馈 | 原因 | 修复 |
|---|---|---|---|
| 1 | 选中「必应滚动壁纸」后只看到 1 张，说好的「保留最近 7 天」要等一周才凑齐 | `BingDaily` 每次只解析站内图集的第一个 4K 直链，存成 `bing-<今天>.jpg` 就结束 —— 天数是靠时间累积，不是靠图集本身 | `BingDaily` 新增 `ENTRY` 正则，按 `<a ... data-date>` 一次解析出最近 7 天的「日期 + 4K 直链」；`fetchToday` 循环下载缺失的日期（已有文件跳过），一次补满 7 张，标题随即显示「保留 7 张，最新在前」 |
| 2 | 自动拉新不该重复下载已有的图 | 原逻辑只比较「今天抓过没有」 | `DATE_KEY` 仍记当天（同一天只跑一次抓取），但下载按 `bing-<日期>.jpg` 是否存在判断，跨天只补新增的那一张 |
| 3 | 装到电视后要确认弹窗真有三格、真能看到保留图、桌面真换了图 | 上一版只验证了编译与签名 | 用 `input tap` + `uiautomator dump` + `screencap` 在 MiTV-ASTR0 上实测：「壁纸 · 0 张本地精选」弹窗出现「跟随主题 / 无壁纸 / 必应滚动壁纸」三格 → 点第三格后标题变「壁纸 · 必应滚动壁纸（保留 1 张，最新在前）」并出现带 `2026-11-06` 标签的缩略图 → 设置页该行变「壁纸：必应滚动壁纸（1 张，30 分钟一换）」→ 回桌面背景已换成当日 4K 竹林图，`files/bing/bing-2026-11-06.jpg`（1.9 MB）已落盘 |

**涉及文件**：`BingDaily.java`、`app/build.gradle`（`versionCode` 10 → 11，`versionName` → `2.7.11-bing-seven`）。

## v2.8.0 修改记录（设置菜单、儿童/老人模式、天气与农历）

| # | 反馈 | 原因 | 修复 |
|---|---|---|---|
| 1 | 顶栏「隐藏设置」胶囊改叫「设置」，点开后页内标题仍要叫「隐藏设置」 | 顶栏胶囊和设置页大标题共用同一个 `R.string.launcher_settings`，直接改会把页内标题一起改掉 | 新增独立字符串 `header_settings`（中英各一条），顶栏胶囊改用它；设置页标题继续绑 `launcher_settings`，两处从此互不影响 |
| 2 | 齿轮下拉里「进入儿童模式」「进入老人模式」两行合成一个按钮「儿童/老人模式」 | 七行菜单太长，两个模式行又各占一行 | `showGearMenu()` 只留一行「儿童/老人模式」（图标 `ic_kids_mode`），点击弹二级列表：进入儿童模式 / 进入老人模式 /（模式开着时）退出当前模式；`enterKidsMode()` 与 `leaveKidsMode()` 的原有逻辑（含 PIN 校验）不变 |
| 3 | 进入儿童模式后，页面下面的「应用库」胶囊不该出现 | footer 的可见条件只看 `showLibrary`，没有考虑模式 | `AppAdapter` 新增 `footerLibrary() = showLibrary && kidsAllowed == null`，`getItemCount()` / `isLibraryAt()` / `getItemViewType()` 全部走它；模式里只剩选定应用 + 模式卡片 |
| 4 | 家长 PIN 用方向键输入时，不要把按的方向显示出来 | `PinDialog.redraw()` 在 arrows 模式下把每个圆点写成 ↑↓←→，旁边的人照着屏幕就能抄下 PIN | 圆点只按已输入长度填实心，文本恒为空；底部那行 ↑ ↓ ← → 仍作为「按方向键」的按键提示保留（那是键位说明，不是输入回显） |
| 5 | 在主页按遥控器返回键，要直接回到主页程序坞 | 非重排状态直接把 `super.dispatchKeyEvent()`，Back 交给系统，可能离开桌面或把焦点落到奇怪的位置 | `dispatchKeyEvent` 在无弹窗、无卡片提示、不在 PIN 输入时拦下 BACK / ESC，`focusTile(0)` 把焦点送回程序坞第一格并消费该键；重排 / 编辑程序坞里的 Back 行为保持原样 |
| 6 | 没有选进程序坞的 app 不要在程序坞下面「冒头」 | `rows_below_dock = 0` 只是把程序坞往下推，`RecyclerView` 照样把其余应用排在下方的行里，看起来就是冒出来一截 | 试过 `AppAdapter.reload()` 在 `rowsBelowDock == 0` 时截掉程序坞行之后的多余条目，实机反馈「主页少了一大片 app、看不见添加到主页的应用」后**已回退**：主页照旧列出全部应用，行数仍由 `rows_below_dock` 控制 |
| 7 | 日期下面要加天气显示、农历显示 | 农历视图 `lunarDate` 早就存在，但 `R.bool/lunar_date` 默认是 `false`，一直不显示；天气完全没有 | `lunar_date` 默认改 `true`；中文农历改成「农历九月廿八 丙午年」并挪到「11月6日 星期五」**上面一排**（`Lunar` 内置月名/日名对照表，不再显示「9月28日」）；天气行放在日期下面；新增 `Weather.java`（Open-Meteo 免 key 接口 + 45 个城市坐标表 + 上次结果缓存），`activity_main.xml` 在日期下加 `@id/weather` 一行，`MainActivity.onResume` 刷新，最多 30 分钟请求一次 |
| 8 | 隐藏设置里要能选天气城市 | 原来没有任何入口，城市只能写死 | `activity_launcher_settings.xml` 增加「天气城市」行，点击弹 45 个城市的单选列表（`Weather.CITIES`），选中即写入 `weather_city` 并立刻拉一次该城市天气，回桌面即可看到 |

**涉及文件**：`Weather.java`（新增）、`MainActivity.java`、`AppAdapter.java`、`PinDialog.java`、`Lunar.java`、`LauncherSettingsActivity.java`、`activity_main.xml`、`activity_launcher_settings.xml`、`values/strings.xml`、`values-zh-rCN/strings.xml`、`values/lunar.xml`、`values-zh-rCN/lunar.xml`、`values-zh-rTW/lunar.xml`、`values/config.xml`、`app/build.gradle`（`versionCode` 11 → 12，`versionName` → `2.8.0-settings-weather`）。

## v2.8.1 修改记录（回退程序坞截断）

| # | 反馈 | 处置 |
|---|---|---|
| 1 | 「程序坞的改变回到上一版」 | `2.8.0` 里那段「`rows_below_dock = 0` 就截掉程序坞行之后的所有条目」在电视上把主页削得只剩程序坞一行，添加到主页的应用也一起消失。已整段删除，主页恢复成 `2.7.11` 的样子：程序坞照旧贴底，其余应用按 `rows_below_dock` 排在网格里；想要程序坞下面留几行，在隐藏设置里把「程序坞下方行数」改成 1 / 2 / 3 即可 |

**涉及文件**：`AppAdapter.java`（删掉截断段）、`app/build.gradle`（`versionCode` 12 → 13，`versionName` → `2.8.1-settings-weather`）、`README.md`（实机截图换成本 fork 的电视截图）。

### 天气与农历（Weather & Lunar）

- **数据源**：`https://api.open-meteo.com/v1/forecast` 的 `current=temperature_2m,weather_code`，免 key、免注册，只读当前温度与 WMO 天气代码；`Weather.describe()` 把代码翻成中文（晴 / 多云 / 阴 / 小雨 / 雷阵雨 …）。
- **显示**：时钟下方三行依次是「农历九月廿八 丙午年」（农历，日期上一行）→「11月6日 星期五」（日期）→「成都 · 多云 18℃」（天气，日期下一行）。
- **节流与离线**：最多 30 分钟请求一次；结果（温度、代码、时间）写进 `launcher_settings`，断网时继续显示上一次的数字，不会空一行。
- **城市**：`Weather.CITIES` 内置 45 个城市（成都、北京、上海、广州、深圳、重庆、杭州、南京、武汉、西安、天津、苏州、郑州、长沙、东莞、沈阳、青岛、合肥、佛山、福州、厦门、昆明、哈尔滨、济南、南昌、贵阳、南宁、石家庄、太原、长春、大连、无锡、宁波、温州、珠海、三亚、海口、兰州、西宁、银川、乌鲁木齐、呼和浩特、拉萨、香港、澳门、台北），默认成都。
- **农历**：`Lunar` 纯算法（无 ICU）算出当天农历，中文用「农历九月廿八 丙午年」（月名正月…腊月，日名初一…三十），闰月显示「闰四月」；排在公历日期上面一行。UTC 偏移默认 +8，可在主题 `config.json` 的 `lunar_utc_offset` 覆盖。

## v2.8.3 修改记录（儿童/老人模式：菜单收紧、其他应用放行、工作日「不允许」）

| # | 反馈 | 处置 |
|---|---|---|
| 1 | 儿童/老人模式下，主页「设置」按钮里只显示「退出该模式」 | `showGearMenu()` 在 `Kids.active()` 时只加一行「退出当前模式」（仍走 PIN 校验）；输入源、网络、隐藏设置、系统设置、儿童/老人模式、调整应用顺序都不进菜单。长按 OK 的 `showSettingsMenu()` 同样只剩这一行，所以在模式里摸不到「隐藏设置」「系统设置」「重排」 |
| 2 | 设置页「其他应用」下只显示「允许打开」 | `activity_kids_setup.xml` 删掉 `blockOn`（阻止打开）chip，只保留 `blockOff`（允许打开）；`bindKind` 不再在切到儿童时把阻止状态打开；`Kids.blockOthers` 默认改为 `false`，老版本留下的 `true` 在设置页打开时（`refresh()`）自动纠正 |
| 3 | 工作日时长删「不限」、加「不允许」 | 工作日档位改为 不允许 / 30 / 60 / 90 / 120 分钟。`weekdayMinutes = -1` 表示「不允许」，`Kids.Lock.NO_WATCH` 在时段、休息之前返回，锁屏显示「今天不能看电视」；家长 PIN 仍可临时放行。`Rules.withWeekday()/withWeekend()` 直接赋值——`copy()` 里的 `-1` 一直是「不改」，承载不了这个值 |

**涉及文件**：`MainActivity.java`、`Kids.java`、`KidsSetupActivity.java`、`KidsLockActivity.java`、`KidsWatch.java`、`activity_kids_setup.xml`、`values/strings.xml`、`values-zh-rCN/strings.xml`、`app/build.gradle`（`versionCode` 13 → 14，`versionName` → `2.8.3-kids-mode-rules`）。

## v2.8.4 修改记录（儿童模式：答题换时长）

第 4 项改造：儿童 →「其他应用」下面的「打开答题限制」。打开后，当天时间用完不再只有「家长 PIN 加时」一条路，孩子自己答题就能换到下一段观看时间。

**机制来源**：只吸收 [StudyOnTV](https://github.com/jankxia/StudyOnTV) 的「答题换时长」思路，代码全部用 Java 重写，没有搬运它的 `.kt`、网页、题库或任何资源 —— 那个项目的许可证是 Source Available / Personal Use Only，明文禁止再分发衍生作品，而本仓库是公开的并发布 Release。

| # | 反馈 | 处置 |
|---|---|---|
| 4 | 儿童 →「其他应用」增加「打开答题限制」，启用后儿童模式下即生效 | 儿童设置页新增「答题限制」区块（关闭 / 打开答题限制 + 展开后的题库、加时、玩法、时段表），全部落在 `SharedPreferences`，没有任何网络入口；`Kids.lockNow()` 在当天额度用完时返回 `Lock.QUIZ`，锁屏页出现「开始答题」，答对够数就由 `Kids.grantQuizTime()` 写进账本 |

**实现要点**

- **题库（小学，立即出题）**：`assets/kids-quiz.json` 130 题 —— 语文 60（古诗接句、作者、常识）、数学 40（四则运算、应用题，脚本逐题验算过答案）、英语 30（字母、词汇、日常用语、英文数字）。每题 4 个选项，无网也能出题。学段 chip 里「小学」可选，「初中」「高中」目前点下去只提示以后再加 —— 题库先按用户拍板的范围只做小学。
- **科目可选**：语文 / 数学 / 英语三个 chip 各自开关，出题只从勾选的科目里抽；抽题顺序是「今天没出过且从没答对」→「今天没出过但答对过」→「今天出过」，各段先打乱，避免连着几天重复同一批题。
- **加时与账本**：`KidsPlan` 存配置（每组加时 5/10/15/20 分钟、每次 3/5/8 题、通过率 50/60/80%、每日 1/2/3/5 组、玩法、自动下一题 0/3/5 秒、星级、错题加练、节假日按周末、时段表），`KidsLedger` 存当天账（已攒的 credit、今天答了几组、错题池、错题累计、日期），跨天只清 credit 与组数、保留错题池。
- **三种玩法**：答一组题（答对达通过率才加时）、连对闯关（连续答对 N 道才算）、只练不加时（不换时长，专门清错题池）。
- **错题加练**：连续答错累计到阈值就往加练池里加题，下一组多出这些题。
- **观看时段表**：7×48 的半小时格子（周一…周日 × 00:00…23:30），绿色可以看、灰色不能看，方向键选格子、OK 切换。关掉的格子与「最晚时间」叠加生效 —— 就算还没到最晚时间，落在灰格子里也锁屏。
- **兜底**：题库读不出来时直接按配置加时并给出提示，不会把孩子永久锁在锁屏页。
- **不影响计时**：答题页显示期间 `KidsWatch.quizShowing` 为真，这一页既不算观看时间，也不会被「回桌面」守卫打断。

**体积**：APK 从 405,617 B 增到 433,944 B，多了 28,327 B（130 题题库压缩后约 5 KB，其余是 5 个新类和界面）。

**涉及文件**（新增）：`KidsPlan.java`、`KidsLedger.java`、`KidsBank.java`、`KidsQuizActivity.java`、`KidsScheduleActivity.java`、`assets/kids-quiz.json`、`activity_kids_quiz.xml`、`item_quiz_option.xml`、`activity_kids_schedule.xml`、`quiz_option_bg.xml`、`quiz_option_right.xml`、`quiz_option_wrong.xml`、`slot_on.xml`、`slot_off.xml`；（修改）`Kids.java`、`KidsWatch.java`、`KidsSetupActivity.java`、`KidsLockActivity.java`、`AndroidManifest.xml`、`activity_kids_setup.xml`、`activity_kids_lock.xml`、`values/styles.xml`、`values/strings.xml`、`values-zh-rCN/strings.xml`、`app/build.gradle`（`versionCode` 14 → 15，`versionName` → `2.8.4-quiz-time`）。

## v2.8.5 修改记录（时长档位补 10 分钟）

v2.8.4 的补丁。原来时长档位最小是 30 分钟，想验证「答完一组题加 10 分钟」这条链路，得先看满半小时才碰得到上限，短时间放行也不方便。

| 改动 | 说明 |
|---|---|
| 工作日时长多一档 **10 分钟** | 档位变成 不允许 / 10 / 30 / 60 / 90 / 120，仍是最前面「不允许」＝整天锁 |
| 周末时长多一档 **10 分钟** | 档位变成 不限 / 10 / 30 / 60 / 90 / 120 |

`Rules` 里的判定只认分钟数本身（`-1` 不允许、`0` 不限、其余为额度），所以十进制的 10 直接就能用，不用改计时逻辑。`KidsSetupActivity` 的 `bindMinutes` 多绑定两个 chip，`setWeekdaySelected` / `setMinutesSelected` 各多收一个 id。

**体积**：APK 从 433,944 B 到 434,052 B，多了 108 B。

**涉及文件**：`activity_kids_setup.xml`、`KidsSetupActivity.java`、`app/build.gradle`（`versionCode` 15 → 16，`versionName` → `2.8.5-ten-minutes`）。

## v2.8.6 修改记录（休息间隔 5/10 分钟，退出必输 PIN）

两条要求一起改：休息间隔要有更短的档位，时长用满之后不许一个按钮就溜出儿童模式。

| 改动 | 说明 |
|---|---|
| 休息间隔多 **5 分钟**、**10 分钟** | 档位从 关 / 30 / 45 / 60 改成 关 / 5 / 10 / 30 |
| 休息间隔删掉 **45 分钟**、**60 分钟** | 两档太接近 30，且从没被用过；旧的 45/60 设置读取时回落成「关」，不会出现整行无选中 |
| 时长用满后点「退出儿童模式」要输 PIN | 原来 `KidsLockActivity.leave()` 直接 `suspend()`，只靠按钮的可见性挡着，等于没有校验 |

`Rules.breakEvery` 存的就是分钟数，`KidsTime.advance/lock` 早就按 `breakEvery * 60_000` 判休息，所以计时逻辑一行没动，只改档位与绑定。

退出这一侧补了五个口子：

| 位置 | 原来 | 现在 |
|---|---|---|
| `KidsLockActivity.leave()` | 谁调用都放行 | 只有 PIN 刚校验通过（`granting`）才 `suspend()` |
| `KidsLockActivity.lockCheck()` | 只看缓存的 `Kids.lock`，字段过期会在该锁的时候把锁屏关掉 | 每轮自己 `Kids.lockNow()` 重算，只在返回 `null` 时 `finish()` |
| `KidsLockActivity.askParent()` | 没设 PIN 也弹键盘，永远「PIN 错误」 | 先判 `pinSet()`，没设就提示去设 PIN |
| `MainActivity.leaveKidsMode()`（设置胶囊里的「退出当前模式」） | `!pinSet()` 时直接 `suspend()` | 不放行，改为提示并跳去儿童设置页设 PIN |
| `KidsSetupActivity` 的「关闭模式」 | 点一下就关 | 有 PIN 且不在 `unlockedRecently()` 里，先验 PIN 再 `Kids.turnOff()` |

答题入口按约定**不验 PIN**：`KidsTime.ruleLock()` 的顺序是 不允许 → 最晚时间 → 时长用完 → 休息，过了「最晚看到」先返回 `LATE`，锁屏直接变成「该睡觉了」，答题按钮不会再冒出来顶替。

**体积**：APK 从 434,052 B 到 434,392 B，多了 340 B。

**涉及文件**：`activity_kids_setup.xml`、`KidsSetupActivity.java`、`KidsLockActivity.java`、`MainActivity.java`、`app/build.gradle`（`versionCode` 16 → 17，`versionName` → `2.8.6-short-breaks`）。

**实机验证**（MiTV-ASTR0，adb 实拍）：错 PIN → Toast「PIN 错误。」；对 PIN → 出现 15 / 30 / 60 / 今天不限 + 退出儿童模式；点「退出儿童模式」回主页、儿童模式已挂起；休息设 5 分钟后连续观看约 5 分钟弹出「休息时间 / 2:29 后恢复。」。设置胶囊里的「退出当前模式」现在同样先弹 PIN。休息行确认只剩 关 / 5 / 10 / 30。

**已知限制**：锁屏靠 `startActivity` 拉起，第三方播放器在前台时会被 Android 11 的后台启动限制挡掉（本应用 `SYSTEM_ALERT_WINDOW` 为 `default`），所以视频正在播的时候休息页要等按一次 HOME 才出现；给这个应用打开「显示在其他应用上层」后该限制解除。

## v2.8.7 修改记录（24 小时制时钟，每次选择都要 PIN）

三条要求：主页时钟走 24 小时制；「今天的时长用完了」不再给「今天不限」；面板上点任何一个按钮都要再输一次家长 PIN。这一版也把答题流程第一次在电视上跑通。

| 改动 | 说明 |
|---|---|
| 主页时钟改 **24 小时制** | `TextClock` 的 `format12Hour` 从 `h:mm` 改成 `HH:mm`（`format24Hour` 本来就是 `HH:mm`），显示午别的小字 `@id/ampm` 加 `visibility="gone"`；平板变体 `layout-iw` 同步改 |
| 用完时**隐藏「今天不限」** | `render()` 里 `lock == DAY_USED` 时把 `grantDay` 置 `GONE`，面板只剩 15 / 30 / 60 分钟 |
| 每个加时按钮**单独验 PIN** | 新增 `grantBehindPin(minutes)`：点 15 / 30 / 60 都弹一次 `PinDialog(VERIFY)`，验过才 `grant()`。面板开着不再等于有权限 |
| 守卫诊断日志 | `KidsWatch.tick()` 每 6 个 tick（约 1 分钟）写一行 `Log.d("KidsWatch", ...)`，带 `watch= / used= / lock=`，用 `adb logcat -s KidsWatch` 就能核对当天到底记了多少分钟 |

**答题入口以前为什么不出现**——这次查实了：`dailyCapMs = (limitToday + roundsPerDay × sessionMinutes) × 60_000`。当时周末 60 分钟、每组加时还是 **5** 分钟、每天 3 组，上限只有 75 分钟，而那天已经看了 77 分钟，`canEarnMore()` 恒为 false，`lockNow()` 就一直停在 `DAY_USED`，永远升不到 `QUIZ`。把「每组加时」调到 15 分钟后上限变 105 分钟，入口立刻冒出来。

**实机验证**（MiTV-ASTR0，adb 实拍）：时钟 21:45、22:09 均为 24 小时制、无午别；锁屏「今天的时长用完了 / 今天已看 1 小时 17 分」下方只有「家长」，验 PIN 后的面板只有 15 / 30 / 60 分钟 + 退出儿童模式；再点「15 分钟」会**第二次**弹家长 PIN；答题整轮跑通：出题 → 判对（所选项变绿、答对计数上涨）→ 结算「通过啦 / 答对 2 / 3，加了 12 分钟」→ 回桌面不再锁屏。

**体积**：APK 434,392 B → 434,980 B，多了 588 B。

**涉及文件**：`activity_main.xml`、`layout-iw/activity_main.xml`、`KidsLockActivity.java`、`KidsWatch.java`、`app/build.gradle`（`versionCode` 17 → 18，`versionName` → `2.8.7-pin-every-choice`）。

## v2.8.8 修改记录（时间设置互锁，锁屏能盖住播放中的视频）

### 一、三个时间不再互相打架

以前工作日时长、周末时长、休息间隔各选各的，能设出「休息 30 分钟，但一天只看 10 分钟」这种永远不会触发的组合。现在按一条物理事实把它们串起来：**休息点必须落在当天的分钟数之内**。

| 你做的动作 | 页面随之发生什么 |
|---|---|
| 周末从 60 改到 **10** | 休息行从 关 / 5 / 10 / 30 变成只剩 **关 / 5**；10 和 30 直接收起 |
| 休息还是 **30** 时把周末改成 10 | 休息**自动降成 5**，弹出「Break changed to 5 min: it has to fall inside the day's minutes」 |
| 把「最晚看到」设得比某档时长还短 | 该时长档自动收起（窗口装不下它） |
| 打开页面时存在历史遗留矛盾值 | 进页面即自愈，写回时顺手改掉，不会出现「整行没有一个选中」 |

「不限」和「不允许」不设上限，所以休息档位在那两种情况下全开。规则集中在 `KidsSetupActivity` 的 `breakFits / breakInside / shortestLimit / limitFits` 四个纯函数里，绑定时和刷新时用同一套判断，不会出现「拦得住点击却拦不住旧值」。

### 二、锁屏压不住播放器：补一个权限就够

根因是 Android 11 的后台启动限制：应用没有「显示在其他应用上层」权限时，`startActivity` 从后台拉起界面会被静默拒绝。所以以前在飞牛 TV 里正播着视频，锁屏只能等按一次 HOME 才出现。

| 改动 | 位置 |
|---|---|
| 声明 `SYSTEM_ALERT_WINDOW` | `AndroidManifest.xml` |
| 设置页加「Lock screen over video」按钮，显示 `on / off`，点一下走系统授权页 | `activity_kids_setup.xml`、`KidsSetupActivity.openOverlaySettings()` |
| 授权后守卫的启动不再被拦；**没授权时**每 3 个 tick 记一次失败，最多 10 分钟提示家长一次，不再默默失败 | `KidsWatch.tick()` |

这是最省事的一条路：一次性授权，之后不改代码、不加前台服务、不碰无障碍。

**实机验证**（MiTV-ASTR0，`com.trim.tv` 播放器在前台）：

```
appops get com.ts.launcher SYSTEM_ALERT_WINDOW  →  SYSTEM_ALERT_WINDOW: allow
mResumedActivity（等待前）  →  com.trim.tv/.modules.media.player.PlayerActivity
mResumedActivity（5 分钟后）→  com.ts.launcher/com.gathin.tvhome.KidsLockActivity
```

屏幕上出现「休息时间 / 4:01 后恢复。」，**全程没有按 HOME**，播放器被锁屏直接盖住。同一轮还验证了联动：周末 60 → 改 10，休息 30 自动变 5 并提示。

**体积**：APK 434,980 B → 436,708 B，多了 1,728 B。

**涉及文件**：`AndroidManifest.xml`、`activity_kids_setup.xml`、`strings.xml`、`KidsSetupActivity.java`、`KidsWatch.java`、`app/build.gradle`（`versionCode` 18 → 19，`versionName` → `2.8.8-linked-limits`）。

## v2.8.9 修改记录（学段→年级→科目三级选卷、题库 2210 题、程序坞归位）

### 一、题库从「三档」升级成「学段 → 年级 → 科目」

以前只有小学 / 初中 / 高中三档、每档三门课。现在按真实课表排：**先选学段，再选年级，再选科目**，年级和科目两行随学段重建。

| 学段 | 年级 | 科目 | 题量目标 | 实际 |
|---|---|---|---|---|
| 小学 | 一年级 … 六年级 | 语文、数学、英语 | 500 | **503** |
| 初中 | 初一、初二、初三 | 语文、数学、英语、历史、物理、化学 | 700 | **700** |
| 高中 | 高一、高二、高三 | 语文、数学、英语、物理、化学、生物、历史、地理、政治 | 1000 | **1007** |

合计 **2210 题**，`assets/kids-quiz.json` 363,141 B，字段 `st`（学段）/ `g`（年级）/ `s`（科目）/ `q` / `o` / `a` / `l`。题量比目标上下浮动几道，是为了每个「年级 × 科目」都凑成整组、不留半空的池子。

| 改动 | 位置 |
|---|---|
| 年级、六个科目开关持久化；`grades(stage)` / `stageSubjects(stage)` / `stageOf(grade)` / `chooseStage()` | `KidsPlan.java` |
| 题目带学段年级，`pick(context,n,stage,grade,subjects)` 只在本年级本学段出题，空池按「本年级 → 本学段 → 全库」三级回退 | `KidsBank.java` |
| 学段行固定三个芯片；年级行按学段动态生成（超过 3 个折成两行）；科目行最多两行 | `KidsSetupActivity.ensureChips()`、`activity_kids_setup.xml`、`kids_chip.xml` |
| 科目至少留一科，关到 0 时提示 | `kids_subject_need` |

### 二、答错时把错误答案打出来

以前只写「正确答案是 X」。现在写「**你选的是 X，正确答案是 Y**」，红框留在错选项、绿框标出正确项。

### 三、程序坞跑到时钟下面：回归修掉

现象：儿童模式下主页只有 2 个图标，偶尔会浮到左上角时间下面，不回底部；有时过一会儿又自己好了。

根因是程序坞的垂直位置只在 `grid.addOnLayoutChangeListener` 里算一次，而进出模式会 `reloadApps`：如果那一刻 grid 还没测量完（宽高为 0），算出来的 padding 极小，程序坞就被顶到时钟下面，之后没有新的布局事件，位置就卡在那儿。

| 改动 | 位置 |
|---|---|
| 位置计算抽成 `fitDockPadding(int,int)`，入口挡住 0 宽高 | `MainActivity.java` |
| `reloadApps` 末尾、模式切换后、`onWindowFocusChanged(true)` 各补一次重算 | 同上 |

实机验证（MiTV-ASTR0，装包后 `pkill` 再启动，冷启动直接进儿童模式）：

```
02:22 截图 → 儿童模式 / 飞牛TV 两个图标贴在屏幕底部，「儿童模式」大标题在上方
把 `DockFit` 日志临时打开时读到：w=1696 h=841 dockRow=true single=false rows=0 tileW=279 itemH=247 want=498 padTop=20 → 498
```

### 四、三个时间真正跟着「最晚看到」走

v2.8.8 已经做了「休息要落在当天分钟之内」，但窗口取的是 `最晚 - 开始时段`（固定 16 小时），所以任何时候 10/30/60/90/120 全都摆着，看着像没联动。

现在窗口改成**从此刻到下一个「最晚看到」还能用多久**（在时段之外则用整天窗口，晚上写的设置对明天照样有效），并把它同时约束三项：

| 现在 | 最晚看到 | 时长行 | 休息行 |
|---|---|---|---|
| 20:00 | 22:00 | 全部档位（还剩 120 分钟） | 关 / 5 / 10 / 30 |
| 21:00 | 22:00 | 只剩 10 / 30 / 60，90 和 120 收起 | 关 / 5 / 10 |
| 21:40 | 22:00 | 只剩 10 | 关 / 5 |
| 22:30（已过点） | 22:00 | 全部档位（今天已锁，设置是给明天） | 全开 |
| 任意 | 不限 | 全部档位 | 全开 |

选任一项都会重算另外两项：超出窗口的时长自动降到最长可行档并提示，休息再跟着新的最短时长收缩；进页面时也会先把历史遗留的矛盾值收一遍，不会出现整行没选中或选了却不生效。

规则复算脚本 `link_check.py`（仓库外本地跑）对 7 个场景断言「收下来的值必须在可见档位里、不能超出窗口、休息必须落在最短时长内」，全部通过。

### 五、设置页那条英文按钮改成中文

「Lock screen over video: on / off」在中文界面一直是英文，因为 `values-zh-rCN` 里根本没有 `kids_overlay_*` 四条。补齐后：

```
kids_overlay_ask  = 允许锁屏盖住正在播放的视频
kids_overlay_on   = 锁屏盖住视频：已开启
kids_overlay_off  = 锁屏盖住视频：未开启，点一下去开启
kids_overlay_need = 家长：请允许本应用「显示在其他应用的上层」，否则视频播放时锁屏盖不住。
```

打包后核验 `resources.arsc`：四条中文串 4/4 命中。中文资源里不再有整条英文的按钮（`check_zh.py` 扫过只剩 `%1$s` 这类格式串）。

**体积**：436,708 B（v2.8.8）→ 537,484 B。涨的 100 KB 几乎全是题库（363 KB JSON 压进 APK 后约 100 KB）。

**涉及文件**：`assets/kids-quiz.json`、`KidsPlan.java`、`KidsBank.java`、`KidsSetupActivity.java`、`KidsQuizActivity.java`、`MainActivity.java`、`activity_kids_setup.xml`、`kids_chip.xml`、`values/strings.xml`、`values-zh-rCN/strings.xml`、`app/build.gradle`（`versionCode` 19 → 20，`versionName` → `2.8.9-stage-grades`）。

## v2.9.0 修改记录（立即答题、设置页重排、答题居中、老人模式独立）

### 一、「立即答题」：和观看限制彻底分开

1. 儿童设置页「答题限制」下面多了一个 **立即答题** 按钮，点一下直接进答题界面，跟答题限制开关、每日组数、当天剩余时长都没有关系。
2. 儿童模式程序坞固定多一张 **立即答题** 卡片（金色星星图标），紧跟在「儿童模式」卡片后面。它不占白名单里的 app 名额，「调整应用顺序」也动不到它。
3. 答题界面改成卡通风格：奶油色渐变底、白色圆角题卡、四张答案卡、暖橙色的聚焦框，答对是绿色卡、答错是浅红色卡。
4. 星级奖励方案（与观看时长完全无关，只记账）：
   - 答对 1 题 = 1 ⭐，星星从下方飞进画面、回弹一下再飘走；
   - 一组全对额外 +1 ⭐（满星奖励）；
   - 答错不给星，下面显示「✊ 继续加油哦！」，同时保留上一版的「你选的是 X，正确答案是 Y」；
   - 星星累计存在本机（`Kids.stars()`，偏好键 `stars`），答题页右上角常显 ⭐ 总数；
   - 立即答题里的星星不受「星级奖励」开关限制（练习场景星星就是全部奖励）；锁屏加时流程仍按开关；
   - 星星不进 `KidsLedger`，不折算 `creditMs`，不影响每日上限、不参与任何锁定判断。
5. 立即答题只有一条出路：**回到儿童模式**。左上角有「返回」按钮，按 BACK 也是回儿童主页；从锁屏加时流程进来的答题仍然吃掉 BACK 键，不能逃出去。
6. 立即答题不清错题池、不写错题池、不看 `roundsPerDay`，可以一直练；错题池的清空仍然只在「只练不加时」玩法里发生。

### 二、儿童设置页重排

1. 每组加时 / 每次题量 / 每日组数 —— 从「标题一行 + 芯片一行」的两段式改成「标签在左、芯片在右」的三行紧凑排列（`KidsFieldRow` + `KidsChipSmall`），三项挨在一起，省掉近一半竖向空间。
2. 通过率挪到「科目」的下一行，顺序变成：学段 → 年级 → 科目 → 通过率 → 三行数字 → 玩法 → 错题加练 → 星级奖励 → 自动下一题 → 观看时段。
3. 答题限制的「打开 / 关闭」两个芯片合成一个状态芯片：开启时显示「已打开答题限制」，未开启显示「未打开答题限制」，点一下就切换。

### 三、答题界面不再切掉最后一个选项

根因：`activity_kids_quiz.xml` 是「竖排 LinearLayout + 没有滚动」，选项 78dp 高 × 4 再加 12dp 间距，在 1080p 电视上（density≈2，可视高度约 540dp）光选项就要 400dp 左右，加上 40sp 题干和反馈行直接顶穿屏幕底。

修法：
- 外面套 `ScrollView(fillViewport=true)`，内层 `LinearLayout(gravity=center_vertical)`：内容少于屏高时整块垂直居中，超过时才滚动（题干特别长也不会被截）；
- 选项 78dp → 56dp、间距 12dp → 8dp、字号 25sp → 23sp、题干 40sp → 25sp。单行题干 + 4 个选项约 520dp，落在 540dp 可视区内，整块居中显示。

### 四、老人模式：三项删掉，PIN 各管各的

1. 老人模式下 工作日时长 / 周末时长 / 休息间隔 三整块不再显示（`weekdaySection` / `weekendSection` / `breakSection` 设为 GONE），「最晚看到」照旧保留。
2. 逻辑上也真的不生效：`Kids.todayRules()` 在 `Kind.ELDERS` 时把 weekday / weekend / breakEvery 归零后再算锁，所以旧版本存下来的「60 分钟 / 休息 30 分钟」不会偷偷继续管着老人。
3. 设置页在老人模式下也不再对旧时间值做「超窗自愈」写回，避免把儿童模式的数字顺手改掉。
4. PIN 分两套：儿童的 `pin_hash` / `pin_arrows`，老人的 `pin_hash_elders` / `pin_arrows_elders`；`pinSet()` / `pinArrows()` / `pinMatches()` / `choosePin()` 一律按当前 kind 取。儿童 PIN 打不开老人模式设置页，反过来也一样，第一次进老人模式会要求单独设一个。

**涉及文件**：`launcher/app/build.gradle`（`versionCode` 20 → 21，`versionName` → `2.9.0-answer-now`）、`Kids.java`、`KidsQuizActivity.java`、`KidsSetupActivity.java`、`AppAdapter.java`、`MainActivity.java`、`activity_kids_setup.xml`、`activity_kids_quiz.xml`、`item_quiz_option.xml`、`styles.xml`、`values/strings.xml`、`values-zh-rCN/strings.xml`、`drawable/quiz_bg_cartoon.xml`、`quiz_card.xml`、`quiz_option_bg.xml`、`quiz_option_right.xml`、`quiz_option_wrong.xml`、`quiz_back_bg.xml`、`ic_kids_quiz.xml`。

### 五、实拍复验后的两处微调

1. 自由练习结算页原来误写「错题池清空了」，但立即答题路径并不清 `extraPool`（见本节一.6），已把 `quiz_practice_sub` 改为「答对 X / Y，继续加油！」，英文同步；
2. 「每组加时（分钟）」在 104dp 的标签列里折成两行，已把 `KidsFieldLabel` 加宽到 144dp、加 `maxLines=1` 锁定单行，英文标签同步缩短（`Round minutes / Questions / Rounds/day`）。

## 实机截图

下面 8 张全部在小米电视 **MiTV-ASTR0**（Android 11，1920×1080）上用 `adb shell screencap` 直接截取，没有修图，对应 `2.8.1-settings-weather`（v2.8.3 的界面截图待补）：

| 主页：农历在日期上一行、天气在下一行 | 设置下拉：输入源 / 网络 / 隐藏设置 / 系统设置 / 儿童/老人模式 / 调整应用顺序 |
|---|---|
| ![](docs/tvhome/01-home.jpg) | ![](docs/tvhome/02-settings-menu.jpg) |
| **儿童/老人模式：点开是二级列表** | **隐藏设置：壁纸与天气城市并列成行** |
| ![](docs/tvhome/03-mode-picker.jpg) | ![](docs/tvhome/04-hidden-settings.jpg) |
| **天气城市：45 个城市单选，默认成都** | **壁纸：跟随主题 / 无壁纸 / 必应滚动壁纸（保留 7 张）** |
| ![](docs/tvhome/05-weather-city.jpg) | ![](docs/tvhome/06-wallpaper-picker.jpg) |
| **儿童模式：底部没有「应用库」胶囊** | **家长 PIN：圆点只填实，不回显按过的方向** |
| ![](docs/tvhome/07-kids-mode.jpg) | ![](docs/tvhome/08-parent-pin.jpg) |

上面这一组是 v2.8.1 的界面，v2.8.3 之后的设置菜单与儿童模式没有变化。下面 6 张是 **v2.8.6** 在电视上重新实拍的，覆盖本轮改到的每一处：

| 主页 | 儿童模式：只剩允许的应用 |
|---|---|
| ![](docs/tvhome/14-home.jpg) | ![](docs/tvhome/09-kids-home.jpg) |
| **儿童设置：休息间隔只剩 关 / 5 / 10 / 30** | **家长 PIN：四个方向键，错了只提示「PIN 错误。」** |
| ![](docs/tvhome/10-kids-setup.jpg) | ![](docs/tvhome/11-parent-pin.jpg) |
| **时长用完后点「家长」验 PIN 才出现的加时面板** | **休息间隔到点弹出的休息锁屏（倒计时）** |
| ![](docs/tvhome/12-kids-used-up.jpg) | ![](docs/tvhome/13-kids-break.jpg) |

下面 4 张是 **v2.8.7** 的：

| 主页时钟：24 小时制，没有午别 | 用完状态：面板只剩 15 / 30 / 60，没有「今天不限」 |
|---|---|
| ![](docs/tvhome/15-kids-24h.jpg) | ![](docs/tvhome/16-kids-used-up-panel.jpg) |
| **答题入口：答对 3 道题可以再看 15 分钟** | **结算：通过啦，答对 2 / 3，加了 12 分钟** |
| ![](docs/tvhome/17-kids-quiz.jpg) | ![](docs/tvhome/18-kids-quiz-pass.jpg) |

下面 3 张是 **v2.8.8** 的：

| 周末改 10 分钟后，休息行只剩 关 / 5，并提示休息已降为 5 | 设置页新增「锁屏盖住视频」开关，已授权时显示 on |
|---|---|
| ![](docs/tvhome/19-kids-linked-rows.jpg) | ![](docs/tvhome/20-kids-overlay.jpg) |
| **休息锁屏直接压在正在播放的飞牛TV 上，没有按 HOME** | |
| ![](docs/tvhome/21-kids-break-over-player.jpg) | |

下面 4 张是 **v2.8.9** 的（本机新装后 `pkill` 重启、冷启动直接进儿童模式时截的）：

| 学段选小学：年级折成两行（一 / 二 / 三年级，四 / 五 / 六年级） | 学段选高中：高一 / 高二 / 高三，九科分两行 |
|---|---|
| ![](docs/tvhome/22-kids-grade-rows.jpg) | ![](docs/tvhome/23-kids-high-subjects.jpg) |
| **三个时间：工作日不允许 / 周末 10 / 最晚不限 / 休息关** | **程序坞归位：两个图标贴在屏幕底部，大标题在上方** |
| ![](docs/tvhome/24-kids-times.jpg) | ![](docs/tvhome/25-kids-dock-fixed.jpg) |

## 下载与安装

**安装包：** 到本 fork 的 [Releases](https://github.com/jankxia/cleanmitv/releases) 下载最新的 `tvhome-v*-signed.apk`（当前为 `tvhome-v2.9.0-answer-now-signed.apk`）；原项目的 `CleanMiTV-2.7.5-local-MiTV-OS3.apk`（版本 2.7.5-local）仍在 [原仓库 release 目录](https://github.com/sqlsec/cleanmitv/tree/main/release)

> ⚠️ 这个包针对小米 OS3 国行固件构建。该固件只允许白名单内的包名成为第三方 Home，所以用 `-PlauncherApplicationId=com.ts.launcher` 编译。Java 代码的命名空间仍然是 `com.gathin.tvhome`，没有修改系统、固件或签名权限。如果你的电视已经装了同包名的其他应用，会出现签名冲突。

**签名固定（很重要）**：本仓库 Actions 产出的 APK 一律用仓库 secret `TVHOME_KEYSTORE_B64` 里那份固定 key 签名（PKCS12，alias `tvhome`），所以后续版本都能 `adb install -r` 原地覆盖、设置与壁纸不丢。反过来说，**首次装到一台已经装了「别的 key 签的同包名应用」的电视时，必须先卸载一次**，否则会报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match`：

```bash
adb uninstall com.ts.launcher        # 只会删这一份 launcher 的数据，卸载后马上装新的
adb install tvhome-v2.8.4-quiz-time-signed.apk
```

之后再换版本就只需要 `adb install -r`。其他机型请按下方步骤从源码编译默认包名版本。

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
- 本 fork 另在 MiTV-ASTR0（Android 11，包名 `com.ts.launcher`）上用 adb 实测：三颗顶栏胶囊（第三颗现为「设置」）、设置下拉菜单（输入源 / 网络 / 隐藏设置 / 系统设置 / 儿童/老人模式 / 调整应用顺序）、壁纸弹窗三格（跟随主题 / 无壁纸 / 必应滚动壁纸）、必应图集标题「保留 N 张，最新在前」与日期标签、桌面换成当日必应 4K 图。
- 这台电视的应用显示模式只有 1080p 60 Hz，所以本项目不宣称桌面能跑到 120 / 144 Hz。CPU 数据来自电视 `top` 的短时采样，只用于同一台机器、同一场景下的前后对比。

## 许可证

MIT，与原项目一致。版权归原作者及贡献者所有，本 fork 的改动同样以 MIT 发布。
