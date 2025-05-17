package com.mlinyun.mymusicplayer.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;

/**
 * ExoPlayer实现的播放引擎
 * 基于Media3 ExoPlayer实现IPlayerEngine接口
 * 更适合Android 16 (API 35)及以上版本
 */
public class ExoPlayerImpl implements IPlayerEngine {
    private static final String TAG = "ExoPlayerImpl";

    private ExoPlayer exoPlayer;
    private final Context context;
    private Uri currentUri;

    // 回调接口
    private OnCompletionListener onCompletionListener;
    private OnErrorListener onErrorListener;
    private OnPreparedListener onPreparedListener;

    // 用于延迟执行的Handler
    private final Handler handler;

    // 播放状态追踪
    private boolean isPreparing = false;
    private boolean needPlayWhenReady = false;

    /**
     * 构造函数
     *
     * @param context 应用上下文
     */
    public ExoPlayerImpl(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void initialize() {
        // 释放现有播放器实例
        releasePlayer();

        try {
            // 创建ExoPlayer实例
            exoPlayer = new ExoPlayer.Builder(context)
                    .setHandleAudioBecomingNoisy(true)  // 处理音频变得嘈杂的情况（如拔出耳机）
                    .build();            // 设置监听器
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int playbackState) {
                    Log.d(TAG, "ExoPlayer状态变更: " + playbackStateToString(playbackState));

                    // 更新状态标志
                    if (playbackState == Player.STATE_READY) {
                        isPreparing = false;
                        Log.d(TAG, "ExoPlayer准备完成，可以播放");

                        // 准备完成
                        if (onPreparedListener != null) {
                            handler.post(() -> onPreparedListener.onPrepared());
                        }

                        // 如果需要自动播放
                        if (needPlayWhenReady) {
                            needPlayWhenReady = false;
                            exoPlayer.play();
                            Log.d(TAG, "ExoPlayer自动开始播放");
                        }
                    } else if (playbackState == Player.STATE_ENDED) {
                        // 播放完成
                        Log.d(TAG, "ExoPlayer播放完成");
                        if (onCompletionListener != null) {
                            handler.post(() -> onCompletionListener.onCompletion());
                        }
                    } else if (playbackState == Player.STATE_IDLE) {
                        // 播放器空闲状态
                        isPreparing = false;
                        Log.d(TAG, "ExoPlayer空闲状态");
                    } else if (playbackState == Player.STATE_BUFFERING) {
                        // 缓冲状态
                        Log.d(TAG, "ExoPlayer正在缓冲");
                    }
                }

                @Override
                public void onPlayerError(@NonNull androidx.media3.common.PlaybackException error) {
                    isPreparing = false;

                    Log.e(TAG, "ExoPlayer错误: " + error.getMessage() + ", 错误码: " + error.errorCode, error);

                    // 尝试获取更详细的错误信息
                    String errorMessage = "ExoPlayer错误: " + error.getMessage();
                    if (error.getCause() != null) {
                        errorMessage += " 原因: " + error.getCause().getMessage();
                    }

                    final String finalErrorMsg = errorMessage;

                    // 通知错误回调
                    if (onErrorListener != null) {
                        final int errorCode = error.errorCode;
                        handler.post(() -> onErrorListener.onError(errorCode, 0));
                    }

                    // 试图恢复播放
                    if (currentUri != null) {
                        Log.d(TAG, "尝试从错误中恢复");
                        handler.postDelayed(() -> {
                            try {
                                if (exoPlayer != null) {
                                    exoPlayer.stop();
                                    prepare(currentUri);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "恢复播放失败", e);
                            }
                        }, 1000);
                    }
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    Log.d(TAG, "ExoPlayer播放状态变更: " + (isPlaying ? "正在播放" : "已暂停/停止"));
                }
            });

