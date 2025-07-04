# 🎵 Android 音乐播放器架构设计文档

## 1. 总体架构概述

音乐播放器应用采用 **MVVM (Model-View-ViewModel)** 架构模式，结合前台服务（Service）实现后台播放功能。架构设计注重模块化、可维护性和扩展性，各组件间通过接口和事件机制实现松耦合。

### 1.1 系统架构图

```plantuml
@startuml
!theme plain
skinparam componentStyle uml2
skinparam packageStyle rectangle
skinparam backgroundColor transparent

package "用户界面层 (UI Layer)" {
  [MainActivity] as main
  [PlaybackFragment] as playback
  [PlaylistFragment] as playlist
  [LyricsFragment] as lyrics
  
  [ViewModels] as viewmodels
}

package "业务逻辑层 (Domain Layer)" {
  [PlayerController] as controller
  [LyricsProcessor] as lyricsProcessor
  [PlaylistManager] as playlistManager
  [NotificationManager] as notifManager
}

package "数据层 (Data Layer)" {
  [SongRepository] as songRepo
  [LyricsRepository] as lyricsRepo
  [PreferenceManager] as prefManager
  database "本地存储" as storage
}

package "服务层 (Service Layer)" {
  [MusicPlayerService] as service
  [PlayerEngine (MediaPlayer/ExoPlayer)] as engine
  [AudioFocusHandler] as audioFocus
}

main --> playback
main --> playlist
main --> lyrics

playback --> viewmodels
playlist --> viewmodels
lyrics --> viewmodels

viewmodels --> controller
viewmodels --> playlistManager
viewmodels --> lyricsProcessor

controller --> service
lyricsProcessor --> lyricsRepo
playlistManager --> songRepo

service --> engine
service --> audioFocus
service --> notifManager

songRepo --> storage
lyricsRepo --> storage
prefManager --> storage
@enduml
```

### 1.2 架构设计原则

1. **关注点分离**：UI、业务逻辑和数据处理分离，提高可维护性
2. **单一职责**：每个组件只负责单一功能，减少组件间耦合
3. **依赖注入**：使用接口和工厂模式实现组件间依赖关系
4. **观察者模式**：状态变化通过观察者模式传播，保持数据一致性
5. **生命周期感知**：组件感知 Android 生命周期，防止内存泄漏和崩溃

## 2. 模块划分结构

### 2.1 音频播放模块

音频播放模块负责音乐文件的加载、播放控制和状态管理，是整个应用的核心。

```plantuml
@startuml
!theme plain
skinparam packageStyle rectangle
skinparam backgroundColor transparent

package "音频播放模块" {
  class MusicPlayerService {
    - playerEngine: IPlayerEngine
    - audioFocusHandler: AudioFocusHandler
    - notificationManager: PlayerNotificationManager
    - mediaSession: MediaSessionCompat
    + startPlayback()
    + pausePlayback() 
    + stopPlayback()
    + seekTo(position)
    + playNext()
    + playPrevious()
  }
  
  interface IPlayerEngine {
    + prepare(uri)
    + play()
    + pause()
    + stop()
    + seekTo(position)
    + release()
    + isPlaying()
    + getCurrentPosition()
    + getDuration()
  }
  
  class MediaPlayerEngine implements IPlayerEngine {
    - mediaPlayer: MediaPlayer
  }
  
  class ExoPlayerEngine implements IPlayerEngine {
    - exoPlayer: ExoPlayer
  }
  
  class AudioFocusHandler {
    - audioManager: AudioManager
    - focusRequest: AudioFocusRequest
    + requestAudioFocus()
    + abandonAudioFocus()
  }
  
  class PlayerNotificationManager {
    + updateNotification(metadata, state)
    + cancelNotification()
  }
  
  class PlayerBroadcastReceiver {
    + onReceive(action)
  }
  
  MusicPlayerService --> IPlayerEngine
  MusicPlayerService --> AudioFocusHandler
  MusicPlayerService --> PlayerNotificationManager
  MusicPlayerService --> PlayerBroadcastReceiver
}
@enduml
```

**主要组件：**

