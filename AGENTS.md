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

## 功能改动记录

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