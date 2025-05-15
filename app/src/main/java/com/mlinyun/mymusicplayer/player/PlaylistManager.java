package com.mlinyun.mymusicplayer.player;

import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.repository.SongRepository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * 播放列表管理器
 * 负责管理歌曲列表，提供排序、过滤、随机播放等功能
 * 维护当前播放位置和播放模式
 */
public class PlaylistManager {
    private static final String TAG = "PlaylistManager";

    // 排序方式枚举
    public enum SortOrder {
        TITLE_ASC,      // 按标题升序
        TITLE_DESC,     // 按标题降序
        ARTIST_ASC,     // 按艺术家升序
        ARTIST_DESC,    // 按艺术家降序
        ALBUM_ASC,      // 按专辑升序
        ALBUM_DESC,     // 按专辑降序
        DURATION_ASC,   // 按时长升序
        DURATION_DESC   // 按时长降序
    }

    // 数据源
    private final SongRepository songRepository;

    // 播放列表数据
    private final List<Song> originalPlaylist = new ArrayList<>();
    private final List<Song> currentPlaylist = new ArrayList<>();

    // 播放状态
    private int currentIndex = -1;
    private PlayMode playMode = PlayMode.SEQUENCE;

    // LiveData通知UI变化
    private final MutableLiveData<List<Song>> playlistLiveData = new MutableLiveData<>();
    private final MutableLiveData<Integer> currentIndexLiveData = new MutableLiveData<>();
    private final MutableLiveData<PlayMode> playModeLiveData = new MutableLiveData<>();

    // 随机播放使用的随机数生成器
    private final Random random = new Random();

    // 历史记录，用于"上一首"功能
    private final List<Integer> playHistory = new ArrayList<>();
    private static final int MAX_HISTORY_SIZE = 50;

    /**
     * 构造函数
     *
     * @param context 上下文
     */
    public PlaylistManager(Context context) {
        this.songRepository = new SongRepository(context);

        // 初始化LiveData
        playlistLiveData.setValue(currentPlaylist);
        currentIndexLiveData.setValue(currentIndex);
        playModeLiveData.setValue(playMode);
    }

    /**
     * 加载所有本地音乐
     */
    public void loadAllSongs() {
        List<Song> songs = songRepository.getAllSongs();
        if (songs != null) {
            originalPlaylist.clear();
            originalPlaylist.addAll(songs);
            resetPlaylist();
        }
    }

    /**
     * 重置播放列表为原始列表
     */
    private void resetPlaylist() {
        currentPlaylist.clear();
        currentPlaylist.addAll(originalPlaylist);
        currentIndex = currentPlaylist.isEmpty() ? -1 : 0;
        playlistLiveData.setValue(currentPlaylist);
        currentIndexLiveData.setValue(currentIndex);
    }

    /**
     * 获取当前歌曲
     *
     * @return 当前播放的歌曲，如果没有则返回null
     */
    public Song getCurrentSong() {
        if (currentIndex >= 0 && currentIndex < currentPlaylist.size()) {
            return currentPlaylist.get(currentIndex);
        }
        return null;
    }

    /**
     * 获取下一首歌曲的索引
     *
     * @return 下一首歌曲的索引
     */
    public int getNextSongIndex() {
        if (currentPlaylist.isEmpty()) {
            return -1;
        }

        switch (playMode) {
            case SHUFFLE:
                // 随机播放模式，返回随机索引
                return random.nextInt(currentPlaylist.size());

            case LOOP:
                // 列表循环模式，返回下一个索引，如果是最后一个则返回第一个
                return (currentIndex + 1) % currentPlaylist.size();

            case SINGLE_LOOP:
                // 单曲循环模式，返回当前索引
                return currentIndex;

            case SEQUENCE:
            default:
                // 顺序播放模式，返回下一个索引，如果是最后一个则返回-1
                int nextIndex = currentIndex + 1;
                return nextIndex < currentPlaylist.size() ? nextIndex : 0;
        }
    }

