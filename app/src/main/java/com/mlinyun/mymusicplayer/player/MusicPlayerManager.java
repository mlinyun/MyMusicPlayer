package com.mlinyun.mymusicplayer.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.mlinyun.mymusicplayer.model.Song;

/**
 * 音乐播放器管理类
 * 封装播放引擎，提供统一的播放控制接口，处理播放状态管理
 */
public class MusicPlayerManager {
    private static final String TAG = "MusicPlayerManager";
    private static final int UPDATE_INTERVAL_MS = 100; // 进度更新间隔(毫秒)

    private IPlayerEngine playerEngine;
    private ServiceCallback serviceCallback;
    private PlayerState currentState;
    private PlayMode playMode;
    private Song currentSong;
    private Context context;

    private Handler progressHandler;
    private Runnable progressRunnable;
    private boolean isProgressTracking = false;

    /**
     * 构造函数
     *
     * @param context    应用上下文
     * @param engineType 播放引擎类型
     */
    public MusicPlayerManager(Context context, PlayerEngineType engineType) {
        this.context = context;
        this.currentState = PlayerState.IDLE;
        this.playMode = PlayMode.LOOP; // 默认为列表循环播放模式

        // 创建播放引擎
        if (engineType == PlayerEngineType.MEDIA_PLAYER) {
            playerEngine = new MediaPlayerImpl(context);
        } else {
            // 可以在这里添加ExoPlayer实现
            playerEngine = new MediaPlayerImpl(context);
        }

        // 初始化进度更新Handler
        progressHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (playerEngine != null && isPlaying() && serviceCallback != null) {
                    int position = playerEngine.getCurrentPosition();
                    serviceCallback.onPlaybackPositionChanged(position);
                }
                progressHandler.postDelayed(this, UPDATE_INTERVAL_MS);
            }
        };

        // 初始化引擎
        initialize();
    }

    /**
     * 初始化播放器
     */
    public void initialize() {
        playerEngine.initialize();

        // 设置监听器
        playerEngine.setOnCompletionListener(() -> {
            currentState = PlayerState.COMPLETED;
            stopProgressTracking();

            if (serviceCallback != null) {
                serviceCallback.onPlaybackCompleted();
                serviceCallback.onPlaybackStateChanged(currentState);
            }
        });

        playerEngine.setOnErrorListener((what, extra) -> {
            currentState = PlayerState.ERROR;
            stopProgressTracking();

            if (serviceCallback != null) {
                serviceCallback.onError(what, "播放出错: " + what + ", " + extra);
                serviceCallback.onPlaybackStateChanged(currentState);
            }
        });

        playerEngine.setOnPreparedListener(() -> {
            currentState = PlayerState.PREPARED;

            if (serviceCallback != null) {
                serviceCallback.onPlaybackStateChanged(currentState);
            }

            // 如果设置了自动播放，则准备完成后立即播放
            play();
        });

        currentState = PlayerState.IDLE;
    }

    /**
     * 加载并准备播放歌曲
     *
     * @param song 待播放的歌曲
     */
    public void prepareAndPlay(Song song) {
        if (song == null || song.getPath() == null) {
            if (serviceCallback != null) {
                serviceCallback.onError(-1, "无效的歌曲数据");
            }
            return;
        }

        try {
            currentSong = song;
            Uri uri = Uri.parse(song.getPath());
            currentState = PlayerState.PREPARING;

            if (serviceCallback != null) {
                serviceCallback.onPlaybackStateChanged(currentState);
            }

            playerEngine.prepare(uri);
        } catch (Exception e) {
            Log.e(TAG, "Error preparing song", e);
            currentState = PlayerState.ERROR;

            if (serviceCallback != null) {
                serviceCallback.onError(-1, "准备歌曲失败: " + e.getMessage());
                serviceCallback.onPlaybackStateChanged(currentState);
            }
        }
    }

    /**
     * 开始播放
     */
    public void play() {
        if (currentState == PlayerState.PREPARED || currentState == PlayerState.PAUSED) {
            playerEngine.play();
            currentState = PlayerState.PLAYING;
            startProgressTracking();

            if (serviceCallback != null) {
                serviceCallback.onPlaybackStateChanged(currentState);
            }
        } else if (currentState == PlayerState.COMPLETED) {
            // 如果播放完成，从头开始播放
            seekTo(0);
            playerEngine.play();
            currentState = PlayerState.PLAYING;
            startProgressTracking();

            if (serviceCallback != null) {
                serviceCallback.onPlaybackStateChanged(currentState);
            }
        } else if (currentState == PlayerState.STOPPED) {
            // 如果已停止，需要重新准备
            if (currentSong != null) {
                prepareAndPlay(currentSong);
            }
        }
    }

    /**
     * 暂停播放
     */
    public void pause() {
        if (currentState == PlayerState.PLAYING) {
            playerEngine.pause();
            currentState = PlayerState.PAUSED;
            stopProgressTracking();

            if (serviceCallback != null) {
                serviceCallback.onPlaybackStateChanged(currentState);
            }
        }
    }

    /**
     * 停止播放
     */
    public void stop() {
        playerEngine.stop();
        currentState = PlayerState.STOPPED;
        stopProgressTracking();

        if (serviceCallback != null) {
            serviceCallback.onPlaybackStateChanged(currentState);
        }
    }

    /**
     * 跳转到指定位置
     *
     * @param position 播放位置(毫秒)
     */
    public void seekTo(int position) {
        playerEngine.seekTo(position);

        if (serviceCallback != null) {
            serviceCallback.onPlaybackPositionChanged(position);
        }
    }

    /**
     * 释放资源
     */
    public void release() {
        stop();
        stopProgressTracking();
        playerEngine.release();
        currentState = PlayerState.IDLE;
    }

    /**
     * 开始跟踪播放进度
     */
    private void startProgressTracking() {
        if (!isProgressTracking) {
            progressHandler.post(progressRunnable);
            isProgressTracking = true;
        }
    }

    /**
     * 停止跟踪播放进度
     */
    private void stopProgressTracking() {
        if (isProgressTracking) {
            progressHandler.removeCallbacks(progressRunnable);
            isProgressTracking = false;
        }
    }

    /**
     * 获取当前播放位置
     *
     * @return 当前位置(毫秒)
     */
    public int getCurrentPosition() {
        return playerEngine.getCurrentPosition();
    }

    /**
     * 获取音频总时长
     *
     * @return 总时长(毫秒)
     */
    public int getDuration() {
        return playerEngine.getDuration();
    }

    /**
     * 检查是否正在播放
     *
     * @return 是否播放中
     */
    public boolean isPlaying() {
        return playerEngine.isPlaying();
    }

    /**
     * 获取当前播放状态
     *
     * @return 播放状态
     */
    public PlayerState getCurrentState() {
        return currentState;
    }

    /**
     * 获取当前播放状态 (别名方法，为了兼容性)
     *
     * @return 播放状态
     */
    public PlayerState getState() {
        return currentState;
    }

    /**
     * 设置播放音量
     *
     * @param volume 音量级别 (0.0 到 1.0)
     */
    public void setVolume(float volume) {
        if (playerEngine != null) {
            playerEngine.setVolume(volume, volume);
        }
    }

    /**
     * 获取当前播放模式
     *
     * @return 播放模式
     */
    public PlayMode getPlayMode() {
        return playMode;
    }

    /**
     * 设置播放模式
     *
     * @param playMode 播放模式
     */
    public void setPlayMode(PlayMode playMode) {
        this.playMode = playMode;
    }

    /**
     * 获取当前播放的歌曲
     *
     * @return 当前歌曲
     */
    public Song getCurrentSong() {
        return currentSong;
    }

    /**
     * 设置服务回调接口
     *
     * @param serviceCallback 回调接口
     */
    public void setServiceCallback(ServiceCallback serviceCallback) {
        this.serviceCallback = serviceCallback;
    }

    /**
     * 播放引擎类型枚举
     */
    public enum PlayerEngineType {
        MEDIA_PLAYER,
        EXO_PLAYER
    }
}
