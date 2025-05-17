package com.mlinyun.mymusicplayer.viewmodel;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.mlinyun.mymusicplayer.model.Lyrics;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.player.PlayMode;
import com.mlinyun.mymusicplayer.player.PlayerState;
import com.mlinyun.mymusicplayer.repository.LyricsRepository;
import com.mlinyun.mymusicplayer.repository.SongRepository;
import com.mlinyun.mymusicplayer.service.MusicPlayerService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 音乐播放器的ViewModel类
 * 负责连接UI和服务层，管理UI状态
 */
public class PlayerViewModel extends AndroidViewModel {
    private static final String TAG = "PlayerViewModel";

    // 服务连接相关
    private MusicPlayerService musicService;
    private boolean isServiceBound = false;
    private final MutableLiveData<Boolean> serviceConnected = new MutableLiveData<>(false);

    // 播放状态相关
    private final MutableLiveData<PlayerState> playerState = new MutableLiveData<>(PlayerState.IDLE);
    private final MutableLiveData<Integer> playbackPosition = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> duration = new MutableLiveData<>(0);
    private final MutableLiveData<Song> currentSong = new MutableLiveData<>();
    private final MutableLiveData<List<Song>> playlist = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentSongIndex = new MutableLiveData<>(-1);
    private final MutableLiveData<PlayMode> playMode = new MutableLiveData<>(PlayMode.SEQUENCE);
    private final MutableLiveData<Lyrics> currentLyrics = new MutableLiveData<>();