1. **MusicPlayerService**：核心服务类，管理播放生命周期，保持后台播放能力
2. **IPlayerEngine**：播放引擎接口，支持切换 MediaPlayer/ExoPlayer 实现
3. **AudioFocusHandler**：音频焦点管理，处理中断事件（来电、其他应用播放）
4. **PlayerNotificationManager**：通知管理，展示和更新播放通知
5. **PlayerBroadcastReceiver**：广播接收器，处理通知操作和媒体按钮事件

### 2.2 播放列表管理模块

负责音乐曲目数据的加载、缓存、排序和过滤等功能，提供播放列表管理能力。

```plantuml
@startuml
!theme plain
skinparam packageStyle rectangle
skinparam backgroundColor transparent

package "播放列表管理模块" {
  class PlaylistManager {
    - songRepository: SongRepository
    - currentPlaylist: List<Song>
    - currentIndex: int
    + loadPlaylist()
    + getCurrentSong()
    + nextSong()
    + previousSong()
    + addSong(song)
    + removeSong(songId)
    + setPlayMode(mode)
    + getNextSongByMode()
  }
  
  class SongRepository {
    - localDataSource: LocalMusicDataSource
    - cachedSongs: Map<String, Song>
    + getSongs()
    + getSongById(id)
    + searchSongs(query)
    + scanLocalMusic()
  }
  
  class LocalMusicDataSource {
    - contentResolver: ContentResolver
    + queryAllMusic()
    + queryMusicById(id)
  }
  
  enum PlayMode {
    SEQUENCE
    LOOP
    SHUFFLE
  }
  
  class SongAdapter {
    - songList: List<Song>
    - clickListener: OnItemClickListener
    + onBindViewHolder()
    + updateSongs(songs)
    + highlightCurrentSong(position)
  }
  
  class Song {
    - id: String
    - title: String
    - artist: String
    - album: String
    - uri: Uri
    - duration: long
    - albumArt: Uri
  }
  
  PlaylistManager --> SongRepository
  PlaylistManager --> PlayMode
  PlaylistManager o-- Song
  SongRepository --> LocalMusicDataSource
  SongAdapter o-- Song
}
@enduml
```

**主要组件：**

1. **PlaylistManager**：播放列表业务逻辑，管理当前播放曲目和播放模式
2. **SongRepository**：数据仓库层，统一数据访问接口，处理缓存逻辑
3. **LocalMusicDataSource**：本地数据源，通过 ContentResolver 访问音乐文件
4. **SongAdapter**：RecyclerView 适配器，负责列表 UI 展示和交互
5. **Song**：歌曲数据模型，封装歌曲元数据

### 2.3 歌词处理模块

负责 LRC 歌词文件的解析、加载、时间同步和渲染展示，实现歌词滚动和高亮功能。

```plantuml
@startuml
!theme plain
skinparam packageStyle rectangle
skinparam backgroundColor transparent

package "歌词处理模块" {
  class LyricsProcessor {
    - lyricsRepository: LyricsRepository
    - currentLyrics: Lyrics
    - currentLine: int
    + loadLyrics(songId)
    + findLineByTime(timeMs)
    + getDisplayLines(currentLine)
  }
  
  class LyricsRepository {
    - localSource: LocalLyricsDataSource
    - cache: LRUCache<String, Lyrics>
    + getLyricsBySong(song)
    + searchLyricsByKeyword(artist, title)
    + saveLyrics(songId, lyrics)
  }
  
  class LocalLyricsDataSource {
    - context: Context
    + readLrcFile(path)
    + writeLrcFile(lyrics, path)
    + scanLrcDirectory()
  }
  
  class LrcParser {
    + {static} parse(content): Lyrics
    - parseTimeTag(tag): long
    - extractLyrics(line): String
  }
  
  class LyricsView extends View {
    - lyrics: List<LyricLine>
    - currentLine: int
    - textPaint: Paint
    - highlightPaint: Paint
    + setLyrics(lyrics)
    + updateCurrentLine(line)
    + smoothScrollToLine(line)
    # onDraw(canvas)
  }
  
  class Lyrics {
    - songId: String
    - lines: List<LyricLine>
    + getLineByTime(timeMs)
    + isEmpty()
  }
  
  class LyricLine {
    - timeMs: long
    - text: String
    - translation: String
  }
  
  LyricsProcessor --> LyricsRepository
  LyricsProcessor o-- Lyrics
  LyricsRepository --> LocalLyricsDataSource
  LyricsRepository --> LrcParser
  LocalLyricsDataSource --> LrcParser
  LyricsView o-- Lyrics
  LyricsView o-- LyricLine
  Lyrics o-- LyricLine
}
@enduml
```

