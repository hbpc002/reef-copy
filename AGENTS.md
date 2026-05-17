# Reef 项目开发注意事项

## 构建环境

### Java 配置
- Java 位于: `/root/.sdkman/candidates/java/20.fx-zulu`
- 构建前需设置: `export JAVA_HOME=/root/.sdkman/candidates/java/20.fx-zulu`
- 构建命令: `cd /workspace/Reef && ./gradlew assembleDebug`

### Android SDK
- 位于: `/opt/android-sdk`

## Git 推送注意事项

- 必须使用 HTTPS: `https://github.com/hbpc002/reef-copy.git`
- SSH 方式会超时，勿用
- 执行 `git push` 时 IDE 会弹出认证确认框
- 每次推送前检查 remote 是否为 HTTPS:

### 一键构建 + 启动下载服务

```bash
export JAVA_HOME=/root/.sdkman/candidates/java/20.fx-zulu
export ANDROID_HOME=/opt/android-sdk

cd /workspace/Reef && ./gradlew clean assembleDebug && \

cd Reef/build/outputs/apk/debug && \
DATE=$(date +%Y%m%d-%H%M%S) && cp Reef-debug.apk "Reef-debug-${DATE}.apk" && \

pkill -f "http.server 8081" 2>/dev/null; sleep 0.1 && \
nohup python3 -m http.server 8081 --directory /workspace/Reef/Reef/build/outputs/apk/debug > /dev/null 2>&1 & disown
```

- APK: `Reef-debug-YYYYMMDD-HHMMSS.apk`
- 下载: `http://<服务器IP>:8081`
- 停止服务器: `pkill -f "http.server 8081"`

**自定义服务器脚本 (serve.py):**
- 显示文件详细信息（大小、修改日期）
- 自动格式化文件大小（B/KB/MB/GB）
- 已移至项目根目录: `/workspace/Reef/serve.py`
- 不会被 `./gradlew clean` 清理

## 踩过的坑

### 坑1：后台启动 HTTP 服务器

**错误做法**（在 opencode CLI 中会卡死）:
```bash
python3 -m http.server 8081 --directory ... &
cd /workspace/... && python3 -m http.server 8080 &
```

**正确做法**:
```bash
nohup python3 -m http.server 8081 --directory /path/to/dir > /dev/null 2>&1 &
disown
```

**原因**: opencode 的 Bash 工具会等待后台进程的 shell 退出，`&` 不够，必须 `disown` 才能从当前 shell 会话中完全脱离。

### 坑2：端口被占用

启动前先杀掉旧进程:
```bash
pkill -f "http.server 8081"
```
注意：pkill 匹配到 nohup 中的 http.server 自身进程时会导致 shell 卡死，建议加 `|| true` 或单独执行。

### 坑3：`getInstalledApplications` 在 API 36+ 仍受限

即使有 `QUERY_ALL_PACKAGES` 权限，`getInstalledApplications(0)` 在某些设备上仍返回空或不全。
**根因**: Android 包可见性限制在 API 36 上更严格，`QUERY_ALL_PACKAGES` 可能被 OEM 限制。
**正确做法**: 用 `LauncherApps.getActivityList(null, userHandle)` 代替，该 API 由系统 LauncherApps 服务提供，不受包可见性影响，且自带用户双开/分身感知。

### 坑4：国产 ROM 任务管理划掉应用会关闭无障碍

MIUI、ColorOS、Funtouch OS 等系统在任务管理划掉应用时不仅杀进程，还会**自动关闭该应用的无障碍服务权限**。
**解决方式**:
- 前台保活服务 + `onTaskRemoved` 通过 AlarmManager 1s 后重启
- 但无法编程式重新启用无障碍 — 只能用户手动恢复
- 引导用户锁住多任务（长按卡片点锁）+ 开启自启动 + 关闭电池优化

### 坑5：前台服务需要 POST_NOTIFICATIONS 权限

API 33+ 上 `startForeground()` 如果没有 `POST_NOTIFICATIONS` 权限会抛 `SecurityException`，导致整个进程崩溃。
**解决方式**: 确保请求了 `POST_NOTIFICATIONS` 权限，并且在服务启动时 try-catch。

### 坑6：编译缓存掩盖错误

修改某个文件时如果该文件广泛被引用或依赖了特定 API，增量编译可能掩盖了原有的编译错误。
**例子**: `MainSettingsScreen.kt` 的 `contentPadding.append()` 不存在，但因为缓存一直没被发现；删除 Import（DonateButton）触发了该文件完整重编译，错误才暴露。
**教训**: 修改文件后如果出现与修改无关的错误，检查是否是被缓存掩盖的旧问题。必要时 `./gradlew clean` 确认。

## 功能改动记录

### 2026-05-17: 循环限制 + 自定义锁定 + 保活 + 移除捐赠

**改动:**
- 锁定时长可自定义（分钟步进器 ±1/±5/±15，而非 5 个固定预设）
- 新增循环限制模式：每日限额/循环限制 两种模式切换（FilterChip）
  - 循环模式: 用 X 分钟 → 锁 Y 分钟 → 继续用 → 再锁 → ...
- KeepAliveService 前台保活服务 + onTaskRemoved AlarmManager 自启 + 服务恢复
- 移除主页面、设置页、关于页的捐赠按钮
- ReefWorker 无障碍关闭通知增加「重新开启」按钮直接跳转系统设置
- `loadAccessibleApps` 改用 `LauncherApps.getActivityList()` 修复空列表