    /**
     * 获取上一首歌曲的索引
     *
     * @return 上一首歌曲的索引
     */
    public int getPreviousSongIndex() {
        if (currentPlaylist.isEmpty()) {
            return -1;
        }

        // 如果有播放历史，从历史记录中获取上一首
        if (!playHistory.isEmpty()) {
            return playHistory.remove(playHistory.size() - 1);
        }

        switch (playMode) {
            case SHUFFLE:
                // 随机播放模式，返回随机索引
                return random.nextInt(currentPlaylist.size());

            case LOOP:
                // 列表循环模式，返回上一个索引，如果是第一个则返回最后一个
                return (currentIndex - 1 + currentPlaylist.size()) % currentPlaylist.size();

            case SINGLE_LOOP:
                // 单曲循环模式，返回当前索引
                return currentIndex;

            case SEQUENCE:
            default:
                // 顺序播放模式，返回上一个索引，如果是第一个则保持第一个
                int prevIndex = currentIndex - 1;
                return prevIndex >= 0 ? prevIndex : currentPlaylist.size() - 1;
        }
    }

    /**
     * 移动到下一首歌曲
     *
     * @return 新的当前歌曲，如果没有则返回null
     */
    public Song moveToNext() {
        if (currentPlaylist.isEmpty()) {
            return null;
        }

        // 将当前索引添加到历史记录中
        addToHistory(currentIndex);

        // 获取下一首歌曲的索引
        currentIndex = getNextSongIndex();
        currentIndexLiveData.setValue(currentIndex);

        return getCurrentSong();
    }

    /**
     * 移动到上一首歌曲
     *
     * @return 新的当前歌曲，如果没有则返回null
     */
    public Song moveToPrevious() {
        if (currentPlaylist.isEmpty()) {
            return null;
        }

        // 获取上一首歌曲的索引
        currentIndex = getPreviousSongIndex();
        currentIndexLiveData.setValue(currentIndex);

        return getCurrentSong();
    }

    /**
     * 将索引添加到历史记录中
     *
     * @param index 要添加的索引
     */
    private void addToHistory(int index) {
        playHistory.add(index);
        if (playHistory.size() > MAX_HISTORY_SIZE) {
            playHistory.remove(0);
        }
    }

    /**
     * 设置当前索引
     *
     * @param index 新的索引
     * @return 当前歌曲，如果索引无效则返回null
     */
    public Song setCurrentIndex(int index) {
        if (index < 0 || index >= currentPlaylist.size()) {
            return null;
        }

        // 将当前索引添加到历史记录中
        addToHistory(currentIndex);

        // 设置新索引
        currentIndex = index;
        currentIndexLiveData.setValue(currentIndex);

        return getCurrentSong();
    }

    /**
     * 设置播放模式
     *
     * @param mode 新的播放模式
     */
    public void setPlayMode(PlayMode mode) {
        this.playMode = mode;
        playModeLiveData.setValue(mode);
    }

    /**
     * 获取当前播放模式
     *
     * @return 当前播放模式
     */
    public PlayMode getPlayMode() {
        return playMode;
    }

    /**
     * 添加歌曲到播放列表
     *
     * @param song 要添加的歌曲
     */
    public void addSong(Song song) {
        if (song != null) {
            originalPlaylist.add(song);
            currentPlaylist.add(song);
            playlistLiveData.setValue(currentPlaylist);
        }
    }

    /**
     * 添加多首歌曲到播放列表
     *
     * @param songs 要添加的歌曲列表
     */
    public void addSongs(List<Song> songs) {
        if (songs != null && !songs.isEmpty()) {
            originalPlaylist.addAll(songs);
            currentPlaylist.addAll(songs);
            playlistLiveData.setValue(currentPlaylist);
        }
    }

    /**
     * 移除歌曲
     *
     * @param index 要移除的歌曲索引
     */
    public void removeSong(int index) {
        if (index < 0 || index >= currentPlaylist.size()) {
            return;
        }

        Song song = currentPlaylist.get(index);
        originalPlaylist.remove(song);
        currentPlaylist.remove(index);

        // 调整当前索引
        if (index == currentIndex) {
            // 如果移除的是当前播放的歌曲，重新设置当前索引
            currentIndex = currentIndex < currentPlaylist.size() ? currentIndex : currentPlaylist.size() - 1;
        } else if (index < currentIndex) {
            // 如果移除的歌曲在当前播放的歌曲之前，当前索引需要减1
            currentIndex--;
        }

        currentIndexLiveData.setValue(currentIndex);
        playlistLiveData.setValue(currentPlaylist);
    }

