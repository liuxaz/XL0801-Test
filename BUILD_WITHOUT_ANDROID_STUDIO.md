# 不安装 Android Studio 的编译方法

## 推荐：GitHub Actions 在线编译

1. 新建一个 GitHub 仓库。
2. 把本项目全部文件上传到仓库根目录。
3. 打开仓库的 `Actions`。
4. 选择 `Build Android APK`。
5. 点击 `Run workflow`。
6. 编译完成后，在该次运行页面底部下载：
   `XL0801-debug-apk`
7. 解压后得到：
   `app-debug.apk`

这个 APK 是 Debug 签名，可直接安装测试。

## 本地命令行编译

只需要安装：
- JDK 17
- Android SDK Command-line Tools
- Gradle 8.0.2

然后在项目根目录运行：

```bash
gradle assembleDebug
```

APK 输出：
`app/build/outputs/apk/debug/app-debug.apk`
