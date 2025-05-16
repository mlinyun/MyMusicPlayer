package com.mlinyun.mymusicplayer.player;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;

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
            Log.d(TAG, "已创建MediaPlayer播放引擎");
        } else if (engineType == PlayerEngineType.EXO_PLAYER) {
            // 使用ExoPlayer实现，更适合Android 16 (SDK 35)
            playerEngine = new ExoPlayerImpl(context);
            Log.d(TAG, "已创建ExoPlayer播放引擎");
        } else {
            // 默认回退到MediaPlayer
            playerEngine = new MediaPlayerImpl(context);
            Log.d(TAG, "使用默认MediaPlayer播放引擎");
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
        try {
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

                Log.e(TAG, "播放引擎错误: " + what + ", " + extra);

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
        } catch (Exception e) {
            Log.e(TAG, "Error initializing player engine", e);
            currentState = PlayerState.ERROR;

            if (serviceCallback != null) {
                serviceCallback.onError(-1, "播放器初始化失败: " + e.getMessage());
                serviceCallback.onPlaybackStateChanged(currentState);
            }
        }
    }

    /**
     * 加载并准备播放歌曲
     *
     * @param song 待播放的歌曲
     */
    public void prepareAndPlay(Song song) {
        // 预检查
        if (song == null || song.getPath() == null) {
            Log.e(TAG, "尝试播放无效的歌曲数据");
            if (serviceCallback != null) {
                serviceCallback.onError(-1, "无效的歌曲数据");
            }
            return;
        }

        // 先停止当前可能正在播放的内容
        if (currentState == PlayerState.PLAYING || currentState == PlayerState.PAUSED) {
            try {
                playerEngine.stop();
            } catch (Exception e) {
                Log.e(TAG, "停止当前播放时出错", e);
                // 忽略停止时的异常，继续尝试播放新歌曲
            }
        }

        try {
            // 检查文件是否存在
            File file = new File(song.getPath());
            if (!file.exists()) {
                Log.e(TAG, "文件不存在: " + song.getPath());
                if (serviceCallback != null) {
                    serviceCallback.onError(-1, "文件不存在或无法访问: " + song.getPath());
                }
                return;
            }

            if (!file.canRead()) {
                Log.e(TAG, "文件无法读取: " + song.getPath());
                if (serviceCallback != null) {
                    serviceCallback.onError(-1, "文件无法读取: " + song.getPath());
                }
                return;
            }

            Log.d(TAG, "准备播放: " + song.getTitle() + " (" + song.getPath() + ")");

            currentSong = song;
            Uri uri = Uri.parse(song.getPath());
            currentState = PlayerState.PREPARING;

            if (serviceCallback != null) {
                serviceCallback.onPlaybackStateChanged(currentState);
            }

            // 准备播放引擎
            playerEngine.prepare(uri);

        } catch (Exception e) {
            Log.e(TAG, "准备歌曲时出错", e);
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
        try {
            switch (currentState) {
                case PREPARED:
                case PAUSED:
                    Log.d(TAG, "播放器状态: " + currentState + " - 开始播放");
                    playerEngine.play();
                    currentState = PlayerState.PLAYING;
                    startProgressTracking();

                    if (serviceCallback != null) {
                        serviceCallback.onPlaybackStateChanged(currentState);
                    }
                    break;

                case COMPLETED:
                    // 如果播放完成，从头开始播放
                    Log.d(TAG, "播放器状态: COMPLETED - 重新从头开始播放");
                    seekTo(0);
                    playerEngine.play();
                    currentState = PlayerState.PLAYING;
                    startProgressTracking();

                    if (serviceCallback != null) {
                        serviceCallback.onPlaybackStateChanged(currentState);
                    }
                    break;

                case STOPPED:
                case ERROR:
                    // 如果已停止或有错误，需要重新准备
                    Log.d(TAG, "播放器状态: " + currentState + " - 需要重新准备");
                    if (currentSong != null) {
                        prepareAndPlay(currentSong);
                    } else {
                        Log.e(TAG, "无法播放：当前无歌曲");
                        if (serviceCallback != null) {
                            serviceCallback.onError(-1, "无法播放：当前无歌曲");
                        }
                    }
                    break;

                case PLAYING:
                    // 已经在播放，不需要操作
                    Log.d(TAG, "播放器已经处于播放状态");
                    break;

                default:
                    // 其他状态（如PREPARING, IDLE等），不做操作
                    Log.d(TAG, "播放器当前状态不支持播放操作: " + currentState);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "播放过程中发生错误", e);
            currentState = PlayerState.ERROR;

            if (serviceCallback != null) {
                serviceCallback.onError(-1, "播放失败: " + e.getMessage());
                serviceCallback.onPlaybackStateChanged(currentState);
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
