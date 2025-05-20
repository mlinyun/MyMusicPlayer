package com.mlinyun.mymusicplayer.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.IOException;

/**
 * MediaPlayer实现的播放引擎
 * 基于Android原生MediaPlayer实现IPlayerEngine接口
 */
public class MediaPlayerImpl implements IPlayerEngine {
    private static final String TAG = "MediaPlayerImpl";

    private MediaPlayer mediaPlayer;
    private Context context;
    private Uri currentUri;

    // 回调接口
    private OnCompletionListener onCompletionListener;
    private OnErrorListener onErrorListener;
    private OnPreparedListener onPreparedListener;

    /**
     * 构造函数
     *
     * @param context 应用上下文
     */
    public MediaPlayerImpl(Context context) {
        this.context = context;
    }

    @Override
    public void initialize() {
        // 安全地释放任何现有的MediaPlayer实例
        releaseMediaPlayer();

        try {
            // 创建新的MediaPlayer实例
            mediaPlayer = new MediaPlayer();

            // 设置音频属性
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mediaPlayer.setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .build()
                );
            } else {
                mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
            }

            // 设置监听器
            mediaPlayer.setOnCompletionListener(mp -> {
                if (onCompletionListener != null) {
                    onCompletionListener.onCompletion();
                }
            });