    /**
     * 清空播放列表
     */
    public void clearPlaylist() {
        originalPlaylist.clear();
        currentPlaylist.clear();
        currentIndex = -1;
        playHistory.clear();

        playlistLiveData.setValue(currentPlaylist);
        currentIndexLiveData.setValue(currentIndex);
    }

    /**
     * 根据指定排序方式排序播放列表
     *
     * @param order 排序方式
     */
    public void sortPlaylist(SortOrder order) {
        // 根据指定的排序方式创建比较器
        Comparator<Song> comparator = null;

        switch (order) {
            case TITLE_ASC:
                comparator = (s1, s2) -> s1.getTitle().compareToIgnoreCase(s2.getTitle());
                break;
            case TITLE_DESC:
                comparator = (s1, s2) -> s2.getTitle().compareToIgnoreCase(s1.getTitle());
                break;
            case ARTIST_ASC:
                comparator = (s1, s2) -> s1.getArtist().compareToIgnoreCase(s2.getArtist());
                break;
            case ARTIST_DESC:
                comparator = (s1, s2) -> s2.getArtist().compareToIgnoreCase(s1.getArtist());
                break;
            case ALBUM_ASC:
                comparator = (s1, s2) -> s1.getAlbum().compareToIgnoreCase(s2.getAlbum());
                break;
            case ALBUM_DESC:
                comparator = (s1, s2) -> s2.getAlbum().compareToIgnoreCase(s1.getAlbum());
                break;
            case DURATION_ASC:
                comparator = (s1, s2) -> Long.compare(s1.getDuration(), s2.getDuration());
                break;
            case DURATION_DESC:
                comparator = (s1, s2) -> Long.compare(s2.getDuration(), s1.getDuration());
                break;
        }

        if (comparator != null) {
            // 保存当前正在播放的歌曲
            Song currentSong = getCurrentSong();

            // 排序播放列表
            Collections.sort(currentPlaylist, comparator);
            Collections.sort(originalPlaylist, comparator);

            // 更新当前索引
            if (currentSong != null) {
                currentIndex = currentPlaylist.indexOf(currentSong);
            }

            currentIndexLiveData.setValue(currentIndex);
            playlistLiveData.setValue(currentPlaylist);
        }
    }

    /**
     * 根据关键词搜索歌曲
     *
     * @param keyword 搜索关键词
     */
    public void searchSongs(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            // 如果关键词为空，恢复原始播放列表
            resetPlaylist();
            return;
        }

        // 保存当前播放的歌曲
        Song currentSong = getCurrentSong();

        // 过滤播放列表
        keyword = keyword.toLowerCase().trim();
        currentPlaylist.clear();

        for (Song song : originalPlaylist) {
            if (song.getTitle().toLowerCase().contains(keyword) ||
                    song.getArtist().toLowerCase().contains(keyword) ||
                    song.getAlbum().toLowerCase().contains(keyword)) {
                currentPlaylist.add(song);
            }
        }

        // 更新当前索引
        if (currentSong != null) {
            currentIndex = currentPlaylist.indexOf(currentSong);
        } else {
            currentIndex = currentPlaylist.isEmpty() ? -1 : 0;
        }

        currentIndexLiveData.setValue(currentIndex);
        playlistLiveData.setValue(currentPlaylist);
    }

    /**
     * 获取当前播放列表
     *
     * @return 当前播放列表的LiveData
     */
    public LiveData<List<Song>> getPlaylistLiveData() {
        return playlistLiveData;
    }

    /**
     * 获取当前索引的LiveData
     *
     * @return 当前索引的LiveData
     */
    public LiveData<Integer> getCurrentIndexLiveData() {
        return currentIndexLiveData;
    }

    /**
     * 获取播放模式的LiveData
     *
     * @return 播放模式的LiveData
     */
    public LiveData<PlayMode> getPlayModeLiveData() {
        return playModeLiveData;
    }

    /**
     * 获取播放列表大小
     *
     * @return 播放列表大小
     */
    public int getPlaylistSize() {
        return currentPlaylist.size();
    }

    /**
     * 获取当前索引
     *
     * @return 当前索引
     */
    public int getCurrentIndex() {
        return currentIndex;
    }

    /**
     * 获取当前列表中的所有歌曲
     *
     * @return 当前播放列表
     */
    public List<Song> getCurrentPlaylist() {
        return new ArrayList<>(currentPlaylist);
    }
}
