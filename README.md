# 记物本 · ThingNote

<img src="app-icon.png" width="112" alt="记物本图标">

一款简约的本地物品记录与保修到期提醒应用。

## 下载

**[下载记物本 0.1.6 APK](https://github.com/cybai6767/ThingNote/releases/download/v0.1.6/ThingNote-0.1.6.apk)**

[最新版本](https://github.com/cybai6767/ThingNote/releases/latest) · [全部版本](https://github.com/cybai6767/ThingNote/releases)

支持 Android 8.0 及以上。下载 APK 后在手机打开，按系统提示允许安装。

## 功能

- 概览：查看在保、即将过保、已过保和已处理物品，以及总资产、日均成本和分类金额。
- 添加：一句话识别物品、到期日期、金额及提醒时间，也支持快捷添加和手动填写。
- 我的：搜索、分类筛选、列表与日历视图，自定义分类和拖动排序。
- 设置：外观、提醒以及本地备份导出与恢复。

总资产统计所有未处理物品，包含已过保物品。单件日均成本为金额除以已持有天数，购买当天按 1 天；概览汇总可计算物品的日均成本。

## 数据

物品数据保存在本机。应用无账号、广告或云同步，建议定期导出本地备份。

本仓库公开应用源码、APK 下载和版本更新。签名密钥、密码、本地配置与用户数据不包含在源码中。

## 原项目与改版说明

记物本基于 [keke-han/expiry-alarm-android（到期闹钟）](https://github.com/keke-han/expiry-alarm-android) 修改。

原作者为 **Han Yongliang（[keke-han](https://github.com/keke-han)）**。感谢原作者提供的开源基础。

本改版由 **[cybai6767](https://github.com/cybai6767)** 维护，主要增加物品管理、四个底部导航页面、自定义分类与拖动排序、资产和日均成本统计，并调整界面、图标与金额输入，移除语音输入。

本项目继续采用 **[MIT License](LICENSE)**，保留原作者版权声明和完整许可证。第三方组件各自的许可证见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 技术栈与构建

Android 原生应用，使用 Kotlin、Jetpack Compose、Material 3 和 Gradle。支持 Android 8.0 及以上，编译 SDK 为 36。

配置 Android SDK 和 JDK 后，在项目根目录执行：

```bash
./gradlew assembleDebug
./gradlew testReleaseUnitTest
```

Windows 使用 `gradlew.bat` 替代 `./gradlew`。生成的调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

正式版签名配置参考 [keystore/daoqiji-release.properties.example](keystore/daoqiji-release.properties.example)。实际签名文件与密码仅保留本地，已加入 `.gitignore`。
