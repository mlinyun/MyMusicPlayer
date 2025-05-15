package com.mlinyun.mymusicplayer.repository;

import android.content.Context;
import android.util.Log;
import android.util.LruCache;

import com.mlinyun.mymusicplayer.model.Lyrics;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.utils.LrcParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 歌词仓库类
 * 负责加载、缓存、保存歌词数据
 */
public class LyricsRepository {
    private static final String TAG = "LyricsRepository";

    // 缓存相关
    private static final int CACHE_SIZE = 20; // 最多缓存20个歌词对象
    private final LruCache<String, Lyrics> lyricsCache;

    // 上下文
    private final Context context;

    // 线程池，用于异步加载歌词
    private final Executor executor = Executors.newSingleThreadExecutor();

    /**
     * 构造函数
     *
     * @param context 上下文
     */
    public LyricsRepository(Context context) {
        this.context = context.getApplicationContext();

        // 初始化缓存
        lyricsCache = new LruCache<>(CACHE_SIZE);
    }

    /**
     * 根据歌曲对象获取歌词
     * 优先从缓存获取，如果缓存没有则从本地文件获取
     *
     * @param song     歌曲对象
     * @param callback 加载完成后的回调
     */
    public void getLyricsBySong(Song song, LyricsCallback callback) {
        if (song == null) {
            if (callback != null) {
                callback.onLyricsLoaded(new Lyrics());
            }
            return;
        }

        final String songId = song.getId();

        // 先从缓存中尝试获取
        Lyrics cachedLyrics = lyricsCache.get(songId);
        if (cachedLyrics != null) {
            Log.d(TAG, "从缓存中获取歌词: " + song.getTitle());
            if (callback != null) {
                callback.onLyricsLoaded(cachedLyrics);
            }
            return;
        }

        // 缓存中没有，尝试从文件加载
        executor.execute(() -> {
            Lyrics lyrics = loadLyricsFromFile(song);

            // 将加载的歌词添加到缓存
            if (lyrics != null && !lyrics.isEmpty()) {
                lyricsCache.put(songId, lyrics);
            } else {
                // 如果没有找到歌词，使用空歌词对象
                lyrics = new Lyrics();
            }

            // 通过回调返回结果
            final Lyrics finalLyrics = lyrics;
            if (callback != null) {
                callback.onLyricsLoaded(finalLyrics);
            }
        });
    }

    /**
     * 从文件中加载歌词
     * 查找策略：
     * 1. 与音频文件同名的.lrc文件
     * 2. 歌曲目录下的artist - title.lrc
     * 3. 应用的歌词目录下的[id].lrc
     *
     * @param song 歌曲对象
     * @return 加载的歌词对象，如果未找到返回null
     */
    private Lyrics loadLyricsFromFile(Song song) {
        if (song == null || song.getPath() == null) {
            return null;
        }

        // 1. 检查与音频文件同名的.lrc文件
        String audioPath = song.getPath();
        String lrcPath1 = audioPath.substring(0, audioPath.lastIndexOf('.')) + ".lrc";
        File lrcFile1 = new File(lrcPath1);
        if (lrcFile1.exists() && lrcFile1.isFile() && lrcFile1.canRead()) {
            Log.d(TAG, "在音频文件旁找到歌词: " + lrcFile1.getPath());
            return LrcParser.parse(lrcFile1);
        }

        // 2. 检查歌曲目录下的artist - title.lrc
        File audioFile = new File(audioPath);
        File parentDir = audioFile.getParentFile();
        if (parentDir != null && parentDir.exists() && parentDir.isDirectory()) {
            String artistTitle = song.getArtist() + " - " + song.getTitle() + ".lrc";
            File lrcFile2 = new File(parentDir, artistTitle);
            if (lrcFile2.exists() && lrcFile2.isFile() && lrcFile2.canRead()) {
                Log.d(TAG, "在音频目录找到歌词: " + lrcFile2.getPath());
                return LrcParser.parse(lrcFile2);
            }
        }

        // 3. 检查应用歌词目录
        File lyricsDir = new File(context.getFilesDir(), "lyrics");
        if (!lyricsDir.exists()) {
            lyricsDir.mkdirs();
        }

        File lrcFile3 = new File(lyricsDir, song.getId() + ".lrc");
        if (lrcFile3.exists() && lrcFile3.isFile() && lrcFile3.canRead()) {
            Log.d(TAG, "在应用歌词目录找到歌词: " + lrcFile3.getPath());
            return LrcParser.parse(lrcFile3);
        }

        Log.d(TAG, "未找到歌词文件: " + song.getTitle());
        return null;
    }

