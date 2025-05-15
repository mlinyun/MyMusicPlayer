package com.mlinyun.mymusicplayer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.player.AudioFocusHandler;
import com.mlinyun.mymusicplayer.player.IPlayerEngine;
import com.mlinyun.mymusicplayer.player.MediaPlayerImpl;
import com.mlinyun.mymusicplayer.player.MusicPlayerManager;
import com.mlinyun.mymusicplayer.player.PlayerState;
import com.mlinyun.mymusicplayer.player.PlayMode;
import com.mlinyun.mymusicplayer.player.ServiceCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 音乐播放器服务类
 * 负责后台音乐播放，维持播放状态，处理通知栏控制
 * 实现为前台服务，保持长时间运行而不被系统回收
 */
public class MusicPlayerService extends Service implements ServiceCallback {

    // 通知相关常量
    private static final String CHANNEL_ID = "music_player_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_PLAY = "com.mlinyun.mymusicplayer.ACTION_PLAY";
    private static final String ACTION_PAUSE = "com.mlinyun.mymusicplayer.ACTION_PAUSE";
    private static final String ACTION_PREVIOUS = "com.mlinyun.mymusicplayer.ACTION_PREVIOUS";
    private static final String ACTION_NEXT = "com.mlinyun.mymusicplayer.ACTION_NEXT";
    private static final String ACTION_STOP = "com.mlinyun.mymusicplayer.ACTION_STOP";

    // 服务绑定器
    private final IBinder binder = new MusicBinder();

    // 播放器管理器
    private MusicPlayerManager musicPlayerManager;

    // 音频焦点处理器
    private AudioFocusHandler audioFocusHandler;

    // 媒体会话
    private MediaSessionCompat mediaSession;

    // 通知管理器和构建器
    private NotificationManager notificationManager;
    private NotificationCompat.Builder notificationBuilder;

    // 进度更新相关
    private static final int UPDATE_INTERVAL = 500; // 500ms更新一次进度
    private Handler progressHandler;
    private Runnable progressRunnable;

    // 播放列表相关
    private List<Song> playlist = new ArrayList<>();
    private int currentPosition = -1;
    private PlayMode playMode = PlayMode.SEQUENCE;

    // 回调列表，用于通知UI状态变化
    private List<PlayerCallback> callbacks = new ArrayList<>();
    // 唤醒锁，防止CPU休眠导致播放中断
    private PowerManager.WakeLock wakeLock;

    // 焦点丢失前是否在播放
    private boolean wasPlayingBeforeFocusLoss = false;

    /**
     * 服务创建时的初始化
     */
    @Override
    public void onCreate() {
        super.onCreate();        // 初始化播放器管理器
        musicPlayerManager = new MusicPlayerManager(this, MusicPlayerManager.PlayerEngineType.MEDIA_PLAYER);
        musicPlayerManager.initialize();
        musicPlayerManager.setServiceCallback(this);
        // 初始化音频焦点处理器
        audioFocusHandler = new AudioFocusHandler(this, new AudioFocusHandler.AudioFocusCallback() {
            @Override
            public void onAudioFocusGained(boolean wasPlayingBeforeLoss) {
                if (musicPlayerManager != null && wasPlayingBeforeLoss) {
                    musicPlayerManager.play();
                }
            }

            @Override
            public void onAudioFocusLost() {
                if (musicPlayerManager != null && musicPlayerManager.getState() == PlayerState.PLAYING) {
                    wasPlayingBeforeFocusLoss = true;
                    musicPlayerManager.pause();
                }
            }

            @Override
            public void onAudioFocusLostTransient() {
                if (musicPlayerManager != null && musicPlayerManager.getState() == PlayerState.PLAYING) {
                    wasPlayingBeforeFocusLoss = true;
                    musicPlayerManager.pause();
                }
            }

            @Override
            public void onAudioFocusLostTransientCanDuck() {
                // 可以降低音量而不暂停
                if (musicPlayerManager != null) {
                    musicPlayerManager.setVolume(0.3f);
                }
            }
        });

        // 初始化媒体会话
        initMediaSession();

        // 初始化通知管理器
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();

        // 初始化进度更新
        progressHandler = new Handler(Looper.getMainLooper());
        progressRunnable = new Runnable() {
            @Override
            public void run() {
                if (musicPlayerManager != null &&
                        musicPlayerManager.getState() == PlayerState.PLAYING) {
                    int position = musicPlayerManager.getCurrentPosition();
                    for (PlayerCallback callback : callbacks) {
                        callback.onPositionChanged(position);
                    }
                }
                progressHandler.postDelayed(this, UPDATE_INTERVAL);
            }
        };

        // 获取唤醒锁
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MusicPlayer:WakeLock");
    }