**主要组件：**

1. **LyricsProcessor**：歌词业务逻辑，控制歌词加载与同步
2. **LyricsRepository**：歌词数据仓库，实现缓存和数据访问抽象
3. **LocalLyricsDataSource**：本地歌词文件读写操作
4. **LrcParser**：LRC 文件解析器，解析时间标签和歌词文本
5. **LyricsView**：自定义 View，实现歌词展示、滚动和高亮效果
6. **Lyrics**：歌词数据模型，包含歌词行集合和查找方法
7. **LyricLine**：歌词行数据模型，包含时间戳和文本内容

### 2.4 UI 控制模块

负责用户界面交互逻辑，处理用户输入，更新界面展示，并与其他模块协调工作。

```plantuml
@startuml
!theme plain
skinparam packageStyle rectangle
skinparam backgroundColor transparent

package "UI 控制模块" {
  class MainActivity {
    - viewPager: ViewPager2
    - navigationView: BottomNavigationView
    - viewModel: MainViewModel
    # onCreate()
    # onStart()
    # onDestroy()
    - bindService()
    - setupViewPager()
  }
  
  class PlaybackFragment {
    - viewModel: PlaybackViewModel
    - albumView: ImageView
    - seekBar: SeekBar
    - lyricsView: LyricsView
    - coverView: View
    + onCreate()
    + onViewCreated()
    - setupUI()
    - observeViewModel()
    - setupAnimations()
  }
  
  class PlaylistFragment {
    - viewModel: PlaylistViewModel
    - recyclerView: RecyclerView
    - adapter: SongAdapter
    + onCreate()
    + onViewCreated()
    - setupRecyclerView()
    - observeViewModel()
  }
  
  class PlaybackViewModel {
    - playerController: PlayerController
    - lyricsProcessor: LyricsProcessor
    - playerState: LiveData<PlayerState>
    - currentSong: LiveData<Song>
    - playbackPosition: LiveData<Int>
    - currentLyrics: LiveData<Lyrics>
    + playPause()
    + next()
    + previous()
    + seekTo(position)
    + toggleView()
  }
  
  class PlaylistViewModel {
    - playlistManager: PlaylistManager
    - songs: LiveData<List<Song>>
    - currentSongIndex: LiveData<Int>
    + loadPlaylist()
    + playSong(position)
    + removeSong(position)
    + sortPlaylist(order)
  }
  
  class MainViewModel {
    - isBound: MutableLiveData<Boolean>
    - isServiceRunning: MutableLiveData<Boolean>
    + bindMusicService()
    + unbindMusicService()
  }
  
  MainActivity o-- PlaybackFragment
  MainActivity o-- PlaylistFragment
  MainActivity o-- MainViewModel
  PlaybackFragment o-- PlaybackViewModel
  PlaylistFragment o-- PlaylistViewModel
}
@enduml
```

**主要组件：**

1. **MainActivity**：应用主活动，负责整体界面框架和导航逻辑
2. **PlaybackFragment**：播放控制界面，展示封面、控制按钮和进度条
3. **PlaylistFragment**：播放列表界面，展示和管理歌曲列表
4. **ViewModels**：ViewModel 类，连接 UI 和业务逻辑，管理界面状态数据
5. **动画控制器**：管理封面旋转、视图切换等动画效果

## 3. 类图与数据结构设计

### 3.1 核心类图

