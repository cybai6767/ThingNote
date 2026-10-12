# 记物本 · ThingNote

<img src="app-icon.png" width="112" alt="记物本图标">

一款简约的本地物品记录与保修到期提醒应用。

## 下载

**[下载记物本 0.1.7 APK](https://github.com/cybai6767/ThingNote/releases/download/v0.1.7/ThingNote-0.1.7.apk)**

[最新版本](https://github.com/cybai6767/ThingNote/releases/latest) · [全部版本](https://github.com/cybai6767/ThingNote/releases)

支持 Android 8.0 及以上。下载 APK 后在手机打开，按系统提示允许安装。

## 功能

- 概览：查看在保、即将过保、已过保和已处理物品，以及总资产、日均成本和分类金额。
- 添加：按项填写名称、分类、价格、购买及过保日期、退役日期、计算方式、备注与图片；日期和分类支持滚轮选择，只填名称也能保存。
- 我的：搜索、分类筛选、列表与日历视图，自定义分类和拖动排序。
- 设置：外观、提醒、本地备份导出与恢复，以及清理缓存。

总资产统计所有未处理物品，包含已过保和未设置过保日期的物品。按时间计算时，日均成本为价格除以持有天数，购买当天按 1 天，退役后截止到退役日期；按次数计算时显示单次成本，不混入日均统计；不计算均价的物品仍计入资产。

图片以压缩副本保存在本机，并包含在 JSON 备份内。新版备份格式为 v3，支持导入原 v1/v2 备份。

添加表单改版前的恢复点：[before-item-form-20261012](https://github.com/cybai6767/ThingNote/tree/before-item-form-20261012)（提交 `b990f7a`）。

## 数据

物品数据保存在本机。应用无账号、广告或云同步，建议定期导出本地备份。

本仓库公开应用源码、APK 下载和版本更新。签名密钥、密码、本地配置与用户数据不包含在源码中。

## 来源与许可

基于 Han Yongliang（keke-han）的 [expiry-alarm-android](https://github.com/keke-han/expiry-alarm-android) 修改。

采用 [MIT License](LICENSE)，保留原作者版权声明和许可证文本。第三方组件许可见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

## 技术栈与构建

Android 原生应用，使用 Kotlin、Jetpack Compose、Material 3 和 Gradle。支持 Android 8.0 及以上，编译 SDK 为 36。

配置 Android SDK 和 JDK 后，在项目根目录执行：

```bash
./gradlew assembleDebug
./gradlew testReleaseUnitTest
```

Windows 使用 `gradlew.bat` 替代 `./gradlew`。生成的调试 APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。

正式版签名配置参考 [keystore/daoqiji-release.properties.example](keystore/daoqiji-release.properties.example)。实际签名文件与密码仅保留本地，已加入 `.gitignore`。

正式版使用 `gradlew.bat assembleRelease` 构建，启用代码与资源裁剪；输出为 `app/build/outputs/apk/release/app-release.apk`。调试版保留调试工具，体积会大于正式版。

界面扩展图标按需保存在 `app/src/main/kotlin/com/daoqiji/app/icons`，可继续添加矢量图标；启动图标原始 PNG 保存在 `design-assets`。原始素材及完整源码不受 APK 裁剪影响。

安装包优化前恢复点：[before-apk-optimization-0.1.7](https://github.com/cybai6767/ThingNote/tree/before-apk-optimization-0.1.7)。更新内容见 [CHANGELOG.md](CHANGELOG.md)。