            mediaPlayer.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
                if (onErrorListener != null) {
                    onErrorListener.onError(what, extra);
                    return true; // 错误已处理
                }
                return false; // 错误未处理
            });
            mediaPlayer.setOnPreparedListener(mp -> {
                // 在准备完成时获取并记录总时长（调试用）
                try {
                    int duration = mp.getDuration();
                    Log.d(TAG, "MediaPlayer准备完成，总时长: " + duration + "ms");
                } catch (Exception e) {
                    Log.e(TAG, "获取媒体时长时出错", e);
                }

                if (onPreparedListener != null) {
                    onPreparedListener.onPrepared();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "Error initializing MediaPlayer", e);
            mediaPlayer = null;
            if (onErrorListener != null) {
                onErrorListener.onError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            }
        }
    }

    @Override
    public void prepare(Uri uri) {
        // 检查URI是否有效
        if (uri == null) {
            Log.e(TAG, "Cannot prepare with null URI");
            if (onErrorListener != null) {
                onErrorListener.onError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            }
            return;
        }

        // 确保MediaPlayer实例有效
        if (mediaPlayer == null) {
            Log.d(TAG, "MediaPlayer was null, initializing");
            initialize();

            // 再次检查初始化是否成功
            if (mediaPlayer == null) {
                Log.e(TAG, "Failed to initialize MediaPlayer");
                if (onErrorListener != null) {
                    onErrorListener.onError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
                }
                return;
            }
        }

        // 记录当前URI
        currentUri = uri;

        try {
            // 检查文件是否可读
            if (uri.getScheme() != null && uri.getScheme().equals("file")) {
                java.io.File file = new java.io.File(uri.getPath());
                if (!file.exists() || !file.canRead()) {
                    Log.e(TAG, "File doesn't exist or can't be read: " + uri.getPath());
                    if (onErrorListener != null) {
                        onErrorListener.onError(MediaPlayer.MEDIA_ERROR_IO, 0);
                    }
                    return;
                }
            }

            // 安全重置播放器
            try {
                mediaPlayer.reset();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error resetting player, reinitializing", e);
                initialize();
                if (mediaPlayer == null) return;
            }

            // 设置数据源并准备播放
            mediaPlayer.setDataSource(context, uri);

            // 使用异步准备，防止阻塞UI线程
            mediaPlayer.prepareAsync();

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception preparing media player", e);
            handlePrepareError(e);
        } catch (IOException e) {
            Log.e(TAG, "IO error preparing media player", e);
            handlePrepareError(e);
        } catch (IllegalStateException e) {
            Log.e(TAG, "MediaPlayer in illegal state", e);
            handlePrepareError(e);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Invalid argument for MediaPlayer", e);
            handlePrepareError(e);
        } catch (Exception e) {
            // 捕获所有其他可能的异常
            Log.e(TAG, "Unexpected error preparing media player", e);
            handlePrepareError(e);
        }
    }

    /**
     * 处理准备过程中的错误
     */
    private void handlePrepareError(Exception e) {
        // 记录详细错误
        Log.e(TAG, "Media prepare error: " + e.getMessage(), e);

        // 尝试释放并重新初始化播放器
        try {
            releaseMediaPlayer();
            initialize();

            // 通知错误回调
            if (onErrorListener != null) {
                int errorType = (e instanceof IOException) ?
                        MediaPlayer.MEDIA_ERROR_IO : MediaPlayer.MEDIA_ERROR_UNKNOWN;
                onErrorListener.onError(errorType, 0);
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error handling prepare failure", ex);
        }
    }

    @Override
    public void play() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error starting playback", e);
                if (currentUri != null) {
                    // 如果播放失败，尝试重新准备并播放
                    prepare(currentUri);
                }
            }
        }
    }

    @Override
    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error pausing playback", e);
            }
        }
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            try {
                // 检查是否正在播放
                if (mediaPlayer.isPlaying()) {
                    mediaPlayer.stop();
                } else {
                    // 如果不是播放状态，尝试重置
                    mediaPlayer.reset();
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping playback", e);
                // 出错时尝试重置
                try {
                    mediaPlayer.reset();
                } catch (Exception ignored) {
                    // 忽略重置错误
                }
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error stopping MediaPlayer", e);
            }
        }
    }

    @Override
    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mediaPlayer.seekTo(position, MediaPlayer.SEEK_CLOSEST);
                } else {
                    mediaPlayer.seekTo(position);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error seeking to position", e);
            }
        }
    }

    @Override
    public void release() {
        releaseMediaPlayer();
    }

    /**
     * 释放MediaPlayer资源
     */
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                // 取消所有回调
                mediaPlayer.setOnCompletionListener(null);
                mediaPlayer.setOnErrorListener(null);
                mediaPlayer.setOnPreparedListener(null);

                // 检查播放状态并安全停止
                try {
                    if (mediaPlayer.isPlaying()) {
                        mediaPlayer.stop();
                    }
                } catch (IllegalStateException e) {
                    Log.w(TAG, "MediaPlayer was in an invalid state when trying to stop", e);
                }

                // 释放资源
                mediaPlayer.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing media player", e);
            } finally {
                mediaPlayer = null;
                currentUri = null;
            }
        }
    }

    @Override
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error getting current position", e);
                return 0;
            }
        }
        return 0;
    }

    @Override
    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                // 确保MediaPlayer处于合法状态
                if (mediaPlayer.isPlaying() ||
                        currentUri != null) { // 只有在正在播放或已设置URI时才获取时长
                    return mediaPlayer.getDuration();
                } else {
                    return 0;
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error getting duration: MediaPlayer in illegal state", e);
                return 0;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error getting duration", e);
                return 0;
            }
        }
        return 0;
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(leftVolume, rightVolume);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error setting volume", e);
            }
        }
    }

    @Override
    public boolean isPlaying() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.isPlaying();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error checking playback state", e);
                return false;
            } catch (Exception e) {
                Log.e(TAG, "Unexpected error checking playback state", e);
                // 必须捕获所有可能的异常，避免应用崩溃
                return false;
            }
        }
        return false;
    }

    @Override
    public void setOnCompletionListener(OnCompletionListener listener) {
        this.onCompletionListener = listener;
    }

    @Override
    public void setOnErrorListener(OnErrorListener listener) {
        this.onErrorListener = listener;
    }

    @Override
    public void setOnPreparedListener(OnPreparedListener listener) {
        this.onPreparedListener = listener;
    }
}