```plantuml
@startuml
!theme plain
skinparam classAttributeIconSize 0
skinparam backgroundColor transparent

class MusicPlayerManager {
  - playerEngine: IPlayerEngine
  - serviceCallback: ServiceCallback
  - currentState: PlayerState
  - playMode: PlayMode
  + initialize()
  + play()
  + pause()
  + stop()
  + seekTo(position)
  + next()
  + previous()
  + setPlayMode(mode)
  + release()
  + getCurrentPosition(): int
  + getDuration(): int
  + isPlaying(): boolean
}

interface IPlayerEngine {
  + initialize()
  + prepare(uri: Uri)
  + play()
  + pause()
  + stop()
  + seekTo(position: int)
  + release()
  + getCurrentPosition(): int
  + getDuration(): int
  + isPlaying(): boolean
  + setOnCompletionListener(listener)
  + setOnErrorListener(listener)
  + setOnPreparedListener(listener)
}

class MediaPlayerImpl implements IPlayerEngine {
  - mediaPlayer: MediaPlayer
  - currentUri: Uri
}

class ExoPlayerImpl implements IPlayerEngine {
  - exoPlayer: ExoPlayer
  - currentUri: Uri
}

interface ServiceCallback {
  + onPlaybackStateChanged(state)
  + onPlaybackPositionChanged(position)
  + onPlaybackCompleted()
  + onError(error)
}

class MusicPlayerService implements ServiceCallback {
  - musicPlayerManager: MusicPlayerManager
  - notificationManager: PlayerNotificationManager
  - audioFocusHandler: AudioFocusHandler
  - binder: MusicBinder
  + onBind(): IBinder
  + onStartCommand(): int
  + onDestroy()
}

class SongAdapter {
  - songs: List<Song>
  - currentPosition: int
  - clickListener: OnSongClickListener
  + onCreateViewHolder()
  + onBindViewHolder()
  + setCurrentSong(position)
  + updateSongs(newSongs)
}

class LrcParser {
  + {static} parseLrc(content: String): Lyrics
  - parseTimestamp(tag: String): long
}

MusicPlayerManager --> IPlayerEngine
MusicPlayerManager --> ServiceCallback

MusicPlayerService o-- MusicPlayerManager
MusicPlayerService o-- "1" AudioFocusHandler
MusicPlayerService o-- "1" PlayerNotificationManager
@enduml
```

### 3.2 数据模型设计

```plantuml
@startuml
!theme plain
skinparam classAttributeIconSize 0
skinparam backgroundColor transparent

class Song {
  - id: String
  - title: String
  - artist: String
  - album: String
  - duration: long
  - path: String
  - albumArtUri: Uri
  + getReadableTimeString(): String
}

class Playlist {
  - id: String
  - name: String
  - songs: List<Song>
  - dateCreated: Date
  + addSong(song)
  + removeSong(index)
  + moveSong(fromIndex, toIndex)
  + size(): int
  + getSongAt(index): Song
}

class Lyrics {
  - songId: String
  - lyricLines: List<LyricLine>
  + getLineByTimeMs(timeMs): LyricLine
  + getNextLine(currentLine): LyricLine
  + isEmpty(): boolean
  + size(): int
}

class LyricLine {
  - timeMs: long
  - text: String
  + getFormattedTime(): String
}

enum PlayMode {
  SEQUENCE
  LOOP
  SHUFFLE
  SINGLE_LOOP
}

enum PlayerState {
  IDLE
  INITIALIZED
  PREPARING
  PREPARED
  PLAYING
  PAUSED
  STOPPED
  COMPLETED
  ERROR
}

class PlaybackProgress {
  - position: int
  - duration: int
  - isBuffering: boolean
  + getPercentage(): float
  + getFormattedPosition(): String
  + getFormattedDuration(): String
}

class AudioFocusState {
  - hasAudioFocus: boolean
  - isTransientLoss: boolean
  - shouldPlayWhenFocusGained: boolean
}

Song "many" -- "many" Playlist
Lyrics --> "many" LyricLine
@enduml
```

## 4. 关键流程建模

### 4.1 播放流程：加载 → 播放 → 暂停/停止

```plantuml
@startuml
!theme plain
skinparam backgroundColor transparent

participant "UI/Fragment" as UI
participant "ViewModel" as VM
participant "PlayerController" as PC
participant "PlaylistManager" as PM
participant "MusicPlayerService" as SVC
participant "PlayerEngine" as PE

UI -> VM : playSong(songId)
VM -> PC : playSong(songId)
PC -> PM : getSongById(songId)
PM --> PC : song
PC -> SVC : prepareAndPlay(song)
SVC -> PE : prepare(song.uri)
PE --> SVC : onPrepared
SVC -> PE : play()
PE --> SVC : 播放状态更新
SVC --> PC : 广播播放状态
PC --> VM : 更新播放状态
VM --> UI : 更新UI (LiveData)

== 暂停流程 ==
UI -> VM : pausePlayback()
VM -> PC : pausePlayback()
PC -> SVC : pause()
SVC -> PE : pause()
PE --> SVC : 暂停状态更新
SVC --> PC : 广播暂停状态
PC --> VM : 更新播放状态
VM --> UI : 更新UI (LiveData)

== 停止流程 ==
UI -> VM : stopPlayback()
VM -> PC : stopPlayback()
PC -> SVC : stop()
SVC -> PE : stop()
PE --> SVC : 停止状态更新
SVC --> PC : 广播停止状态
PC --> VM : 更新播放状态
VM --> UI : 更新UI (LiveData)
@enduml
```

