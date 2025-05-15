package com.mlinyun.mymusicplayer.repository;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import com.mlinyun.mymusicplayer.model.Song;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 歌曲仓库类
 * 负责从本地媒体库加载歌曲数据，提供缓存和查询功能
 */
public class SongRepository {
    private static final String TAG = "SongRepository";

    // 上下文
    private final Context context;

    // 歌曲缓存，使用Map通过ID快速访问
    private final Map<String, Song> songCache = new HashMap<>();

    // 缓存是否已初始化
    private boolean isCacheInitialized = false;

    /**
     * 构造函数
     *
     * @param context 应用上下文
     */
    public SongRepository(Context context) {
        this.context = context;
    }

    /**
     * 获取所有本地歌曲
     *
     * @return 歌曲列表
     */
    public List<Song> getAllSongs() {
        if (!isCacheInitialized) {
            loadSongsFromMediaStore();
        }
        return new ArrayList<>(songCache.values());
    }

    /**
     * 通过ID获取歌曲
     *
     * @param id 歌曲ID
     * @return 对应的歌曲对象，如果不存在返回null
     */
    public Song getSongById(String id) {
        if (!isCacheInitialized) {
            loadSongsFromMediaStore();
        }
        return songCache.get(id);
    }

    /**
     * 重新扫描媒体库并刷新缓存
     *
     * @return 更新后的歌曲列表
     */
    public List<Song> refreshMediaStore() {
        songCache.clear();
        isCacheInitialized = false;
        return getAllSongs();
    }

    /**
     * 从媒体库加载歌曲数据
     */
    private void loadSongsFromMediaStore() {
        Log.d(TAG, "开始从媒体库加载歌曲");

        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        // 只选择音乐文件（非铃声等）
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0";

        // 按标题排序
        String sortOrder = MediaStore.Audio.Media.TITLE + " ASC";

        // 要获取的列
        String[] projection = {
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM_ID
        };

        try (Cursor cursor = contentResolver.query(uri, projection, selection, null, sortOrder)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);

                do {
                    // 从游标中提取字段
                    String id = cursor.getString(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long duration = cursor.getLong(durationColumn);
                    String path = cursor.getString(pathColumn);

                    // 获取专辑封面URI
                    Uri albumArtUri = null;
                    if (albumIdColumn != -1) {
                        long albumId = cursor.getLong(albumIdColumn);
                        albumArtUri = getAlbumArtUri(albumId);
                    }

                    // 创建歌曲对象并添加到缓存中
                    Song song = new Song(id, title, artist, album, duration, path, albumArtUri);
                    songCache.put(id, song);

                } while (cursor.moveToNext());

                Log.d(TAG, "从媒体库加载了 " + songCache.size() + " 首歌曲");
            } else {
                Log.d(TAG, "媒体库中没有找到歌曲");
            }
        } catch (Exception e) {
            Log.e(TAG, "加载媒体库歌曲时出错", e);
        }

        isCacheInitialized = true;
    }

    /**
     * 根据专辑ID获取专辑封面URI
     *
     * @param albumId 专辑ID
     * @return 专辑封面URI
     */
    private Uri getAlbumArtUri(long albumId) {
        return ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
        );
    }

    /**
     * 根据关键词搜索歌曲
     *
     * @param keyword 搜索关键词
     * @return 匹配的歌曲列表
     */
    public List<Song> searchSongs(String keyword) {
        if (!isCacheInitialized) {
            loadSongsFromMediaStore();
        }

        if (keyword == null || keyword.trim().isEmpty()) {
            return getAllSongs();
        }

        List<Song> result = new ArrayList<>();
        String lowerKeyword = keyword.toLowerCase().trim();

        for (Song song : songCache.values()) {
            if (song.getTitle().toLowerCase().contains(lowerKeyword) ||
                    song.getArtist().toLowerCase().contains(lowerKeyword) ||
                    song.getAlbum().toLowerCase().contains(lowerKeyword)) {
                result.add(song);
            }
        }

        return result;
    }

    /**
     * 根据艺术家名称查找歌曲
     *
     * @param artist 艺术家名称
     * @return 匹配的歌曲列表
     */
    public List<Song> getSongsByArtist(String artist) {
        if (!isCacheInitialized) {
            loadSongsFromMediaStore();
        }

        List<Song> result = new ArrayList<>();

        for (Song song : songCache.values()) {
            if (song.getArtist().equals(artist)) {
                result.add(song);
            }
        }

        return result;
    }

    /**
     * 根据专辑名称查找歌曲
     *
     * @param album 专辑名称
     * @return 匹配的歌曲列表
     */
    public List<Song> getSongsByAlbum(String album) {
        if (!isCacheInitialized) {
            loadSongsFromMediaStore();
        }

        List<Song> result = new ArrayList<>();

        for (Song song : songCache.values()) {
            if (song.getAlbum().equals(album)) {
                result.add(song);
            }
        }

        return result;
    }

    /**
     * 检查歌曲是否存在
     *
     * @param path 歌曲路径
     * @return 是否存在
     */
    public boolean isSongExists(String path) {
        if (!isCacheInitialized) {
            loadSongsFromMediaStore();
        }

        for (Song song : songCache.values()) {
            if (song.getPath().equals(path)) {
                return true;
            }
        }

        return false;
    }

    /**
     * 扫描媒体库获取所有歌曲
     * 这会强制重新加载所有音乐文件
     *
     * @return 歌曲列表
     */
    public List<Song> scanMediaStore() {
        // 清空缓存
        songCache.clear();
        isCacheInitialized = false;

        // 重新加载
        loadSongsFromMediaStore();

        // 返回新的列表
        return new ArrayList<>(songCache.values());
    }

    /**
     * 获取缓存的歌曲列表
     *
     * @return 歌曲列表，如果缓存未初始化则先加载
     */
    public List<Song> getCachedSongs() {
        if (!isCacheInitialized) {
            loadSongsFromMediaStore();
        }
        return new ArrayList<>(songCache.values());
    }
}