    /**
     * 绑定服务时返回Binder对象
     */
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    /**
     * 服务启动命令处理
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            switch (action) {
                case ACTION_PLAY:
                    play();
                    break;
                case ACTION_PAUSE:
                    pause();
                    break;
                case ACTION_PREVIOUS:
                    playPrevious();
                    break;
                case ACTION_NEXT:
                    playNext();
                    break;
                case ACTION_STOP:
                    stopForeground(true);
                    stopSelf();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    /**
     * 服务销毁时释放资源
     */
    @Override
    public void onDestroy() {
        if (progressHandler != null) {
            progressHandler.removeCallbacks(progressRunnable);
        }

        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (mediaSession != null) {
            mediaSession.release();
        }

        if (audioFocusHandler != null) {
            audioFocusHandler.abandonAudioFocus();
        }

        if (musicPlayerManager != null) {
            musicPlayerManager.release();
        }

        super.onDestroy();
    }

    /**
     * 初始化媒体会话
     */
    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "MusicPlayerSession");
        mediaSession.setActive(true);
        // 媒体会话回调可以在此处实现
    }

    /**
     * 创建通知渠道（Android 8.0及以上需要）
     */
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音乐播放通知",
                    NotificationManager.IMPORTANCE_LOW); // 使用低重要性避免声音提示
            channel.setDescription("显示当前播放的音乐信息");
            channel.setShowBadge(false);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * 更新通知栏
     */
    private void updateNotification(Song song, PlayerState state) {
        if (song == null) return;

        // 创建通知
        notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_music_note)
                .setContentTitle(song.getTitle())
                .setContentText(song.getArtist())
                .setOngoing(state == PlayerState.PLAYING)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        // 添加控制按钮
        // 这些按钮的PendingIntent需要实现，连接到相应的操作

        Notification notification = notificationBuilder.build();
        startForeground(NOTIFICATION_ID, notification);
    }

    /**
     * 停止前台服务
     */
    private void stopForegroundService() {
        stopForeground(true);
    }

    /**
     * 播放方法
     */
    public void play() {
        if (currentPosition >= 0 && currentPosition < playlist.size()) {
            if (!audioFocusHandler.requestAudioFocus()) {
                return;  // 无法获取音频焦点，不播放
            }

            Song song = playlist.get(currentPosition);
            musicPlayerManager.prepareAndPlay(song);

            wakeLock.acquire(3600000); // 获取一小时的唤醒锁
            updateNotification(song, PlayerState.PLAYING);
            startProgressUpdates();
        } else if (!playlist.isEmpty()) {
            currentPosition = 0;
            play();
        }
    }

    /**
     * 继续播放
     */
    public void resume() {
        if (!audioFocusHandler.requestAudioFocus()) {
            return;
        }

        musicPlayerManager.play();
        wakeLock.acquire(3600000);
        updateNotification(getCurrentSong(), PlayerState.PLAYING);
        startProgressUpdates();
    }

    /**
     * 暂停播放
     */
    public void pause() {
        musicPlayerManager.pause();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        updateNotification(getCurrentSong(), PlayerState.PAUSED);
    }

    /**
     * 播放下一首
     */
    public void playNext() {
        if (playlist.isEmpty()) return;

        int nextPosition;
        switch (playMode) {
            case SHUFFLE:
                Random random = new Random();
                nextPosition = random.nextInt(playlist.size());
                break;
            case LOOP:
                nextPosition = (currentPosition + 1) % playlist.size();
                break;
            default: // SEQUENCE
                nextPosition = currentPosition + 1;
                if (nextPosition >= playlist.size()) {
                    nextPosition = 0; // 循环回到第一首
                }
                break;
        }

        currentPosition = nextPosition;
        play();

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onSongChanged(getCurrentSong());
        }
    }

    /**
     * 播放上一首
     */
    public void playPrevious() {
        if (playlist.isEmpty()) return;

        // 如果当前播放进度超过3秒，从头开始播放，否则播放前一首
        if (musicPlayerManager.getCurrentPosition() > 3000) {
            musicPlayerManager.seekTo(0);
            return;
        }

        int prevPosition;
        switch (playMode) {
            case SHUFFLE:
                Random random = new Random();
                prevPosition = random.nextInt(playlist.size());
                break;
            case LOOP:
                prevPosition = (currentPosition - 1 + playlist.size()) % playlist.size();
                break;
            default: // SEQUENCE
                prevPosition = currentPosition - 1;
                if (prevPosition < 0) {
                    prevPosition = playlist.size() - 1; // 循环到最后一首
                }
                break;
        }

        currentPosition = prevPosition;
        play();

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onSongChanged(getCurrentSong());
        }
    }

    /**
     * 停止播放
     */
    public void stop() {
        musicPlayerManager.stop();

        if (wakeLock.isHeld()) {
            wakeLock.release();
        }

        stopForegroundService();
    }

    /**
     * 跳转到指定位置
     */
    public void seekTo(int position) {
        musicPlayerManager.seekTo(position);
    }

    /**
     * 获取当前播放位置
     */
    public int getCurrentPosition() {
        return musicPlayerManager.getCurrentPosition();
    }

    /**
     * 获取当前歌曲总时长
     */
    public int getDuration() {
        return musicPlayerManager.getDuration();
    }

    /**
     * 设置播放模式
     */
    public void setPlayMode(PlayMode mode) {
        this.playMode = mode;
    }

    /**
     * 获取当前播放模式
     */
    public PlayMode getPlayMode() {
        return playMode;
    }

    /**
     * 设置播放列表
     */
    public void setPlaylist(List<Song> songs) {
        this.playlist = songs;

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onPlaylistChanged(playlist);
        }
    }

    /**
     * 添加歌曲到播放列表
     */
    public void addSong(Song song) {
        playlist.add(song);

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onPlaylistChanged(playlist);
        }
    }

    /**
     * 添加歌曲到播放列表并立即播放该歌曲
     *
     * @param song 要添加并播放的歌曲
     */
    public void addSongAndPlay(Song song) {
        // 添加歌曲到列表
        playlist.add(song);

        // 播放新添加的歌曲（在列表末尾）
        playAtIndex(playlist.size() - 1);

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onPlaylistChanged(playlist);
        }
    }

    /**
     * 移除歌曲
     */
    public void removeSong(int index) {
        if (index < 0 || index >= playlist.size()) return;

        // 如果移除的是正在播放的歌曲，则停止播放
        if (index == currentPosition) {
            stop();
            currentPosition = -1; // 重置当前播放位置
        }
        // 如果移除的歌曲在正在播放的歌曲之前，需要调整当前播放位置
        else if (index < currentPosition) {
            currentPosition--;
        }

        playlist.remove(index);

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onPlaylistChanged(playlist);
        }
    }

    /**
     * 播放指定索引的歌曲
     */
    public void playAtIndex(int index) {
        if (index < 0 || index >= playlist.size()) return;

        currentPosition = index;
        play();

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onSongChanged(getCurrentSong());
        }
    }

    /**
     * 获取当前歌曲
     */
    public Song getCurrentSong() {
        if (currentPosition >= 0 && currentPosition < playlist.size()) {
            return playlist.get(currentPosition);
        }
        return null;
    }

    /**
     * 获取当前播放索引
     */
    public int getCurrentIndex() {
        return currentPosition;
    }

    /**
     * 获取播放列表
     */
    public List<Song> getPlaylist() {
        return playlist;
    }

    /**
     * 获取当前播放状态
     */
    public PlayerState getPlayerState() {
        return musicPlayerManager.getState();
    }

    /**
     * 开始更新进度
     */
    private void startProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
        progressHandler.post(progressRunnable);
    }

    /**
     * 停止更新进度
     */
    private void stopProgressUpdates() {
        progressHandler.removeCallbacks(progressRunnable);
    }

    /**
     * 添加播放回调
     */
    public void addCallback(PlayerCallback callback) {
        if (!callbacks.contains(callback)) {
            callbacks.add(callback);
        }
    }

    /**
     * 移除播放回调
     */
    public void removeCallback(PlayerCallback callback) {
        callbacks.remove(callback);
    }

    /**
     * 实现ServiceCallback接口的方法
     */
    @Override
    public void onPlaybackStateChanged(PlayerState state) {
        // 通知所有回调
        for (PlayerCallback callback : callbacks) {
            callback.onPlayStateChanged(state);
        }

        // 根据状态更新服务
        if (state == PlayerState.PAUSED) {
            stopProgressUpdates();
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        } else if (state == PlayerState.PLAYING) {
            startProgressUpdates();
            if (!wakeLock.isHeld()) {
                wakeLock.acquire(3600000);
            }
        } else if (state == PlayerState.STOPPED || state == PlayerState.COMPLETED || state == PlayerState.ERROR) {
            stopProgressUpdates();
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
            stopForegroundService();
        }

        // 更新通知
        Song currentSong = getCurrentSong();
        if (currentSong != null) {
            updateNotification(currentSong, state);
        }
    }

    @Override
    public void onPlaybackPositionChanged(int position) {
        // 进度更新已在progressRunnable中处理
    }

    @Override
    public void onPlaybackCompleted() {
        // 播放完成，根据播放模式决定下一步操作
        switch (playMode) {
            case SINGLE_LOOP:
                // 单曲循环，重新播放当前歌曲
                play();
                break;
            case SHUFFLE:
            case LOOP:
            case SEQUENCE:
            default:
                // 其他模式，播放下一首
                playNext();
                break;
        }
    }

    @Override
    public void onError(int errorCode, String errorMessage) {
        // 播放错误处理
        Exception error = new Exception("错误码: " + errorCode + ", 错误信息: " + errorMessage);
        for (PlayerCallback callback : callbacks) {
            callback.onError(error);
        }

        // 尝试播放下一首
        playNext();
    }

    /**
     * 音乐播放器服务的Binder类，用于活动绑定服务
     */
    public class MusicBinder extends Binder {
        public MusicPlayerService getService() {
            return MusicPlayerService.this;
        }
    }

    /**
     * 播放器回调接口，用于通知UI层播放状态变化
     */
    public interface PlayerCallback {
        void onPlayStateChanged(PlayerState state);

        void onPositionChanged(int position);

        void onSongChanged(Song song);

        void onPlaylistChanged(List<Song> playlist);

        void onError(Exception error);
    }
}