### 4.2 歌词同步流程：时间匹配 → 滚动 → 高亮

```plantuml
@startuml
!theme plain
skinparam backgroundColor transparent

participant "PlaybackFragment" as UI
participant "PlaybackViewModel" as VM
participant "LyricsProcessor" as LP
participant "PlayerController" as PC
participant "LyricsView" as LV
participant "LyricsRepository" as LR

== 歌词加载 ==
PC -> PC : 播放状态更新
PC -> VM : 更新当前歌曲信息
VM -> LP : loadLyrics(currentSong)
LP -> LR : getLyricsBySong(song)
LR --> LP : lyrics
LP --> VM : 当前歌词数据
VM --> UI : 更新歌词数据 (LiveData)
UI -> LV : setLyrics(lyrics)

== 歌词同步 (每100ms更新一次) ==
PC -> VM : 更新播放进度 (每100ms)
VM -> LP : findLineByTime(currentPosition)
LP -> LP : 在歌词中查找当前时间对应的行
LP --> VM : 当前行索引
VM --> UI : 更新当前行 (LiveData)
UI -> LV : updateCurrentLine(lineIndex)
LV -> LV : 计算滚动位置
LV -> LV : 高亮当前行
LV -> LV : 平滑滚动到当前位置
@enduml
```

### 4.3 视图切换流程：歌词 ↔ 封面

```plantuml
@startuml
!theme plain
skinparam backgroundColor transparent

participant "用户" as User
participant "PlaybackFragment" as UI
participant "PlaybackViewModel" as VM
participant "CoverView" as CV
participant "LyricsView" as LV
participant "PreferenceManager" as PM

== 封面视图切换到歌词视图 ==
User -> CV : 点击封面视图
CV -> UI : onViewClicked()
UI -> VM : toggleView()
VM -> VM : isShowingLyrics = true
VM --> UI : viewMode更新 (LiveData)
UI -> CV : 淡出动画
UI -> LV : 淡入动画
UI -> PM : saveViewPreference("lyrics")

== 歌词视图切换到封面视图 ==
User -> LV : 点击歌词视图
LV -> UI : onViewClicked()
UI -> VM : toggleView()
VM -> VM : isShowingLyrics = false
VM --> UI : viewMode更新 (LiveData)
UI -> LV : 淡出动画
UI -> CV : 淡入动画
UI -> PM : saveViewPreference("cover")

== 滑动切换视图 ==
User -> UI : 水平滑动
UI -> UI : 检测滑动方向
UI -> VM : toggleView()
VM -> VM : 切换isShowingLyrics
VM --> UI : viewMode更新 (LiveData)
UI -> UI : 执行相应视图切换动画
UI -> PM : 保存视图偏好
@enduml
```

### 4.4 播放列表交互流程：点击切歌、添加删除

```plantuml
@startuml
!theme plain
skinparam backgroundColor transparent

actor User
participant "PlaylistFragment" as UI
participant "SongAdapter" as Adapter
participant "PlaylistViewModel" as VM
participant "PlaylistManager" as PM
participant "PlayerController" as PC

== 点击切歌 ==
User -> Adapter : 点击歌曲项
Adapter -> UI : onSongClick(position)
UI -> VM : playSong(position)
VM -> PM : setCurrentIndex(position)
VM -> PC : playSong(song)
PC -> PC : 加载并播放歌曲
PC --> VM : 播放状态更新 (LiveData)
VM --> UI : 刷新UI状态
UI -> Adapter : highlightSong(position)
Adapter -> Adapter : 更新高亮项

== 添加歌曲 ==
User -> UI : 点击"添加歌曲"按钮
UI -> UI : 显示文件选择器
User -> UI : 选择音频文件
UI -> VM : addSong(uri)
VM -> PM : addSong(song)
PM -> PM : 添加到播放列表
PM --> VM : 更新列表数据
VM --> UI : 更新列表UI (LiveData)
UI -> Adapter : updateSongs(songs)
Adapter -> Adapter : 刷新列表显示

== 删除歌曲 ==
User -> Adapter : 左滑列表项
Adapter -> UI : 显示删除确认
User -> UI : 确认删除
UI -> VM : removeSong(position)
VM -> PM : removeSong(position)
PM -> PM : 从列表中删除
PM --> VM : 更新列表数据
VM --> UI : 更新列表UI (LiveData)
UI -> Adapter : updateSongs(songs)
Adapter -> Adapter : 刷新列表显示
@enduml
```

