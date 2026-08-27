# 信念播放器 (Affirmation Player)

通过自录音频的随机重复播放，强化你的想法和信念。仅在连接蓝牙耳机时播放，避免外放尴尬。

## 功能

- 自己录音（用你自己的声音）
- 设置重复次数和间隔时间
- 白天随机时间点自动触发播放
- 仅蓝牙耳机连接时播放（隐私模式）
- 手机重启后自动恢复服务

## 如何构建 APK（无需安装 Android Studio）

### 方法: GitHub Actions 自动构建

1. 在 GitHub 上创建一个新仓库（Public 或 Private 均可）
2. 将本项目所有文件推送到该仓库的 `main` 分支
3. 推送后 GitHub Actions 会自动开始构建
4. 进入仓库的 **Actions** 页面，点击最新的构建任务
5. 在页面底部 **Artifacts** 区域下载 `affirmation-app-debug-apk`
6. 解压后得到 `app-debug.apk` 文件

### 推送代码到 GitHub

如果你用命令行推送:

```bash
cd affirmation-app
git init
git add .
git commit -m "信念播放器 v1.0"
git branch -M main
git remote add origin https://github.com/你的用户名/affirmation-app.git
git push -u origin main
```

推送后等待 1-2 分钟，GitHub Actions 自动开始编译。

## 安装 APK

1. 将下载的 `app-debug.apk` 传到手机
2. 手机上点击安装（需开启「允许从此来源安装应用」）
3. 打开应用，授予录音、蓝牙、通知权限

## 小米 / 红米手机设置（关键）

MIUI 系统对后台限制严格，以下设置必须手动开启，否则自启动和后台播放无法工作:

### 1. 自启动权限
设置 → 应用设置 → 应用管理 → 信念播放器 → 自启动 → **允许**

### 2. 省电策略
设置 → 应用设置 → 应用管理 → 信念播放器 → 省电策略 → **无限制**

### 3. 锁定后台
打开最近任务列表（从底部上滑停顿）→ 找到信念播放器 → **下拉锁定**（出现锁图标）

### 4. 通知权限
设置 → 通知管理 → 信念播放器 → **允许通知**（前台服务必需）

## 使用方法

1. 打开应用，点击「开始录音」录制你的信念/想法
2. 录完后点击「试听」确认效果
3. 调整播放设置:
   - 重复次数: 每次触发播放几遍
   - 每次间隔: 每遍之间的间隔秒数
   - 最短/最长触发间隔: 两次播放之间的随机时间范围（分钟）
   - 仅蓝牙耳机播放: 开启后只有连接蓝牙耳机才播放
4. 打开「启用播放」开关
5. 手机连接蓝牙耳机后，到随机时间会自动播放

## 技术细节

- 语言: Kotlin
- UI: Jetpack Compose + Material3
- 最低系统: Android 8.0 (API 26)
- 目标系统: Android 14 (API 34)
- 音频格式: AAC (.m4a)
- 调度方式: AlarmManager.setExactAndAllowWhileIdle
- 蓝牙检测: BluetoothManager + HEADSET profile

## 本地构建（可选）

如果想在本地电脑构建:

1. 安装 [Android Studio](https://developer.android.com/studio)
2. 用 Android Studio 打开本项目文件夹
3. 等待 Gradle 同步完成
4. Build → Build Bundle(s)/APK(s) → Build APK(s)
5. APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`

## 许可证

个人使用，自由修改。
