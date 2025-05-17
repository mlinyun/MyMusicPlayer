package com.mlinyun.mymusicplayer.model;

/**
 * 歌词行数据模型
 * 存储单行歌词及其对应的时间戳信息
 */
public class LyricLine {
    // 时间戳(毫秒)
    private long timeMs;

    // 歌词文本内容
    private String text;

    // 是否是当前正在播放的歌词行
    private boolean isCurrentLine;

    /**
     * 构造函数
     *
     * @param timeMs 时间戳(毫秒)
     * @param text   歌词文本
     */
    public LyricLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text;
        this.isCurrentLine = false;
    }

    // Getter和Setter方法

    public long getTimeMs() {
        return timeMs;
    }

    public void setTimeMs(long timeMs) {
        this.timeMs = timeMs;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public boolean isCurrentLine() {
        return isCurrentLine;
    }

    public void setCurrentLine(boolean currentLine) {
        isCurrentLine = currentLine;
    }

    /**
     * 获取格式化的时间字符串(分:秒.毫秒)
     *
     * @return 格式化的时间字符串
     */
    public String getFormattedTime() {
        long totalSeconds = timeMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = timeMs % 1000;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }

    /**
     * 获取LRC格式的时间标签
     *
     * @return [MM:SS.ms]格式的时间标签
     */
    public String getTimeTag() {
        long totalSeconds = timeMs / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        long millis = (timeMs % 1000) / 10;
        // LRC格式通常只精确到百分之一秒
        return String.format("[%02d:%02d.%02d]", minutes, seconds, millis);
    }

    @Override
    public String toString() {
        return getTimeTag() + text;
    }
}
