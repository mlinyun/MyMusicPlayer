package com.mlinyun.mymusicplayer.player;

/**
 * 播放服务回调接口
 * 用于服务层和控制层之间的通信
 */
public interface ServiceCallback {

    /**
     * 播放状态改变回调
     *
     * @param state 新的播放状态
     */
    void onPlaybackStateChanged(PlayerState state);

    /**
     * 播放位置改变回调
     *
     * @param position 当前播放位置(毫秒)
     */
    void onPlaybackPositionChanged(int position);

    /**
     * 播放完成回调
     */
    void onPlaybackCompleted();

    /**
     * 错误回调
     *
     * @param errorCode    错误码
     * @param errorMessage 错误信息
     */
    void onError(int errorCode, String errorMessage);
}