## 5. 性能优化策略

### 5.1 后台播放支持

为确保音乐在应用进入后台或屏幕关闭时仍能持续播放，采用以下策略：

1. **前台服务实现**：
   - 使用 `Service.startForeground()` 提高服务优先级
   - 配置持久通知，包含播放控制和元数据展示
   - 适配 Android 8.0+ 的通知渠道要求

2. **媒体会话集成**：
   - 使用 `MediaSessionCompat` 管理播放状态
   - 处理音频焦点争抢，响应系统音频事件
   - 支持耳机线控和系统媒体控制

```plantuml
@startuml
!theme plain
skinparam backgroundColor transparent

participant "系统" as System
participant "MusicPlayerService" as Service
participant "PlayerNotificationManager" as Notif
participant "MediaSessionCompat" as Session
participant "AudioFocusHandler" as Focus
participant "PlayerEngine" as Player

== 启动前台服务 ==
Service -> Focus : 请求音频焦点
Focus -> System : requestAudioFocus()
System --> Focus : 音频焦点获取结果
Focus --> Service : 焦点状态

Service -> Session : 更新播放状态
Session -> System : 更新媒体控制信息

Service -> Notif : createNotification(metadata, state)
Notif -> Notif : 创建含播放控制的通知
Service -> System : startForeground(notification)

== 处理音频中断 ==
System -> Focus : 音频焦点丢失事件
Focus -> Service : onAudioFocusLoss()
Service -> Player : pause()
Service -> Notif : updateNotification(PAUSED)
Service -> Session : setPlaybackState(PAUSED)

== 处理媒体按键 ==
System -> Session : 媒体按键事件
Session -> Service : onMediaButtonEvent()
Service -> Player : 执行相应操作(播放/暂停等)
Service -> Notif : 更新通知状态
@enduml
```

### 5.2 歌词/歌曲缓存机制

为提高应用性能，减少不必要的磁盘或网络访问，实现缓存机制：

1. **内存缓存策略**：
   - 使用 LRU 缓存存储最近使用的歌曲和歌词
   - 基于使用频率和内存压力调整缓存大小

2. **持久化缓存**：
   - 本地存储解析后的歌词结构，避免重复解析
   - 实现自定义序列化和反序列化，提高读写效率

3. **懒加载与预加载**：
   - 播放列表使用懒加载方式，仅加载可见项
   - 预加载即将播放的歌曲和歌词，提升响应速度

```plantuml
@startuml
!theme plain
skinparam backgroundColor transparent

participant "LyricsProcessor" as LP
participant "LyricsRepository" as LR
participant "MemoryCache" as MC
participant "DiskCache" as DC
participant "LocalSource" as LS

LP -> LR : getLyrics(songId)
LR -> MC : 查询内存缓存
alt 内存缓存命中
    MC --> LR : 返回缓存歌词
else 内存缓存未命中
    LR -> DC : 查询持久化缓存
    alt 持久化缓存命中
        DC --> LR : 返回缓存歌词
        LR -> MC : 添加到内存缓存
    else 持久化缓存未命中
        LR -> LS : 加载本地LRC文件
        LS -> LS : 解析LRC文件
        LS --> LR : 返回解析后的歌词
        LR -> MC : 添加到内存缓存
        LR -> DC : 添加到持久化缓存
    end
end
LR --> LP : 返回歌词对象

note right of LP : 同时预加载播放列表中\n下一首歌曲的歌词
@enduml
```

### 5.3 UI 动效与滚动流畅性优化

为保证界面流畅性和用户体验，针对动画和滚动效果进行优化：

