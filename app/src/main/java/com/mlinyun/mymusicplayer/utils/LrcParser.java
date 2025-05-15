package com.mlinyun.mymusicplayer.utils;

import android.util.Log;

import com.mlinyun.mymusicplayer.model.LyricLine;
import com.mlinyun.mymusicplayer.model.Lyrics;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LRC歌词解析工具类
 * 用于解析标准LRC格式的歌词文件
 */
public class LrcParser {
    private static final String TAG = "LrcParser";

    // LRC时间标签正则表达式 格式: [mm:ss.xx] 例如: [00:12.34]
    private static final Pattern TIME_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\]");

    // LRC元数据标签正则表达式 例如: [ar:艺术家]
    private static final Pattern METADATA_PATTERN = Pattern.compile("\\[(\\w+):(.+)\\]");

    /**
     * 从文件解析LRC歌词
     *
     * @param lrcFile LRC歌词文件
     * @return 解析后的Lyrics对象
     */
    public static Lyrics parse(File lrcFile) {
        if (lrcFile == null || !lrcFile.exists()) {
            Log.e(TAG, "LRC文件不存在");
            return new Lyrics();
        }

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(lrcFile), "UTF-8"))) {
            return parse(reader);
        } catch (IOException e) {
            Log.e(TAG, "读取LRC文件出错", e);
            return new Lyrics();
        }
    }

    /**
     * 从字符串内容解析LRC歌词
     *
     * @param content LRC歌词文本内容
     * @return 解析后的Lyrics对象
     */
    public static Lyrics parse(String content) {
        if (content == null || content.trim().isEmpty()) {
            Log.e(TAG, "LRC内容为空");
            return new Lyrics();
        }

        try {
            return parseContent(content);
        } catch (Exception e) {
            Log.e(TAG, "解析LRC内容出错", e);
            return new Lyrics();
        }
    }

    /**
     * 从BufferedReader解析LRC歌词
     *
     * @param reader BufferedReader对象
     * @return 解析后的Lyrics对象
     */
    public static Lyrics parse(BufferedReader reader) {
        if (reader == null) {
            return new Lyrics();
        }

        try {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\\n");
            }
            return parseContent(content.toString());
        } catch (Exception e) {
            Log.e(TAG, "解析LRC出错", e);
            return new Lyrics();
        }
    }

    /**
     * 解析LRC内容
     *
     * @param content LRC歌词内容
     * @return 解析后的Lyrics对象
     */
    private static Lyrics parseContent(String content) {
        Lyrics lyrics = new Lyrics();
        List<LyricLine> lines = new ArrayList<>();

        // 将内容按行分割
        String[] rawLines = content.split("\\\\n|\\n");

        for (String rawLine : rawLines) {
            // 处理元数据标签
            Matcher metadataMatcher = METADATA_PATTERN.matcher(rawLine);
            while (metadataMatcher.find()) {
                String key = metadataMatcher.group(1);
                String value = metadataMatcher.group(2);
                if (key != null && value != null) {
                    lyrics.addMetadata(key, value);
                }
            }

            // 处理带时间标签的歌词行
            List<Long> times = new ArrayList<>();
            String text = rawLine;

            Matcher timeMatcher = TIME_PATTERN.matcher(rawLine);
            while (timeMatcher.find()) {
                // 提取时间标签
                int minute = Integer.parseInt(timeMatcher.group(1));
                int second = Integer.parseInt(timeMatcher.group(2));
                int millisecond;
                String msStr = timeMatcher.group(3);
                if (msStr.length() == 2) {
                    millisecond = Integer.parseInt(msStr) * 10;
                } else {
                    millisecond = Integer.parseInt(msStr);
                }

                long timeMs = minute * 60 * 1000 + second * 1000 + millisecond;
                times.add(timeMs);

                // 从文本中移除时间标签
                text = text.replace(timeMatcher.group(0), "");
            }

            // 如果有时间标签，创建歌词行
            if (!times.isEmpty()) {
                text = text.trim();
                for (long time : times) {
                    lines.add(new LyricLine(time, text));
                }
            }
        }

        // 按时间排序
        Collections.sort(lines, Comparator.comparingLong(LyricLine::getTimeMs));

        // 添加排序后的歌词行到Lyrics对象
        for (LyricLine line : lines) {
            lyrics.addLine(line);
        }

        return lyrics;
    }

    /**
     * 解析时间标签字符串为毫秒
     * 格式: mm:ss.xx 例如: 01:23.45
     *
     * @param timeStr 时间字符串
     * @return 对应的毫秒数
     */
    public static long parseTimeToMs(String timeStr) {
        try {
            Matcher matcher = TIME_PATTERN.matcher("[" + timeStr + "]");
            if (matcher.find()) {
                int minute = Integer.parseInt(matcher.group(1));
                int second = Integer.parseInt(matcher.group(2));
                int millisecond;
                String msStr = matcher.group(3);
                if (msStr.length() == 2) {
                    millisecond = Integer.parseInt(msStr) * 10;
                } else {
                    millisecond = Integer.parseInt(msStr);
                }

                return minute * 60 * 1000 + second * 1000 + millisecond;
            }
        } catch (Exception e) {
            Log.e(TAG, "解析时间标签出错: " + timeStr, e);
        }
        return 0;
    }

    /**
     * 将毫秒时间转换为LRC格式的时间标签
     *
     * @param timeMs 毫秒时间
     * @return LRC格式的时间标签字符串，例如: [01:23.45]
     */
    public static String formatTimeTag(long timeMs) {
        int totalSeconds = (int) (timeMs / 1000);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        int milliseconds = (int) (timeMs % 1000);

        return String.format("[%02d:%02d.%03d]", minutes, seconds, milliseconds);
    }

    /**
     * 生成LRC格式歌词内容
     *
     * @param lyrics 歌词对象
     * @return LRC格式的歌词文本
     */
    public static String generateLrcContent(Lyrics lyrics) {
        if (lyrics == null || lyrics.isEmpty()) {
            return "";
        }

        StringBuilder builder = new StringBuilder();

        // 添加元数据
        for (String key : lyrics.getMetadataKeys()) {
            builder.append("[").append(key).append(":").append(lyrics.getMetadata(key)).append("]\n");
        }
        builder.append("\n");

        // 添加歌词行
        for (LyricLine line : lyrics.getLines()) {
            builder.append(formatTimeTag(line.getTimeMs())).append(line.getText()).append("\n");
        }

        return builder.toString();
    }
}