            Log.d(TAG, "ExoPlayer initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing ExoPlayer", e);
            exoPlayer = null;
            if (onErrorListener != null) {
                onErrorListener.onError(-1000, 0); // 使用自定义错误码
            }
        }
    }

    @Override
    public void prepare(Uri uri) {
        if (uri == null) {
            Log.e(TAG, "无法准备空URI");
            if (onErrorListener != null) {
                handler.post(() -> onErrorListener.onError(-1001, 0));
            }
            return;
        }

        Log.d(TAG, "准备播放URI: " + uri + ", scheme: " + uri.getScheme());

        // 确保播放器实例有效
        if (exoPlayer == null) {
            Log.d(TAG, "ExoPlayer实例为null，重新初始化");
            initialize();

            if (exoPlayer == null) {
                Log.e(TAG, "ExoPlayer初始化失败");
                if (onErrorListener != null) {
                    handler.post(() -> onErrorListener.onError(-1002, 0));
                }
                return;
            }
        }

        // 重置准备状态
        isPreparing = true;

        // 记录当前URI
        currentUri = uri;

        try {
            // 获取内容类型和验证文件是否可访问
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                java.io.File file = new java.io.File(uri.getPath());
                if (!file.exists()) {
                    Log.e(TAG, "文件不存在: " + uri);
                    if (onErrorListener != null) {
                        handler.post(() -> onErrorListener.onError(-1005, 0));
                    }
                    isPreparing = false;
                    return;
                }
                if (!file.canRead()) {
                    Log.e(TAG, "文件不可读: " + uri);
                    if (onErrorListener != null) {
                        handler.post(() -> onErrorListener.onError(-1006, 0));
                    }
                    isPreparing = false;
                    return;
                }
                Log.d(TAG, "文件存在且可读: " + uri);
            } else if ("content".equals(scheme)) {
                // 对于内容URI，我们尝试使用ContentResolver获取一些基本信息
                try {
                    String mimeType = context.getContentResolver().getType(uri);
                    Log.d(TAG, "内容URI MIME类型: " + (mimeType != null ? mimeType : "未知"));

                    // 检查是否是支持的音频类型
                    if (mimeType != null && !mimeType.startsWith("audio/")) {
                        Log.w(TAG, "可能不是音频文件，MIME类型: " + mimeType);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "检查内容URI时出错", e);
                }
            }

            // 停止任何当前播放并释放资源
            exoPlayer.stop();

            // 创建MediaItem (有些特殊处理以支持更多格式)
            androidx.media3.common.MediaItem.Builder builder = new androidx.media3.common.MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(uri.toString());

            MediaItem mediaItem = builder.build();

            Log.d(TAG, "开始准备媒体: " + uri);

            // 设置媒体项并准备播放
            exoPlayer.setMediaItem(mediaItem);
            // 使用异步准备
            exoPlayer.prepare();

            // 如果需要在准备好后自动播放
            if (needPlayWhenReady) {
                Log.d(TAG, "设置准备好后自动播放");
                exoPlayer.setPlayWhenReady(true);
            }

            Log.d(TAG, "ExoPlayer已开始准备播放: " + uri);
        } catch (Exception e) {
            Log.e(TAG, "准备ExoPlayer时出错: " + e.getMessage(), e);
            isPreparing = false;

            if (onErrorListener != null) {
                final String errorMsg = "准备播放失败: " + (e.getMessage() != null ? e.getMessage() : "未知错误");
                handler.post(() -> onErrorListener.onError(-1003, 0));
            }

            // 尝试恢复
            try {
                if (exoPlayer != null) {
                    exoPlayer.stop();
                }
            } catch (Exception ex) {
                Log.e(TAG, "停止播放器失败", ex);
            }
        }
    }

    @Override
    public void play() {
        if (exoPlayer != null) {
            try {
                if (isPreparing) {
                    // 如果还在准备中，设置标志位在准备完成后自动播放
                    needPlayWhenReady = true;
                    Log.d(TAG, "ExoPlayer将在准备完成后播放");
                    return;
                }

                int state = exoPlayer.getPlaybackState();
                if (state == Player.STATE_IDLE) {
                    // 如果是空闲状态，需要先准备
                    if (currentUri != null) {
                        Log.d(TAG, "ExoPlayer处于空闲状态，重新准备: " + currentUri);
                        prepare(currentUri);
                        needPlayWhenReady = true;
                    }
                } else {
                    exoPlayer.play();
                    Log.d(TAG, "ExoPlayer开始播放，状态: " + state);
                }
            } catch (Exception e) {
                Log.e(TAG, "启动ExoPlayer播放时出错: " + e.getMessage(), e);
                if (onErrorListener != null) {
                    handler.post(() -> onErrorListener.onError(-1004, 0));
                }
            }
        } else {
            Log.e(TAG, "ExoPlayer实例为null，无法播放");
            if (currentUri != null) {
                Log.d(TAG, "尝试重新初始化ExoPlayer");
                initialize();
                prepare(currentUri);
                needPlayWhenReady = true;
            }
        }
    }

    @Override
    public void pause() {
        if (exoPlayer != null) {
            try {
                exoPlayer.pause();
                Log.d(TAG, "ExoPlayer paused");
            } catch (Exception e) {
                Log.e(TAG, "Error pausing ExoPlayer", e);
            }
        }
    }

    @Override
    public void stop() {
        if (exoPlayer != null) {
            try {
                exoPlayer.stop();
                Log.d(TAG, "ExoPlayer stopped");
            } catch (Exception e) {
                Log.e(TAG, "Error stopping ExoPlayer", e);
            }
        }
    }

    @Override
    public void seekTo(int position) {
        if (exoPlayer != null) {
            try {
                exoPlayer.seekTo(position);
                Log.d(TAG, "ExoPlayer seeked to: " + position);
            } catch (Exception e) {
                Log.e(TAG, "Error seeking in ExoPlayer", e);
            }
        }
    }

    @Override
    public void release() {
        releasePlayer();
    }

    private void releasePlayer() {
        if (exoPlayer != null) {
            try {
                exoPlayer.release();
                Log.d(TAG, "ExoPlayer released");
            } catch (Exception e) {
                Log.e(TAG, "Error releasing ExoPlayer", e);
            } finally {
                exoPlayer = null;
                currentUri = null;
            }
        }
    }

    @Override
    public int getCurrentPosition() {
        if (exoPlayer != null) {
            try {
                return (int) exoPlayer.getCurrentPosition();
            } catch (Exception e) {
                Log.e(TAG, "Error getting current position", e);
            }
        }
        return 0;
    }

    @Override
    public int getDuration() {
        if (exoPlayer != null) {
            try {
                long duration = exoPlayer.getDuration();
                return duration != androidx.media3.common.C.TIME_UNSET ? (int) duration : 0;
            } catch (Exception e) {
                Log.e(TAG, "Error getting duration", e);
            }
        }
        return 0;
    }

    @Override
    public boolean isPlaying() {
        return exoPlayer != null && exoPlayer.isPlaying();
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

    /**
     * 将播放状态代码转换为可读字符串，便于调试
     */
    private String playbackStateToString(int state) {
        switch (state) {
            case Player.STATE_IDLE:
                return "空闲";
            case Player.STATE_BUFFERING:
                return "缓冲中";
            case Player.STATE_READY:
                return "就绪";
            case Player.STATE_ENDED:
                return "播放完成";
            default:
                return "未知状态:" + state;
        }
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (exoPlayer != null) {
            try {
                // ExoPlayer使用的是单一音量控制，我们取左右声道的平均值
                float volume = (leftVolume + rightVolume) / 2.0f;
                exoPlayer.setVolume(volume);
                Log.d(TAG, "ExoPlayer设置音量: " + volume);
            } catch (Exception e) {
                Log.e(TAG, "设置ExoPlayer音量时出错: " + e.getMessage(), e);
            }
        }
    }
}