1. **属性动画优化**：
   - 使用硬件加速提高动画性能
   - 控制动画复杂度，避免过度绘制
   - 合理使用 ValueAnimator 和 ObjectAnimator

2. **滚动优化**：
   - 实现高效歌词滚动，使用 SurfaceView 或 TextureView
   - 滚动视图使用渲染优化和回收机制
   - 避免嵌套滚动视图，减少布局层级

3. **异步处理**：
   - UI 主线程只处理交互和动画
   - 复杂计算和IO操作放入后台线程
   - 使用协程或线程池管理异步任务

```plantuml
@startuml
!theme plain
skinparam backgroundColor transparent

class LyricsView {
    - calculateLayoutOffScreen()
    - optimizeDrawing()
    - recycleInvisibleItems()
}

class AlbumCoverView {
    - useHardwareAcceleration()
    - optimizeRotationAnimation()
}

class "UI线程" as UIThread
class "IO线程池" as IOThread
class "计算线程" as CalcThread

UIThread -> LyricsView : 渲染可见歌词
IOThread -> LyricsView : 加载歌词文件
CalcThread -> LyricsView : 预计算布局
UIThread -> AlbumCoverView : 执行旋转动画

note right of UIThread
  主线程仅处理:
  1. 用户交互响应
  2. 动画渲染
  3. 布局测量和绘制
end note

note right of IOThread
  IO线程池处理:
  1. 文件读写
  2. 网络请求
  3. 数据库操作
end note

note right of CalcThread
  计算线程处理:
  1. 歌词行位置计算
  2. 列表过滤和排序
  3. 复杂业务逻辑
end note
@enduml
```

### 5.4 异常场景处理

为提高应用稳定性，实现全面的异常处理机制：

1. **播放失败恢复**：
   - 检测音频文件完整性和格式支持
   - 实现重试机制和播放失败降级策略
   - 音频解码错误时提供友好提示

2. **网络和存储异常**：
   - 监听网络状态变化，适当降级功能
   - 处理存储权限和可用空间不足情况
   - 实现断点续传和请求重试机制

3. **生命周期异常**：
   - 充分处理活动和片段生命周期
   - 避免内存泄漏和崩溃
   - 保存和恢复状态，提供无缝体验

```plantuml
@startuml
!theme plain
skinparam backgroundColor transparent

participant "MusicPlayerService" as Service
participant "PlayerEngine" as Engine
participant "ErrorHandler" as ErrHandler
participant "UI" as UI

== 播放错误处理 ==
Service -> Engine : 播放歌曲
Engine -> Engine : 尝试播放
Engine --> Service : onError(errorCode)
Service -> ErrHandler : handlePlaybackError(error)
activate ErrHandler

alt 网络错误
    ErrHandler -> ErrHandler : 检查网络连接
    ErrHandler -> Service : scheduleRetry()
else 文件损坏
    ErrHandler -> Service : skipToNext()
else 格式不支持
    ErrHandler -> UI : showUnsupportedFormatError()
else 其他错误
    ErrHandler -> UI : showGenericError()
    ErrHandler -> Service : resetPlayerState()
end

deactivate ErrHandler

== 资源释放 ==
Service -> Service : onDestroy()
Service -> Engine : release()
Service -> Service : 清理资源和缓存
@enduml
```

## 6. 总结

本架构设计文档详细描述了 Android 音乐播放器的软件架构、模块划分、关键流程和优化策略。设计遵循现代 Android 应用开发最佳实践，确保代码质量、可维护性和性能表现。通过模块化设计和清晰的依赖关系，应用能够灵活应对功能扩展和需求变更，同时提供流畅的用户体验。

### 6.1 实现注意事项

1. 遵循单一职责原则，各类和接口功能单一清晰
2. 依赖于抽象而非具体实现，提高代码灵活性
3. 合理处理生命周期和资源管理，避免内存泄漏
4. 异步操作使用合理的线程模型，避免阻塞主线程
5. 统一错误处理策略，提供友好的用户体验
6. 实现全面的日志和监控，便于问题排查

### 6.2 潜在改进方向

1. 支持云端音乐同步和播放
2. 集成均衡器和音效处理
3. 增强社交分享功能
4. 支持更多音频格式和播放特性
5. 添加歌词编辑功能
