package com.mlinyun.mymusicplayer.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 歌词数据模型
 * 存储完整的歌词数据，包含多个歌词行
 */
public class Lyrics {

    // 关联的歌曲ID
    private String songId;

    // 歌词行列表
    private List<LyricLine> lines;

    // 歌词标题
    private String title;

    // 歌词艺术家
    private String artist;

    // 歌词专辑
    private String album;

    // 歌词元数据，存储LRC文件中的标签信息
    private Map<String, String> metadata;    /**
     * 构造函数
     */
    public Lyrics() {
        this.lines = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    /**
     * 构造函数
     * @param songId 关联的歌曲ID
     */
    public Lyrics(String songId) {
        this.songId = songId;
        this.lines = new ArrayList<>();
        this.metadata = new HashMap<>();
    }

    /**
     * 添加一行歌词
     * @param line 歌词行对象
     */
    public void addLine(LyricLine line) {
        // 确保歌词按时间顺序排序
        if (lines.isEmpty()) {
            lines.add(line);
            return;
        }

        // 找到插入位置
        for (int i = 0; i < lines.size(); i++) {
            if (line.getTimeMs() < lines.get(i).getTimeMs()) {
                lines.add(i, line);
                return;
            }
        }

        // 如果时间大于所有已存在的行，添加到末尾
        lines.add(line);
    }

    /**
     * 根据时间戳查找对应的歌词行
     * @param timeMs 当前播放时间(毫秒)
     * @return 匹配的歌词行，如果没有匹配则返回null
     */
    public LyricLine getLineByTime(long timeMs) {
        if (lines.isEmpty()) {
            return null;
        }

        // 如果时间小于第一行，返回第一行
        if (timeMs < lines.get(0).getTimeMs()) {
            return lines.get(0);
        }

        // 查找当前时间对应的歌词行
        for (int i = 0; i < lines.size() - 1; i++) {
            if (timeMs >= lines.get(i).getTimeMs() && timeMs < lines.get(i + 1).getTimeMs()) {
                return lines.get(i);
            }
        }

        // 如果时间大于最后一行，返回最后一行
        return lines.get(lines.size() - 1);
    }

    /**
     * 根据时间戳查找对应的歌词行索引
     * @param timeMs 当前播放时间(毫秒)
     * @return 匹配的歌词行索引，如果没有匹配则返回-1
     */
    public int getLineIndexByTime(long timeMs) {
        if (lines.isEmpty()) {
            return -1;
        }

        // 如果时间小于第一行，返回第一行索引
        if (timeMs < lines.get(0).getTimeMs()) {
            return 0;
        }

        // 查找当前时间对应的歌词行索引
        for (int i = 0; i < lines.size() - 1; i++) {
            if (timeMs >= lines.get(i).getTimeMs() && timeMs < lines.get(i + 1).getTimeMs()) {
                return i;
            }
        }

        // 如果时间大于最后一行，返回最后一行索引
        return lines.size() - 1;
    }

    /**
     * 更新当前高亮的歌词行
     * @param currentTimeMs 当前播放时间(毫秒)
     */
    public void updateCurrentLine(long currentTimeMs) {
        int currentLineIndex = getLineIndexByTime(currentTimeMs);
        if (currentLineIndex >= 0) {
            for (int i = 0; i < lines.size(); i++) {
                lines.get(i).setCurrentLine(i == currentLineIndex);
            }
        }
    }

    // Getter和Setter方法

    public String getSongId() {
        return songId;
    }

    public void setSongId(String songId) {
        this.songId = songId;
    }

    public List<LyricLine> getLines() {
        return lines;
    }

    public void setLines(List<LyricLine> lines) {
        this.lines = lines;
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

    /**
     * 检查歌词是否为空
     * @return 如果歌词行为空返回true，否则返回false
     */
    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /**
     * 获取歌词行数
     * @return 歌词行数
     */    public int size() {
        return lines.size();
    }

    /**
     * 添加元数据
     * @param key 元数据键
     * @param value 元数据值
     */
    public void addMetadata(String key, String value) {
        metadata.put(key, value);

        // 设置常见元数据到对应字段
        if ("ti".equals(key)) {
            this.title = value;
        } else if ("ar".equals(key)) {
            this.artist = value;
        } else if ("al".equals(key)) {
            this.album = value;
        }
    }

    /**
     * 获取元数据
     * @param key 元数据键
     * @return 元数据值
     */
    public String getMetadata(String key) {
        return metadata.get(key);
    }

    /**
     * 获取所有元数据键
     * @return 元数据键集合
     */
    public Set<String> getMetadataKeys() {
        return metadata.keySet();
    }
}