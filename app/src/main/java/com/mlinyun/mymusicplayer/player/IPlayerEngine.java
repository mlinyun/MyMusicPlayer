package com.mlinyun.mymusicplayer.player;

import android.net.Uri;

/**
 * 播放器引擎接口
 * 定义播放器引擎应支持的核心功能，使得可以无缝切换MediaPlayer和ExoPlayer实现
 */
public interface IPlayerEngine {

    /**
     * 初始化播放器
     */
    void initialize();

    /**
     * 准备播放指定URI的音频
     * @param uri 音频文件URI
     */
    void prepare(Uri uri);

    /**
     * 开始播放
     */
    void play();

    /**
     * 暂停播放
     */
    void pause();

    /**
     * 停止播放并释放资源
     */
    void stop();

    /**
     * 定位到指定位置播放
     * @param position 目标位置(毫秒)
     */
    void seekTo(int position);

    /**
     * 释放播放器资源
     */
    void release();

    /**
     * 获取当前播放位置
     * @return 当前位置(毫秒)
     */
    int getCurrentPosition();

    /**
     * 获取音频总时长
     * @return 总时长(毫秒)
     */
    int getDuration();
    /**
     * 检查是否正在播放
     * @return 是否播放中
     */
    boolean isPlaying();

    /**
     * 设置音量
     * @param leftVolume 左声道音量 (0.0 到 1.0)
     * @param rightVolume 右声道音量 (0.0 到 1.0)
     */
    void setVolume(float leftVolume, float rightVolume);

    /**
     * 设置播放完成监听器
     * @param listener 完成回调接口
     */
    void setOnCompletionListener(OnCompletionListener listener);

    /**
     * 设置错误监听器
     * @param listener 错误回调接口
     */
    void setOnErrorListener(OnErrorListener listener);

    /**
     * 设置准备完成监听器
     * @param listener 准备完成回调接口
     */
    void setOnPreparedListener(OnPreparedListener listener);

    /**
     * 播放完成监听接口
     */
    interface OnCompletionListener {
        void onCompletion();
    }

    /**
     * 错误监听接口
     */
    interface OnErrorListener {
        void onError(int what, int extra);
    }

    /**
     * 准备完成监听接口
     */
    interface OnPreparedListener {
        void onPrepared();
    }
}