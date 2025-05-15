package com.mlinyun.mymusicplayer.player;

/**
 * 播放模式枚举
 * 定义音乐播放器支持的播放模式
 */
public enum PlayMode {
    // 顺序播放：按列表顺序播放，播放到最后一首时停止
    SEQUENCE,

    // 列表循环：按列表顺序循环播放，播放到最后一首后继续播放第一首
    LOOP,

    // 随机播放：随机顺序播放列表中的歌曲
    SHUFFLE,

    // 单曲循环：循环播放当前歌曲
    SINGLE_LOOP
}