**改动文件:**
- `Reef/src/main/java/dev/pranav/reef/util/AppLimits.kt` — CyclicConfig + 状态存储
- `Reef/src/main/java/dev/pranav/reef/screens/DailyLimitScreen.kt` — 循环模式 UI + 自定义步进器
- `Reef/src/main/java/dev/pranav/reef/MainActivity.kt` — 循环配置保存
- `Reef/src/main/java/dev/pranav/reef/services/AppLockService.kt` — 循环锁定逻辑
- `Reef/src/main/java/dev/pranav/reef/accessibility/UsageTracker.kt` — CYCLIC_LOCK BlockReason
- `Reef/src/main/java/dev/pranav/reef/accessibility/BlockerService.kt` — 循环锁定通知
- `Reef/src/main/java/dev/pranav/reef/services/KeepAliveService.kt` (新增)
- `Reef/src/main/java/dev/pranav/reef/App.kt` — KeepAliveService 启动
- `Reef/src/main/java/dev/pranav/reef/MainScreen.kt` — 移除 DonateDialog
- `Reef/src/main/java/dev/pranav/reef/screens/MainSettingsScreen.kt` — 移除 DonateButton
- `Reef/src/main/java/dev/pranav/reef/ui/about/AboutScreen.kt` — 移除捐赠按钮
- `Reef/src/main/java/dev/pranav/reef/util/ReefWorker.kt` — 通知加跳转按钮
- `Reef/src/main/AndroidManifest.xml` — KeepAliveService 注册
- `Reef/src/main/java/dev/pranav/reef/screens/CreateRoutineScreen.kt` — loadAccessibleApps 修复
- `Reef/src/main/res/values/strings.xml` + `values-zh-rCN/strings.xml` — 新字符串

### 2026-05-17: 密码保护功能 (App Lock)

### 2026-05-17: 密码保护功能 (App Lock)

**改动文件:**
- `Reef/src/main/java/dev/pranav/reef/screens/PasswordSettingsScreen.kt` (新增)
- `Reef/src/main/java/dev/pranav/reef/ui/lock/AppLockScreen.kt` (新增)
- `Reef/src/main/java/dev/pranav/reef/screens/SettingsModels.kt`
- `Reef/src/main/java/dev/pranav/reef/screens/SettingsScreen.kt`
- `Reef/src/main/java/dev/pranav/reef/screens/MainSettingsScreen.kt`
- `Reef/src/main/java/dev/pranav/reef/MainActivity.kt`
- `Reef/build.gradle.kts`
- `Reef/src/test/java/dev/pranav/reef/PasswordHashTest.kt` (新增)
- `Reef/src/test/java/dev/pranav/reef/TimerStateTest.kt` (新增)
- `Reef/src/androidTest/java/dev/pranav/reef/LockScreenTest.kt` (新增)
- `Reef/src/androidTest/java/dev/pranav/reef/NavigationTest.kt` (新增)
- `Reef/src/androidTest/java/dev/pranav/reef/PasswordSettingsTest.kt` (新增)

**功能说明:**
- App Lock 开关 (Settings → App Lock)
- 4-6 位 PIN 码设置/修改/移除
- SHA-256 哈希存储，不存明文
- 启动锁屏界面，数字键盘输入 PIN
- 每次从后台回到前台重新锁定
- 28 个单元测试 + 24 个 UI 测试

### 2026-05-17: Bug 修复 - 专注计划无法添加应用

**根因:**
- `loadAccessibleApps()` 中 `getApplicationLabel()` 在某些系统包上返回 null，`.toString()` 抛 NPE，被外层 try-catch 吞掉，导致整个函数返回空列表
- `queryIntentActivities` 的 launcher 包过滤 + `accessible` 门控逻辑导致在包可见性受限时过滤掉所有应用
- Manifest 缺少 `<queries>` 声明，在 API 36 上 `queryIntentActivities` 返回不完整

**修复:**
- `CreateRoutineScreen.kt` - 重写 `loadAccessibleApps()`: 逐项 try-catch，跳过异常应用；去除多余过滤，直接用 `getInstalledApplications(0)` 返回所有应用；按应用名排序
- `AndroidManifest.xml` - 添加 `<queries>` 声明 launcher intent，确保包可见性

### 2026-05-03: 应用自动锁定功能

**改动文件:**
- `Reef/src/main/java/dev/pranav/reef/services/AppLockService.kt` (新增)
- `Reef/src/main/java/dev/pranav/reef/accessibility/BlockerService.kt`
- `Reef/src/main/java/dev/pranav/reef/accessibility/UsageTracker.kt`
- `Reef/src/main/java/dev/pranav/reef/util/AppLimits.kt`
- `Reef/src/main/java/dev/pranav/reef/screens/DailyLimitScreen.kt`
- `Reef/src/main/java/dev/pranav/reef/screens/MainSettingsScreen.kt`
- `Reef/src/main/java/dev/pranav/reef/screens/CreateRoutineScreen.kt`
- `Reef/src/main/java/dev/pranav/reef/App.kt`
- `Reef/src/main/java/dev/pranav/reef/MainActivity.kt`
- `Reef/src/main/res/values/strings.xml`
- `Reef/src/main/AndroidManifest.xml`

**功能说明:**
- Auto Lock 开关 (MainSettingsScreen)
- 每日限制设置时可配置锁定时长 (DailyLimitScreen)
- AppLockService 前台服务检测使用时间并自动锁定
- BlockerService 阻止被锁定的应用

**Bug 修复 (2026-05-03):**
- AppLockService: 修正 getLimit 与 getLockDurationMs 的误用
- AppLockService: 优化锁定状态检查逻辑