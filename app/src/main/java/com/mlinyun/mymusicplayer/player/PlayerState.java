package com.mlinyun.mymusicplayer.player;

/**
 * 播放器状态枚举
 * 定义音乐播放器可能的所有状态
 */
public enum PlayerState {
    // 空闲状态：初始化或停止后的状态
    IDLE,

    // 初始化状态：播放器已初始化但未准备好
    INITIALIZED,

    // 准备中状态：正在准备播放资源
    PREPARING,

    // 准备完成状态：资源加载完成，可以开始播放
    PREPARED,

    // 播放状态：正在播放音频
    PLAYING,

    // 暂停状态：播放暂停，可以继续播放
    PAUSED,

    // 停止状态：播放停止，需要重新准备才能播放
    STOPPED,

    // 播放完成状态：播放结束
    COMPLETED,

    // 错误状态：播放出错，需要恢复
    ERROR
}