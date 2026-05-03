# Reef 项目开发注意事项

## 构建环境

### Java 配置
- Java 位于: `/root/.sdkman/candidates/java/20.fx-zulu`
- 构建前需设置: `export JAVA_HOME=/root/.sdkman/candidates/java/20.fx-zulu`
- 构建命令: `cd /workspace/Reef && ./gradlew assembleDebug`

### Android SDK
- 位于: `/opt/android-sdk`

## Git 推送注意事项

- HTTPS 方式需要交互式认证，云主机环境无法直接推送
- 需在本地终端执行 `git push`，IDE 会弹出认证确认框
- 仓库地址: `https://github.com/hbpc002/reef-copy.git`

### 构建 APK
```bash
export JAVA_HOME=/root/.sdkman/candidates/java/20.fx-zulu
cd /workspace/Reef && ./gradlew clean assembleDebug
cd Reef/build/outputs/apk/debug
DATE=$(date +%Y%m%d) && cp Reef-debug.apk "Reef-debug-${DATE}.apk"
```

APK 输出目录: `/workspace/Reef/Reef/build/outputs/apk/debug/`

## 功能改动记录

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