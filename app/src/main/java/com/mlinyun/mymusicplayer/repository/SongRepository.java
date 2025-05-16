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

                do {                    // 从游标中提取字段
                    String id = cursor.getString(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long duration = cursor.getLong(durationColumn);
                    String path = cursor.getString(pathColumn);

                    // 获取专辑封面URI
                    Uri albumArtUri = null;

                    // 首先尝试从歌曲所在目录查找本地专辑封面
                    albumArtUri = findLocalAlbumArt(path);

                    // 如果本地未找到专辑封面，则使用MediaStore中的专辑封面
                    if (albumArtUri == null && albumIdColumn != -1) {
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
     * 根据歌曲路径查找同文件夹下的专辑封面图片
     * 支持的图片格式: jpg, jpeg, png, webp
     *
     * @param songPath 歌曲文件路径
     * @return 专辑封面URI，如果没有找到则返回null
     */
    private Uri findLocalAlbumArt(String songPath) {
        if (songPath == null || songPath.isEmpty()) {
            return null;
        }

        try {
            // 从歌曲路径中提取文件夹路径和文件名（不带扩展名）
            java.io.File songFile = new java.io.File(songPath);
            String folderPath = songFile.getParent();
            String fileName = songFile.getName();

            // 获取文件名（不带扩展名）
            int lastDotIndex = fileName.lastIndexOf(".");
            if (lastDotIndex > 0) {
                fileName = fileName.substring(0, lastDotIndex);
            }

            Log.d(TAG, "查找本地专辑封面，歌曲路径: " + songPath);
            Log.d(TAG, "查找本地专辑封面，文件夹: " + folderPath);
            Log.d(TAG, "查找本地专辑封面，文件名(无扩展名): " + fileName);

            // 检查同文件夹下是否存在同名的图片文件
            String[] imageExtensions = {".jpg", ".jpeg", ".png", ".webp", ".JPG", ".JPEG", ".PNG", ".WEBP"};
            for (String ext : imageExtensions) {
                java.io.File imageFile = new java.io.File(folderPath, fileName + ext);
                if (imageFile.exists() && imageFile.isFile()) {
                    Log.d(TAG, "找到本地专辑封面: " + imageFile.getAbsolutePath());
                    return Uri.fromFile(imageFile);
                }
            }
            // 检查是否存在通用的封面图片文件
            String[] commonCoverNames = {"cover", "folder", "album", "front", "artwork", "Cover", "Folder", "Album", "Front", "Artwork", "COVER", "FOLDER", "ALBUM"};
            for (String name : commonCoverNames) {
                for (String ext : imageExtensions) {
                    java.io.File imageFile = new java.io.File(folderPath, name + ext);
                    if (imageFile.exists() && imageFile.isFile()) {
                        Log.d(TAG, "找到通用文件夹封面: " + imageFile.getAbsolutePath());
                        return Uri.fromFile(imageFile);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "查找本地专辑封面时出错", e);
        }

        return null;
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

    /**
     * 根据关键词从本地媒体库搜索歌曲
     * 与searchSongs方法不同，此方法直接从媒体存储中查询，确保能找到最新添加的歌曲
     *
     * @param keyword 搜索关键词
     * @return 匹配的歌曲列表
     */
    public List<Song> searchLocalSongs(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return new ArrayList<>();
        }

        List<Song> results = new ArrayList<>();
        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

        // 只选择音乐文件（非铃声等）
        String lowerKeyword = "%" + keyword.toLowerCase() + "%";
        String selection = MediaStore.Audio.Media.IS_MUSIC + "!= 0 AND (" +
                "LOWER(" + MediaStore.Audio.Media.TITLE + ") LIKE ? OR " +
                "LOWER(" + MediaStore.Audio.Media.ARTIST + ") LIKE ? OR " +
                "LOWER(" + MediaStore.Audio.Media.ALBUM + ") LIKE ?)";
        String[] selectionArgs = {lowerKeyword, lowerKeyword, lowerKeyword};

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

        try (Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID);
                int titleColumn = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
                int artistColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
                int albumColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM);
                int durationColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
                int pathColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DATA);
                int albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);

                do {                    // 从游标中提取字段
                    String id = cursor.getString(idColumn);
                    String title = cursor.getString(titleColumn);
                    String artist = cursor.getString(artistColumn);
                    String album = cursor.getString(albumColumn);
                    long duration = cursor.getLong(durationColumn);
                    String path = cursor.getString(pathColumn);

                    // 获取专辑封面URI
                    Uri albumArtUri = null;

                    // 首先尝试从歌曲所在目录查找本地专辑封面
                    albumArtUri = findLocalAlbumArt(path);

                    // 如果本地未找到专辑封面，则使用MediaStore中的专辑封面
                    if (albumArtUri == null && albumIdColumn != -1) {
                        long albumId = cursor.getLong(albumIdColumn);
                        albumArtUri = getAlbumArtUri(albumId);
                    }

                    // 创建带搜索标记的歌曲对象
                    Song song = new Song(id, title, artist, album, duration, path, albumArtUri);
                    song.setSearchResult(true);

                    // 检查该歌曲是否已在缓存中存在（表示已在播放列表中）
                    // 如果在缓存中不存在，则视为本地搜索结果
                    if (!songCache.containsKey(id)) {
                        results.add(song);
                    }

                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e(TAG, "本地搜索歌曲时出错", e);
        }

        return results;
    }

    /**
     * 处理本地专辑封面的加载失败情况
     * 为可能遇到的问题提供解决方法
     *
     * @param song 需要重试加载封面的歌曲
     * @return 处理后的URI，如果无法修复则返回null
     */
    public Uri handleAlbumArtLoadError(Song song) {
        if (song == null || song.getPath() == null) {
            return null;
        }

        try {
            // 尝试重新从本地文件系统加载专辑封面
            Uri albumArtUri = findLocalAlbumArt(song.getPath());
            if (albumArtUri != null) {
                // 更新歌曲对象的专辑封面URI
                song.setAlbumArtUri(albumArtUri);
                return albumArtUri;
            }

            // 如果本地文件不存在，尝试从媒体库获取
            ContentResolver contentResolver = context.getContentResolver();
            Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String selection = MediaStore.Audio.Media.DATA + "=?";
            String[] selectionArgs = {song.getPath()};
            String[] projection = {MediaStore.Audio.Media.ALBUM_ID};

            try (Cursor cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int albumIdColumn = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
                    if (albumIdColumn != -1) {
                        long albumId = cursor.getLong(albumIdColumn);
                        Uri mediaStoreUri = getAlbumArtUri(albumId);
                        song.setAlbumArtUri(mediaStoreUri);
                        return mediaStoreUri;
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "处理专辑封面加载错误时出现异常", e);
        }

        return null;
    }
}