    /**
     * 保存歌词到文件
     *
     * @param song   歌曲对象
     * @param lyrics 歌词对象
     * @return 是否保存成功
     */
    public boolean saveLyrics(Song song, Lyrics lyrics) {
        if (song == null || lyrics == null || lyrics.isEmpty()) {
            return false;
        }

        // 将歌词添加到缓存
        lyricsCache.put(song.getId(), lyrics);

        // 创建歌词目录
        File lyricsDir = new File(context.getFilesDir(), "lyrics");
        if (!lyricsDir.exists()) {
            lyricsDir.mkdirs();
        }

        // 创建歌词文件
        File lrcFile = new File(lyricsDir, song.getId() + ".lrc");

        try {
            // 生成LRC文件内容
            String lrcContent = LrcParser.generateLrcContent(lyrics);

            // 写入文件
            try (FileOutputStream fos = new FileOutputStream(lrcFile)) {
                fos.write(lrcContent.getBytes("UTF-8"));
                fos.flush();
            }

            Log.d(TAG, "歌词保存成功: " + lrcFile.getPath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "保存歌词失败", e);
            return false;
        }
    }

    /**
     * 清除歌词缓存
     */
    public void clearCache() {
        lyricsCache.evictAll();
    }

    /**
     * 删除歌词文件
     *
     * @param song 歌曲对象
     * @return 是否删除成功
     */
    public boolean deleteLyrics(Song song) {
        if (song == null) {
            return false;
        }

        // 从缓存中移除
        lyricsCache.remove(song.getId());

        // 删除文件
        File lyricsDir = new File(context.getFilesDir(), "lyrics");
        File lrcFile = new File(lyricsDir, song.getId() + ".lrc");

        if (lrcFile.exists()) {
            boolean deleted = lrcFile.delete();
            Log.d(TAG, "删除歌词文件: " + deleted);
            return deleted;
        }

        return false;
    }

    /**
     * 从assets导入示例歌词
     *
     * @param assetFileName assets中的文件名
     * @param song          歌曲对象
     * @return 是否导入成功
     */
    public boolean importLyricsFromAssets(String assetFileName, Song song) {
        if (assetFileName == null || song == null) {
            return false;
        }

        try {
            // 打开assets中的文件
            InputStream inputStream = context.getAssets().open(assetFileName);

            // 创建歌词目录
            File lyricsDir = new File(context.getFilesDir(), "lyrics");
            if (!lyricsDir.exists()) {
                lyricsDir.mkdirs();
            }

            // 创建输出文件
            File outputFile = new File(lyricsDir, song.getId() + ".lrc");

            // 复制文件
            try (OutputStream outputStream = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }
            }

            inputStream.close();

            // 解析并缓存歌词
            Lyrics lyrics = LrcParser.parse(outputFile);
            if (lyrics != null && !lyrics.isEmpty()) {
                lyricsCache.put(song.getId(), lyrics);
            }

            Log.d(TAG, "从assets导入歌词成功: " + outputFile.getPath());
            return true;
        } catch (IOException e) {
            Log.e(TAG, "从assets导入歌词失败", e);
            return false;
        }
    }

    /**
     * 歌词加载回调接口
     */
    public interface LyricsCallback {
        void onLyricsLoaded(Lyrics lyrics);
    }
}
