<div align="center">

# 爱琦艺客户端

**一款更适合全球华人使用的增强型第三方 Telegram 客户端**

[![Latest Release](https://img.shields.io/github/v/release/dijuntuandui/Cherrygram?label=release)](https://github.com/dijuntuandui/Cherrygram/releases/latest)
[![License: GPL-2.0](https://img.shields.io/badge/license-GPL--2.0-blue.svg)](LICENSE)
[![Telegram 12.10.6](https://img.shields.io/badge/Telegram-12.10.6-26A5E4.svg)](https://core.telegram.org/api)

基于 [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) · 上游 [Telegram App for Android](https://github.com/DrKLO/Telegram)

</div>

---

## 这是什么

爱琦艺客户端（Aiqiyi）是 [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) 的中文向分支。Cherrygram 本身是 Telegram Android 官版源码的增强 fork，这个仓库在它之上做三件事：

1. 面向中文 / 华人使用场景的界面与体验修整（转场动画、输入区、资料页、壁纸层等）；
2. 一批自研插件，代码集中在 `uz.unnarsx.cherrygram.aiqiyi.*`，设置入口在 **爱琦艺插件功能设置 → 通用 → 爱琦艺插件**；
3. 可以直接跑通的构建环境：国内网络可用的 Gradle 镜像方案、容器化构建、一键出通用包。

## 下载

**[→ 前往 Releases 下载最新通用包](https://github.com/dijuntuandui/Cherrygram/releases/latest)**

- 资产名形如 `Cherrygram-PlayStore-11.3.0-TG-12.10.6-universal.apk`（armeabi-v7a + arm64-v8a）
- 每次构建生成 `build-<运行号>` 标签，附带该次提交的签名包

## 版本信息

| 项目 | 值 |
| --- | --- |
| 应用版本 | `12.10.6`（versionCode `7112`，输出覆盖为 `71120`） |
| Cherrygram 基线 | `11.3.0` |
| 包名 | `com.iosAiqiyi` |
| minSdk / targetSdk | `24` / `36`（`bundleAfat_SDK23` 变体为 23） |
| compileSdk | `36` |
| AGP / Gradle / JDK | `8.13.2` / `8.13` / `17` |
| NDK / CMake | `27.2.12479018` / `3.22.1` |
| ABI | `armeabi-v7a`, `arm64-v8a` |
| 许可证 | GPL-2.0 |

## 代码地图

```
TMessagesProj/src/main/java/uz/unnarsx/cherrygram/
├── aiqiyi/            # 本仓库自研插件（按功能分子目录）
│   ├── bookmark/  callzone/  cleanup/  comeback/  dialogue/
│   ├── local/     location/  overlay/  signin/    verified/
│   └── videozone/ wallpaper/ zone/
├── core/
│   ├── configs/       # 功能开关落盘配置（AiqiyiLocalDataConfig 等）
│   ├── huanghun/      # 视频库：通话视频、实时圆形录制、壁纸布局
│   └── helpers/ icons/ ui/ updater/ ...
├── preferences/       # Cherrygram 设置页，GeneralPreferencesEntry 内是插件总开关
├── chats/ misc/ camera/    # Cherrygram 原有增强模块
gradle/aiqiyi-mirrors.init.gradle   # 国内镜像注入（init script）
Dockerfile                          # 容器化构建
apkdiff.py / apkfrombundle.py       # APK 比对 / AAB 转 APK
```

## 构建

环境：JDK 17、Android SDK Platform 36 + Build-Tools 36.0.0、NDK `27.2.12479018`、CMake `3.22.1`，建议 40 GB 以上空闲磁盘。

1. 克隆并拉子模块（tlottie / td / ffmpeg / boringssl / libvpx 等十几个）：

   ```bash
   git clone --recursive --shallow-submodules https://github.com/dijuntuandui/Cherrygram.git
   cd Cherrygram
   # 忘了 --recursive 时补：
   git submodule update --init --recursive --depth=1
   ```

2. 签名文件 `Your_Key.jks` 放仓库根目录，口令从环境变量读取（缺省是占位值）：

   ```bash
   export STORE_PASSWORD=你的store口令
   export KEY_ALIAS=你的别名
   export KEY_PASSWORD=你的key口令
   ```

3. 写 `local.properties`：

   ```properties
   sdk.dir=/path/to/android-sdk
   ndk.dir=/path/to/android-sdk/ndk/27.2.12479018
   ```

4. 出包：

   ```bash
   chmod +x gradlew
   ./gradlew --no-daemon --parallel :TMessagesProj_App:assembleAfatRelease
   ```

   产物：`TMessagesProj_App/build/outputs/apk/afat/release/Cherrygram-PlayStore-11.3.0-TG-12.10.6-universal.apk`

其他变体：

```bash
./gradlew :TMessagesProj_App:bundleBundleAfatRelease        # AAB
./gradlew :TMessagesProj_AppStandalone:assembleAfatStandalone
./gradlew :TMessagesProj_AppHuawei:assembleAfatRelease
```

国内网络不用额外配镜像：`gradlew` 会自动注入 `gradle/aiqiyi-mirrors.init.gradle`，把腾讯云 `gradle-plugins` / `maven-public` 插到仓库列表最前面，官方源留作回落。要强制走官方源，把那行 `set -- -I "$AIQIYI_INIT" "$@"` 临时注释掉。

不想装环境就用容器，镜像已内置 SDK / NDK / CMake：

```bash
docker build -t aiqiyi-build .
docker run --rm -v "$PWD":/home/source aiqiyi-build
```

## 致谢

- [Telegram App for Android](https://github.com/DrKLO/Telegram) — 基础客户端
- [Cherrygram](https://github.com/arsLan4k1390/Cherrygram) — 本仓库保留其全部版权头
- [Nekogram](https://gitlab.com/Nekogram/Nekogram) — 书签等设计参考
- [Catogram](https://github.com/Catogram/Catogram)、[exteraGram](https://github.com/exteraSquad/exteraGram)、[OwlGram](https://github.com/OwlGramDev/OwlGram)、[Telegraher](https://github.com/nikitasius/Telegraher)、[Telegram Monet](https://github.com/c3r5b8/Telegram-Monet)

## 许可证

GPL-2.0，见 [LICENSE](LICENSE)。本项目是 Telegram Android 与 Cherrygram 的衍生物，分发时须同样以 GPL-2.0 开放源码，并保留文件头部的原始版权声明。