    // 是否正在加载歌曲
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);    // 仓库
    private final LyricsRepository lyricsRepository;
    private final SongRepository songRepository;

    // 搜索和排序
    private final MutableLiveData<String> searchFilter = new MutableLiveData<>("");
    private final MutableLiveData<SortMethod> sortMethod = new MutableLiveData<>(SortMethod.TITLE_ASC);
    private final MediatorLiveData<List<Song>> filteredSongs = new MediatorLiveData<>();

    // 扫描相关
    private final MutableLiveData<Boolean> scanning = new MutableLiveData<>(false);
    private final MutableLiveData<String> scanResultMessage = new MutableLiveData<>();

    /**
     * 排序方法枚举
     */
    public enum SortMethod {
        TITLE_ASC,     // 按标题升序
        TITLE_DESC,    // 按标题降序
        ARTIST_ASC,    // 按艺术家升序
        ARTIST_DESC,   // 按艺术家降序
        ALBUM_ASC,     // 按专辑升序
        ALBUM_DESC,    // 按专辑降序
        DURATION_ASC,  // 按时长升序
        DURATION_DESC  // 按时长降序
    }

    // 服务连接回调
    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicPlayerService.MusicBinder binder = (MusicPlayerService.MusicBinder) service;
            musicService = binder.getService();
            isServiceBound = true;
            serviceConnected.postValue(true);

            // 注册回调
            registerServiceCallback();

            // 获取初始数据
            updateFromService();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicService = null;
            isServiceBound = false;
            serviceConnected.postValue(false);
        }
    };
    // 服务回调
    private final MusicPlayerService.PlayerCallback serviceCallback = new MusicPlayerService.PlayerCallback() {
        @Override
        public void onPlayStateChanged(PlayerState state) {
            playerState.postValue(state);
        }

        @Override
        public void onPositionChanged(int position) {
            // 记录进度更新，帮助调试
            Log.d(TAG, "ViewModel收到位置更新: " + position + "ms");
            playbackPosition.postValue(position);
        }

        @Override
        public void onSongChanged(Song song) {
            currentSong.postValue(song);
            duration.postValue(musicService.getDuration());

            // 当歌曲变化时，加载歌词
            if (song != null) {
                loadLyrics(song);
            }
        }

        @Override
        public void onPlaylistChanged(List<Song> songs) {
            playlist.postValue(songs);
            currentSongIndex.postValue(musicService.getCurrentIndex());
        }

        @Override
        public void onError(Exception error) {
            // 可以添加错误处理逻辑
            Log.e(TAG, "播放错误", error);
        }

        @Override
        public void onDurationChanged(int duration) {
            Log.d(TAG, "接收到总时长更新: " + duration + "ms");
            PlayerViewModel.this.duration.postValue(duration);
        }
    };

    /**
     * 构造函数
     */
    public PlayerViewModel(@NonNull Application application) {
        super(application);

        // 初始化仓库
        lyricsRepository = new LyricsRepository(application);
        songRepository = new SongRepository(application);

        // 设置过滤和排序
        setupFilteredSongs();

        // 绑定服务
        bindService();
    }

    /**
     * 设置过滤和排序的观察者
     */
    private void setupFilteredSongs() {
        // 添加playlist数据源
        filteredSongs.addSource(playlist, songs -> applyFiltersAndSort());

        // 添加搜索过滤数据源
        filteredSongs.addSource(searchFilter, filter -> applyFiltersAndSort());

        // 添加排序方法数据源
        filteredSongs.addSource(sortMethod, method -> applyFiltersAndSort());
    }

    /**
     * 绑定音乐服务
     */
    private void bindService() {
        Intent intent = new Intent(getApplication(), MusicPlayerService.class);
        getApplication().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);

        // 同时启动服务，确保后台播放
        getApplication().startService(intent);
    }

    /**
     * 注册服务回调
     */
    private void registerServiceCallback() {
        if (musicService != null) {
            musicService.addCallback(serviceCallback);
        }
    }

    /**
     * 从服务更新数据
     */
    private void updateFromService() {
        if (musicService != null) {
            playerState.postValue(musicService.getPlayerState());
            playbackPosition.postValue(musicService.getCurrentPosition());
            duration.postValue(musicService.getDuration());
            currentSong.postValue(musicService.getCurrentSong());
            playlist.postValue(musicService.getPlaylist());
            currentSongIndex.postValue(musicService.getCurrentIndex());
            playMode.postValue(musicService.getPlayMode());

            // 加载当前歌曲的歌词
            Song song = musicService.getCurrentSong();
            if (song != null) {
                loadLyrics(song);
            }
        }
    }

    /**
     * 加载歌词
     */
    private void loadLyrics(Song song) {
        lyricsRepository.getLyricsBySong(song, new LyricsRepository.LyricsCallback() {
            @Override
            public void onLyricsLoaded(Lyrics lyrics) {
                currentLyrics.postValue(lyrics);
            }
        });
    }

    /**
     * 播放或暂停
     */
    public void togglePlayPause() {
        if (musicService == null) return;

        PlayerState state = musicService.getPlayerState();
        if (state == PlayerState.PLAYING) {
            musicService.pause();
        } else {
            // 如果当前有歌曲，则恢复播放，否则从头开始播放
            if (musicService.getCurrentSong() != null) {
                musicService.resume();
            } else if (!musicService.getPlaylist().isEmpty()) {
                musicService.playAtIndex(0);
            }
        }
    }

    /**
     * 播放下一首
     */
    public void playNext() {
        if (musicService != null) {
            musicService.playNext();
        }
    }

    /**
     * 播放上一首
     */
    public void playPrevious() {
        if (musicService != null) {
            musicService.playPrevious();
        }
    }

    /**
     * 跳转到指定位置
     */
    public void seekTo(int position) {
        if (musicService != null) {
            musicService.seekTo(position);
        }
    }

    /**
     * 播放指定位置的歌曲
     */
    public void playAtIndex(int index) {
        if (musicService != null) {
            musicService.playAtIndex(index);
        }
    }

    /**
     * 设置播放模式
     */
    public void setPlayMode(PlayMode mode) {
        if (musicService != null) {
            musicService.setPlayMode(mode);
            playMode.postValue(mode);
        }
    }

    /**
     * 切换播放模式
     */
    public void togglePlayMode() {
        if (musicService == null) return;

        PlayMode currentMode = musicService.getPlayMode();
        PlayMode newMode;

        // 循环切换模式
        switch (currentMode) {
            case SEQUENCE:
                newMode = PlayMode.LOOP;
                break;
            case LOOP:
                newMode = PlayMode.SHUFFLE;
                break;
            case SHUFFLE:
                newMode = PlayMode.SINGLE_LOOP;
                break;
            case SINGLE_LOOP:
            default:
                newMode = PlayMode.SEQUENCE;
                break;
        }

        setPlayMode(newMode);
    }

    /**
     * 播放指定歌曲
     *
     * @param song 要播放的歌曲
     */
    public void playSong(Song song) {
        if (musicService != null && song != null) {
            // 查找歌曲在播放列表中的位置
            List<Song> currentPlaylist = musicService.getPlaylist();
            int songIndex = -1;

            for (int i = 0; i < currentPlaylist.size(); i++) {
                if (currentPlaylist.get(i).getId().equals(song.getId())) {
                    songIndex = i;
                    break;
                }
            }

            // 如果歌曲在播放列表中，直接播放该位置
            if (songIndex != -1) {
                musicService.playAtIndex(songIndex);
            }
            // 否则将歌曲添加到播放列表末尾，并播放
            else {
                musicService.addSongAndPlay(song);
            }
        }
    }

    /**
     * 清理资源
     */
    @Override
    protected void onCleared() {
        super.onCleared();

        // 解除服务绑定
        if (isServiceBound) {
            if (musicService != null) {
                musicService.removeCallback(serviceCallback);
            }
            getApplication().unbindService(serviceConnection);
            isServiceBound = false;
        }
    }

    // 各种LiveData的getter方法

    public LiveData<Boolean> getServiceConnected() {
        return serviceConnected;
    }

    public LiveData<PlayerState> getPlayerState() {
        return playerState;
    }

    public LiveData<Integer> getPlaybackPosition() {
        return playbackPosition;
    }

    public LiveData<Integer> getDuration() {
        return duration;
    }

    public LiveData<Song> getCurrentSong() {
        return currentSong;
    }

    public LiveData<List<Song>> getPlaylist() {
        return playlist;
    }

    public LiveData<Integer> getCurrentSongIndex() {
        return currentSongIndex;
    }

    /**
     * 获取搜索过滤器LiveData
     *
     * @return 搜索过滤器LiveData
     */
    public MutableLiveData<String> getSearchFilter() {
        return searchFilter;
    }

    public LiveData<PlayMode> getPlayMode() {
        return playMode;
    }

    public LiveData<Lyrics> getCurrentLyrics() {
        return currentLyrics;
    }

    public LiveData<Boolean> getScanningStatus() {
        return scanning;
    }

    public LiveData<String> getScanResultMessage() {
        return scanResultMessage;
    }

    public LiveData<List<Song>> getFilteredSongs() {
        return filteredSongs;
    }

    /**
     * 设置搜索过滤条件
     *
     * @param query 搜索关键词
     */
    public void setSearchFilter(String query) {
        searchFilter.setValue(query);
        applyFiltersAndSort();
    }

    /**
     * 设置排序方法
     *
     * @param method 排序方法
     */
    public void setSortMethod(SortMethod method) {
        sortMethod.setValue(method);
        applyFiltersAndSort();
    }

    /**
     * 应用过滤和排序
     */
    private void applyFiltersAndSort() {
        List<Song> allSongs = playlist.getValue();
        if (allSongs == null) {
            allSongs = new ArrayList<>();
        }

        List<Song> results = new ArrayList<>();
        String query = searchFilter.getValue();

        // 应用搜索过滤
        if (query == null || query.isEmpty()) {
            // 非搜索模式，显示完整播放列表
            results.addAll(allSongs);
            // 确保所有歌曲都没有搜索标记
            for (Song song : results) {
                song.setSearchResult(false);
            }
        } else {
            // 搜索模式，显示匹配的搜索结果
            query = query.toLowerCase();

            // 首先从当前播放列表中查找匹配的歌曲
            Set<String> addedSongIds = new HashSet<>();
            for (Song song : allSongs) {
                if (song.getTitle().toLowerCase().contains(query)
                        || song.getArtist().toLowerCase().contains(query)
                        || song.getAlbum().toLowerCase().contains(query)) {
                    // 当前播放列表中的歌曲不标记为搜索结果
                    song.setSearchResult(false);
                    results.add(song);
                    addedSongIds.add(song.getId());
                }
            }

            // 然后查询本地存储中的其他匹配歌曲
            List<Song> localSongs = songRepository.searchLocalSongs(query);
            if (localSongs != null) {
                for (Song song : localSongs) {
                    // 避免添加已在播放列表中的歌曲
                    if (!addedSongIds.contains(song.getId())) {
                        song.setSearchResult(true);
                        results.add(song);
                    }
                }
            }
        }

        // 应用排序
        SortMethod method = sortMethod.getValue();
        if (method != null) {
            switch (method) {
                case TITLE_ASC:
                    Collections.sort(results, (s1, s2) -> s1.getTitle().compareToIgnoreCase(s2.getTitle()));
                    break;
                case TITLE_DESC:
                    Collections.sort(results, (s1, s2) -> s2.getTitle().compareToIgnoreCase(s1.getTitle()));
                    break;
                case ARTIST_ASC:
                    Collections.sort(results, (s1, s2) -> s1.getArtist().compareToIgnoreCase(s2.getArtist()));
                    break;
                case ARTIST_DESC:
                    Collections.sort(results, (s1, s2) -> s2.getArtist().compareToIgnoreCase(s1.getArtist()));
                    break;
                case ALBUM_ASC:
                    Collections.sort(results, (s1, s2) -> s1.getAlbum().compareToIgnoreCase(s2.getAlbum()));
                    break;
                case ALBUM_DESC:
                    Collections.sort(results, (s1, s2) -> s2.getAlbum().compareToIgnoreCase(s1.getAlbum()));
                    break;
                case DURATION_ASC:
                    Collections.sort(results, Comparator.comparingLong(Song::getDuration));
                    break;
                case DURATION_DESC:
                    Collections.sort(results, (s1, s2) -> Long.compare(s2.getDuration(), s1.getDuration()));
                    break;
            }
        }

        filteredSongs.setValue(results);
    }

    /**
     * 扫描音乐文件
     */
    public void scanMusic() {
        scanning.setValue(true);

        // 异步扫描
        new Thread(() -> {
            try {
                List<Song> songs = songRepository.scanMediaStore();
                playlist.postValue(songs);

                // 更新扫描结果消息
                String message = getApplication().getString(com.mlinyun.mymusicplayer.R.string.found_songs, songs.size());
                scanResultMessage.postValue(message);

            } catch (Exception e) {
                Log.e(TAG, "Failed to scan music", e);
                scanResultMessage.postValue("扫描失败: " + e.getMessage());
            } finally {
                scanning.postValue(false);
            }
        }).start();
    }

    /**
     * 刷新歌曲列表
     */
    public void refreshSongsList() {
        if (songRepository != null) {
            List<Song> songs = songRepository.getCachedSongs();
            if (songs != null && !songs.isEmpty()) {
                playlist.setValue(songs);
            }
        }
    }

    /**
     * 清除扫描结果消息
     */
    public void clearScanResultMessage() {
        scanResultMessage.setValue(null);
    }

    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    /**
     * 获取播放进度百分比
     */
    public int getProgressPercent() {
        Integer position = playbackPosition.getValue();
        Integer total = duration.getValue();

        if (position == null || total == null || total == 0) {
            return 0;
        }

        return (int) ((position * 100L) / total);
    }

    /**
     * 格式化时间为MM:SS格式
     */
    public String formatTime(int timeMs) {
        int totalSeconds = timeMs / 1000;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 添加歌曲到播放列表并立即播放
     *
     * @param song 要添加并播放的歌曲
     */
    public void addSongAndPlay(Song song) {
        if (musicService != null && song != null) {
            musicService.addSongAndPlay(song);
        }
    }

    /**
     * 仅添加歌曲到播放列表，不播放
     *
     * @param song 要添加的歌曲
     */
    public void addSong(Song song) {
        if (musicService != null && song != null) {
            // 先检查歌曲是否已经在播放列表中
            boolean songExists = false;
            List<Song> currentPlaylist = musicService.getPlaylist();

            for (Song existingSong : currentPlaylist) {
                if (existingSong.getId().equals(song.getId())) {
                    songExists = true;
                    break;
                }
            }

            // 如果歌曲不在播放列表中，则添加
            if (!songExists) {
                musicService.addSong(song);
            }
        }
    }

    /**
     * 处理专辑封面加载错误
     * 尝试从其他来源重新获取专辑封面
     *
     * @param song 需要处理的歌曲
     * @return 是否成功处理
     */
    public boolean handleAlbumArtLoadError(Song song) {
        if (song == null) {
            Log.e(TAG, "尝试处理专辑封面错误，但歌曲对象为null");
            return false;
        }

        SongRepository songRepository = new SongRepository(getApplication());
        android.net.Uri newUri = songRepository.handleAlbumArtLoadError(song);

        if (newUri != null) {
            Log.d(TAG, "成功处理专辑封面错误，使用新URI: " + newUri);
            // 通知UI更新
            currentSong.postValue(song);
            return true;
        } else {
            Log.e(TAG, "无法处理专辑封面错误，将使用默认封面");
            return false;
        }
    }
}
