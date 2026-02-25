# ETH Android 系统级悬浮窗

这个工程会在安卓上启动一个可拖动的系统级浮窗（WebView），用于显示你的 ETH 实时交易面板。

## 功能
- 系统级悬浮窗（可覆盖在交易所 APP 上）
- 可拖动位置
- 一键最小化 / 展开
- 一键关闭浮窗
- 前台服务保活（通知栏常驻）

## 导入与编译
1. 用 Android Studio 打开目录 `android-float-overlay`
2. 等待 Gradle 同步完成
3. 连接手机并运行

## 手机上使用
1. 打开 APP
2. 填写面板地址（建议 `https://你的域名/?mini=1`）
3. 点击 `1) 授予悬浮窗权限`
4. 点击 `2) 启动悬浮窗`
5. 切到交易所 APP，浮窗会继续显示

## 注意
- 你的面板地址必须公网可访问（隧道断开会显示加载失败）
- 某些国产 ROM 需要额外开启“后台弹出界面/自启动/省电白名单”

## 关键文件
- `app/src/main/java/com/codex/ethoverlay/MainActivity.kt`
- `app/src/main/java/com/codex/ethoverlay/OverlayService.kt`
- `app/src/main/AndroidManifest.xml`
