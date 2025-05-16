package com.mlinyun.mymusicplayer.model;

import android.net.Uri;

/**
 * 歌曲数据模型
 * 用于存储和管理音乐文件的元数据信息
 */
public class Song {

    // 歌曲唯一标识符
    private String id;

    // 歌曲标题
    private String title;

    // 歌曲艺术家
    private String artist;

    // 专辑名称
    private String album;

    // 歌曲时长(毫秒)
    private long duration;

    // 歌曲文件路径
    private String path;

    // 专辑封面图片Uri
    private Uri albumArtUri;

    // 标记是否为搜索结果，区分已加入播放列表和搜索结果
    private boolean isSearchResult = false;

    /**
     * 构造函数
     *
     * @param id       歌曲ID
     * @param title    歌曲标题
     * @param artist   艺术家
     * @param album    专辑
     * @param duration 时长
     * @param path     文件路径
     */
    public Song(String id, String title, String artist, String album, long duration, String path) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.album = album;
        this.duration = duration;
        this.path = path;
    }

    /**
     * 构造函数(包含专辑封面)
     */
    public Song(String id, String title, String artist, String album, long duration, String path, Uri albumArtUri) {
        this(id, title, artist, album, duration, path);
        this.albumArtUri = albumArtUri;
    }

    // Getter和Setter方法

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Uri getAlbumArtUri() {
        return albumArtUri;
    }

    public void setAlbumArtUri(Uri albumArtUri) {
        this.albumArtUri = albumArtUri;
    }

    public boolean isSearchResult() {
        return isSearchResult;
    }

    public void setSearchResult(boolean searchResult) {
        isSearchResult = searchResult;
    }

    /**
     * 获取格式化的时间字符串(分:秒)
     *
     * @return 格式化的时间字符串
     */
    public String getFormattedDuration() {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 判断专辑封面是否是从本地文件加载的
     *
     * @return 如果是本地文件则返回true，否则返回false
     */
    public boolean isLocalAlbumArt() {
        if (albumArtUri == null) {
            return false;
        }

        String scheme = albumArtUri.getScheme();
        return "file".equals(scheme);
    }

    /**
     * 获取专辑封面的文件路径（仅当是本地文件时有效）
     *
     * @return 专辑封面文件路径，如果不是本地文件则返回null
     */
    public String getAlbumArtPath() {
        if (isLocalAlbumArt()) {
            return albumArtUri.getPath();
        }
        return null;
    }

    @Override
    public String toString() {
        return "Song{" +
                "title='" + title + '\'' +
                ", artist='" + artist + '\'' +
                '}';
    }
}