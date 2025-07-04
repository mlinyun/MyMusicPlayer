# 🎵 MyMusicPlayer - Android 本地音乐播放器

<div align="center">
  <img src="https://img.shields.io/badge/Platform-Android-brightgreen.svg" alt="Platform">
  <img src="https://img.shields.io/badge/Language-Java-orange.svg" alt="Language">
  <img src="https://img.shields.io/badge/Min%20SDK-16-blue.svg" alt="Min SDK">
  <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License">
</div>

## 📖 项目简介

MyMusicPlayer 是一款功能完整、界面精美的 Android 本地音乐播放器应用。采用 Material Design 设计规范，提供流畅的音乐播放体验，支持歌词同步显示、播放列表管理、专辑封面展示等丰富功能。

## ✨ 主要特性

### 🎶 核心播放功能
- **完整播放控制**：播放/暂停/停止/上一曲/下一曲
- **进度控制**：可拖拽的播放进度条，精确控制播放位置
- **时间显示**：实时显示当前播放时间和总时长
- **后台播放**：支持后台播放，系统通知栏控制

### 📱 用户界面
- **双视图模式**：专辑封面视图 ↔ 歌词显示视图
- **响应式布局**：适配不同屏幕尺寸和方向
- **Material Design**：遵循谷歌设计规范，界面简洁美观
- **流畅动画**：专辑封面旋转、歌词滚动、视图切换动效

### 🎵 高级功能
- **歌词同步**：支持 `.lrc` 格式歌词文件，自动同步滚动和高亮
- **播放列表**：动态管理播放列表，支持添加/删除歌曲
- **当前播放高亮**：播放列表中当前播放歌曲高亮显示
- **本地音乐扫描**：自动扫描设备中的音乐文件

## 🏗️ 技术架构

### 核心技术栈
- **开发语言**：Java
- **音频播放引擎**：MediaPlayer / ExoPlayer
- **UI 框架**：Android Views + RecyclerView
- **动画框架**：属性动画 (ObjectAnimator)
- **最低支持版本**：Android 4.1 (API 16)

### 模块架构
```
com.mlinyun.mymusicplayer/
├── player/          # 播放引擎核心模块
│   ├── MusicPlayerService.java
│   └── MusicPlayerManager.java
├── ui/              # 用户界面模块
│   ├── MainActivity.java
│   ├── PlaybackFragment.java
│   └── LrcView.java
├── adapter/         # 列表适配器
│   └── SongAdapter.java
├── model/           # 数据模型
│   ├── Song.java
│   └── LrcLine.java
└── utils/           # 工具类
    ├── LrcParser.java
    └── PlaylistManager.java
```

## 🚀 快速开始

### 环境要求
- Android Studio 4.0+
- JDK 8+
- Android SDK (最低 API 16)
- Gradle 6.0+

### 安装步骤

1. **克隆项目**
    ```bash
    git clone https://github.com/mlinyun/MyMusicPlayer.git
    cd MyMusicPlayer
    ```

2. **导入项目**
   - 打开 Android Studio
   - 选择 "Open an existing Android Studio project"
   - 选择项目根目录

3. **配置环境**
   - 确保 Android SDK 已正确安装
   - 同步 Gradle 依赖

4. **运行应用**
   - 连接 Android 设备或启动模拟器
   - 点击 "Run" 按钮编译安装

### 权限配置

应用需要以下权限：
```xml
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WAKE_LOCK" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
```

## 📱 使用指南

### 基本操作
1. **添加音乐**：应用启动时自动扫描设备音乐文件
2. **播放控制**：点击播放按钮开始播放，支持暂停、上一曲、下一曲
3. **进度控制**：拖拽进度条调整播放位置
4. **视图切换**：点击专辑封面切换到歌词视图

### 高级功能
- **歌词显示**：将 `.lrc` 歌词文件放在音乐文件同目录下
- **播放列表**：在播放列表中点击歌曲直接播放
- **后台播放**：应用切换到后台时继续播放音乐

## 🎨 界面预览

| 主界面 | 歌词视图 | 播放列表 |
|:---:|:---:|:---:|
| 专辑封面 + 播放控制 | 实时歌词同步 | 歌曲列表管理 |

## 📋 开发计划

### 已完成功能 ✅
- [x] 基础播放控制
- [x] 播放列表管理
- [x] 歌词同步显示
- [x] 专辑封面展示
- [x] 后台播放支持

### 计划功能 📋
- [ ] 音效均衡器
- [ ] 播放模式切换（随机、循环）
- [ ] 音乐搜索功能
- [ ] 主题切换
- [ ] 播放历史记录

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request！

### 贡献流程
1. Fork 本仓库
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

### 开发规范
- 遵循 Android Java 代码规范
- 添加详细的中文注释
- 编写单元测试
- 更新相关文档

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- [Android Developer Documentation](https://developer.android.com/)
- [Material Design Guidelines](https://material.io/design)
- [ExoPlayer](https://github.com/google/ExoPlayer)

---
