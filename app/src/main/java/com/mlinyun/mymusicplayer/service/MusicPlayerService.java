package com.mlinyun.mymusicplayer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

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

    private static final String TAG = "MusicPlayerService";

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

    // 专用的通知管理器
    private PlayerNotificationManager playerNotificationManager;

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

    // 错误计数器，用于防止播放错误时的无限递归
    private int errorCounter = 0;
    private static final int MAX_ERROR_COUNT = 3; // 最大连续错误次数

    // 焦点丢失前是否在播放
    private boolean wasPlayingBeforeFocusLoss = false;

    /**
     * 服务创建时的初始化
     */
    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Log.d("MusicPlayerService", "初始化服务 - Android版本: " + Build.VERSION.SDK_INT);

            // 初始化通知管理器
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            createNotificationChannel();

            // 初始化专用的通知管理器
            playerNotificationManager = new PlayerNotificationManager(this);
            // 初始化播放器管理器，根据Android版本选择合适的播放器引擎
            MusicPlayerManager.PlayerEngineType engineType;
            if (Build.VERSION.SDK_INT >= 35) {  // Android 16 (API 35+)
                // 在Android 16及以上版本使用ExoPlayer
                engineType = MusicPlayerManager.PlayerEngineType.EXO_PLAYER;
                Log.d("MusicPlayerService", "使用ExoPlayer播放引擎(适配Android 16)");
            } else {
                // 较低版本使用MediaPlayer
                engineType = MusicPlayerManager.PlayerEngineType.MEDIA_PLAYER;
                Log.d("MusicPlayerService", "使用MediaPlayer播放引擎");
            }
            musicPlayerManager = new MusicPlayerManager(this, engineType);
            musicPlayerManager.initialize();
            musicPlayerManager.setServiceCallback(this);

            // 初始化媒体会话
            initMediaSession();

            // 设置通知管理器的媒体会话令牌
            if (playerNotificationManager != null && mediaSession != null) {
                playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
            }

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

            // 初始化进度更新
            progressHandler = new Handler(Looper.getMainLooper());
            progressRunnable = new Runnable() {
                @Override
                public void run() {
                    if (musicPlayerManager != null &&
                            musicPlayerManager.getState() == PlayerState.PLAYING) {
                        int position = musicPlayerManager.getCurrentPosition();

                        // 记录一些调试信息，帮助诊断进度更新问题
                        int duration = musicPlayerManager.getDuration();
                        Log.d(TAG, "进度更新: 位置=" + position + "ms, 总时长=" + duration + "ms");

                        // 通知位置变化
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
        } catch (Exception e) {
            e.printStackTrace();
            // 处理初始化异常，确保服务可以正常启动
        }
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

        if (playerNotificationManager != null) {
            playerNotificationManager.cancelNotification();
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
        try {
            Log.d("MusicPlayerService", "初始化媒体会话，Android版本: " + Build.VERSION.SDK_INT);

            // 为不同版本Android创建适当的媒体会话
            if (Build.VERSION.SDK_INT >= 35) { // Android 16 (API 35+)
                Log.d("MusicPlayerService", "创建Android 16专用媒体会话");
                mediaSession = new MediaSessionCompat(this, "MusicPlayerSession_V16");
                // 可以在这里添加Android 16特有的媒体会话配置
            } else if (Build.VERSION.SDK_INT >= 34) { // Android 14 (API 34+)
                Log.d("MusicPlayerService", "创建Android 14专用媒体会话");
                mediaSession = new MediaSessionCompat(this, "MusicPlayerSession_V14");
            } else {
                Log.d("MusicPlayerService", "创建标准媒体会话");
                mediaSession = new MediaSessionCompat(this, "MusicPlayerSession");
            }

            mediaSession.setActive(true);

            // 媒体会话回调可以在此处实现
            // 例如：处理媒体按钮和控制台操作
            MediaSessionCompat.Callback mediaSessionCallback = new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    super.onPlay();
                    play();
                    Log.d("MediaSession", "收到播放指令");
                }

                @Override
                public void onPause() {
                    super.onPause();
                    pause();
                    Log.d("MediaSession", "收到暂停指令");
                }

                @Override
                public void onSkipToNext() {
                    super.onSkipToNext();
                    playNext();
                    Log.d("MediaSession", "收到下一首指令");
                }

                @Override
                public void onSkipToPrevious() {
                    super.onSkipToPrevious();
                    playPrevious();
                    Log.d("MediaSession", "收到上一首指令");
                }

                @Override
                public void onStop() {
                    super.onStop();
                    stop();
                    Log.d("MediaSession", "收到停止指令");
                }
            };

            mediaSession.setCallback(mediaSessionCallback);

            // 设置通知管理器的媒体会话令牌
            if (playerNotificationManager != null && mediaSession != null) {
                playerNotificationManager.setMediaSessionToken(mediaSession.getSessionToken());
                Log.d("MusicPlayerService", "媒体会话令牌已设置到通知管理器");
            }

            Log.d("MusicPlayerService", "媒体会话初始化完成");
        } catch (Exception e) {
            Log.e("MusicPlayerService", "初始化媒体会话时出错: " + e.getMessage(), e);
        }
    }

    /**
     * 创建通知渠道（Android 8.0及以上需要）
     */
    private void createNotificationChannel() {
        try {
            Log.d("MusicPlayerService", "创建通知渠道，Android版本: " + Build.VERSION.SDK_INT);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel;

                // 根据Android版本创建适当的通知渠道
                if (Build.VERSION.SDK_INT >= 35) { // Android 16 (API 35+)
                    Log.d("MusicPlayerService", "创建Android 16专用通知渠道");
                    channel = new NotificationChannel(
                            CHANNEL_ID,
                            "音乐播放通知",
                            NotificationManager.IMPORTANCE_DEFAULT); // 使用DEFAULT重要性
                    channel.setDescription("显示当前播放的音乐信息并控制播放");
                    channel.setShowBadge(true);
                    channel.enableVibration(false);
                    channel.setSound(null, null);
                } else if (Build.VERSION.SDK_INT >= 34) { // Android 14 (API 34)
                    Log.d("MusicPlayerService", "创建Android 14专用通知渠道");
                    channel = new NotificationChannel(
                            CHANNEL_ID,
                            "音乐播放通知",
                            NotificationManager.IMPORTANCE_DEFAULT);
                    channel.setDescription("显示当前播放的音乐信息并控制播放");
                    channel.setShowBadge(true);
                    channel.enableVibration(false);
                    channel.setSound(null, null);
                } else {
                    Log.d("MusicPlayerService", "创建标准通知渠道");
                    channel = new NotificationChannel(
                            CHANNEL_ID,
                            "音乐播放通知",
                            NotificationManager.IMPORTANCE_LOW); // 使用低重要性避免声音提示
                    channel.setDescription("显示当前播放的音乐信息");
                    channel.setShowBadge(false);
                }

                // 设置通知渠道类别为媒体播放
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

                notificationManager.createNotificationChannel(channel);
                Log.d("MusicPlayerService", "通知渠道创建成功: " + CHANNEL_ID);
            } else {
                Log.d("MusicPlayerService", "不需要创建通知渠道 (Android 8.0以下)");
            }
        } catch (Exception e) {
            Log.e("MusicPlayerService", "创建通知渠道失败: " + e.getMessage(), e);
        }
    }

    /**
     * 更新通知栏
     */
    private void updateNotification(Song song, PlayerState state) {
        if (song == null) return;

        try {
            // 使用专用的通知管理器创建通知
            Notification notification = playerNotificationManager.updateNotification(song, state);

            if (notification != null) {
                // 确保前台服务适配Android 16
                try {
                    if (state == PlayerState.PLAYING) {
                        if (Build.VERSION.SDK_INT >= 35) { // Android 16 (API 35)
                            // Android 16或更高版本，使用SERVICE_TYPE_MEDIA_PLAYBACK
                            startForeground(
                                    playerNotificationManager.getNotificationId(),
                                    notification,
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                            );
                            Log.d("MusicPlayerService", "启动前台服务 (Android 16+ 方式)");
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { // API 29-34
                            startForeground(
                                    playerNotificationManager.getNotificationId(),
                                    notification,
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                            );
                            Log.d("MusicPlayerService", "启动前台服务 (Android 10+ 方式)");
                        } else {
                            startForeground(playerNotificationManager.getNotificationId(), notification);
                            Log.d("MusicPlayerService", "启动前台服务 (传统方式)");
                        }
                    } else {
                        // 如果不是播放状态，仅更新通知而不保持前台服务
                        notificationManager.notify(NOTIFICATION_ID, notification);

                        // 在暂停状态下可以选择性地停止前台服务，但保留通知
                        if (Build.VERSION.SDK_INT >= 35) { // Android 16
                            stopForeground(Service.STOP_FOREGROUND_DETACH);
                            Log.d("MusicPlayerService", "停止前台服务但保留通知 (Android 16+)");
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                            stopForeground(STOP_FOREGROUND_DETACH); // 停止前台服务但保留通知
                            Log.d("MusicPlayerService", "停止前台服务但保留通知 (Android 7+)");
                        } else {
                            stopForeground(false);
                            Log.d("MusicPlayerService", "停止前台服务但保留通知 (传统方式)");
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();

                    // 记录错误详情以便调试
                    Log.e("MusicPlayerService", "前台服务错误: " + e.getMessage(), e);

                    // 通知回调发生错误
                    for (PlayerCallback callback : callbacks) {
                        callback.onError(e);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();

            // 如果专用通知管理器失败，尝试使用旧的方法（备用方案）
            try {
                Log.d("MusicPlayerService", "使用备用通知方法");
                notificationBuilder = new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_music_note)
                        .setContentTitle(song.getTitle())
                        .setContentText(song.getArtist())
                        .setOngoing(state == PlayerState.PLAYING)
                        .setPriority(NotificationCompat.PRIORITY_HIGH);

                // 为Android 16添加特殊处理
                if (Build.VERSION.SDK_INT >= 35) { // Android 16 (API 35)
                    notificationBuilder.setForegroundServiceBehavior(
                            NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                    );
                    notificationBuilder.setCategory(Notification.CATEGORY_TRANSPORT);
                    Log.d("MusicPlayerService", "应用Android 16特定通知设置");
                } else if (Build.VERSION.SDK_INT >= 34) { // Android 14+ (API 34)
                    notificationBuilder.setForegroundServiceBehavior(
                            NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE
                    );
                    Log.d("MusicPlayerService", "应用Android 14特定通知设置");
                }

                Notification notification = notificationBuilder.build();

                if (state == PlayerState.PLAYING) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        startForeground(
                                NOTIFICATION_ID,
                                notification,
                                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                        );
                    } else {
                        startForeground(NOTIFICATION_ID, notification);
                    }
                    Log.d("MusicPlayerService", "备用方法启动前台服务");
                } else {
                    notificationManager.notify(NOTIFICATION_ID, notification);
                    Log.d("MusicPlayerService", "备用方法只更新通知");
                }
            } catch (Exception ex) {
                ex.printStackTrace();

                for (PlayerCallback callback : callbacks) {
                    callback.onError(ex);
                }
            }
        }
    }

    /**
     * 停止前台服务
     */
    private void stopForegroundService() {
        try {
            // 使用专用通知管理器取消通知
            if (playerNotificationManager != null) {
                playerNotificationManager.cancelNotification();
            }

            // 根据不同的Android版本适配停止前台服务的方法
            if (Build.VERSION.SDK_INT >= 35) { // Android 16 (API 35+)
                Log.d("MusicPlayerService", "使用Android 16方式停止前台服务");
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else if (Build.VERSION.SDK_INT >= 34) { // API 34
                Log.d("MusicPlayerService", "使用API 34方式停止前台服务");
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Log.d("MusicPlayerService", "使用API 24-33方式停止前台服务");
                stopForeground(STOP_FOREGROUND_REMOVE);
            } else {
                Log.d("MusicPlayerService", "使用传统方式停止前台服务");
                stopForeground(true);
            }
        } catch (Exception e) {
            Log.e("MusicPlayerService", "停止前台服务失败: " + e.getMessage(), e);

            // 备用方法，确保通知被移除
            try {
                NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (nm != null) {
                    nm.cancel(NOTIFICATION_ID);
                    Log.d("MusicPlayerService", "使用备用方法移除通知");
                }
            } catch (Exception ex) {
                Log.e("MusicPlayerService", "备用方法移除通知也失败: " + ex.getMessage(), ex);
            }
        }
    }

    /**
     * 播放方法
     */
    public void play() {
        // 安全检查：如果错误计数已经达到最大值，不要继续尝试播放
        if (errorCounter >= MAX_ERROR_COUNT) {
            return;
        }

        if (currentPosition >= 0 && currentPosition < playlist.size()) {
            if (!audioFocusHandler.requestAudioFocus()) {
                return;  // 无法获取音频焦点，不播放
            }

            Song song = playlist.get(currentPosition);

            // 先创建通知并启动前台服务，然后再播放音乐
            // 这样可以确保前台服务已经启动，避免权限问题
            updateNotification(song, PlayerState.PLAYING);

            // 然后播放音乐
            musicPlayerManager.prepareAndPlay(song);

            wakeLock.acquire(3600000); // 获取一小时的唤醒锁
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

        // 先更新通知并启动前台服务
        updateNotification(getCurrentSong(), PlayerState.PLAYING);

        // 然后播放音乐
        musicPlayerManager.play();

        wakeLock.acquire(3600000);
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

        // 安全检查：如果错误计数已经达到最大值，不要继续尝试播放
        if (errorCounter >= MAX_ERROR_COUNT) {
            return;
        }

        int nextPosition;
        switch (playMode) {
            case SHUFFLE:
                Random random = new Random();
                nextPosition = random.nextInt(playlist.size());
                break;
            case LOOP:
                nextPosition = (currentPosition + 1) % playlist.size();
                break;
            case SINGLE_LOOP:
                // 单曲循环模式下，仍然允许用户手动切换到下一首
                nextPosition = (currentPosition + 1) % playlist.size();
                break;
            default: // SEQUENCE
                nextPosition = currentPosition + 1;
                if (nextPosition >= playlist.size()) {
                    nextPosition = 0; // 循环回到第一首
                }
                break;
        }

        // 额外的安全检查，确保位置有效
        if (nextPosition < 0 || nextPosition >= playlist.size()) {
            nextPosition = 0;
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

        // 安全检查：如果错误计数已经达到最大值，不要继续尝试播放
        if (errorCounter >= MAX_ERROR_COUNT) {
            return;
        }

        // 当处于单曲循环模式并且不是从头开始时，只重置当前歌曲到开始位置
        if (playMode == PlayMode.SINGLE_LOOP && musicPlayerManager.getCurrentPosition() > 3000) {
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
            case SINGLE_LOOP:
                // 单曲循环模式下，仍然允许用户手动切换到上一首
                prevPosition = (currentPosition - 1 + playlist.size()) % playlist.size();
                break;
            default: // SEQUENCE
                prevPosition = currentPosition - 1;
                if (prevPosition < 0) {
                    prevPosition = playlist.size() - 1; // 循环到最后一首
                }
                break;
        }

        // 额外的安全检查，确保位置有效
        if (prevPosition < 0 || prevPosition >= playlist.size()) {
            prevPosition = 0;
        }

        // 更新当前位置并播放
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

        // 每次手动播放时重置错误计数器
        errorCounter = 0;
        currentPosition = index;
        play();

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onSongChanged(getCurrentSong());
        }
    }

    /**
     * 播放指定歌曲
     *
     * @param song 要播放的歌曲对象
     */
    public void playSong(Song song) {
        if (song == null) return;

        // 检查歌曲是否已在播放列表中
        int songIndex = -1;
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).getId() == song.getId()) {
                songIndex = i;
                break;
            }
        }

        // 如果歌曲在播放列表中，直接播放
        if (songIndex != -1) {
            playAtIndex(songIndex);
        } else {
            // 如果歌曲不在播放列表中，添加到播放列表并播放
            addSongAndPlay(song);
        }

        // 在播放新歌曲时重置错误计数器
        errorCounter = 0;
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
            // 播放成功，重置错误计数器
            errorCounter = 0;
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
        // 如果播放列表为空，不执行任何操作
        if (playlist.isEmpty()) {
            return;
        }

        // 播放成功完成，重置错误计数器
        errorCounter = 0;

        // 播放完成，根据播放模式决定下一步操作
        switch (playMode) {
            case SINGLE_LOOP:
                // 单曲循环，重新播放当前歌曲
                musicPlayerManager.seekTo(0);
                play();
                break;
            case LOOP:
                // 列表循环，播放下一首
                currentPosition = (currentPosition + 1) % playlist.size();
                play();
                break;
            case SHUFFLE:
                // 随机播放，随机选择下一首
                if (playlist.size() > 1) {
                    // 避免随机到相同的歌曲
                    int oldPosition = currentPosition;
                    Random random = new Random();
                    do {
                        currentPosition = random.nextInt(playlist.size());
                    } while (currentPosition == oldPosition && playlist.size() > 1);
                } else {
                    // 只有一首歌曲时简单重播
                    currentPosition = 0;
                }
                play();
                break;
            case SEQUENCE:
            default:
                // 顺序播放，播放下一首，如果已经是最后一首则循环到第一首
                currentPosition = (currentPosition + 1) % playlist.size();
                play();
                break;
        }

        // 通知回调
        for (PlayerCallback callback : callbacks) {
            callback.onSongChanged(getCurrentSong());
        }
    }

    @Override
    public void onDurationChanged(int duration) {
        Log.d("MusicPlayerService", "收到媒体总时长更新: " + duration + "ms");

        // 通知所有回调
        for (PlayerCallback callback : callbacks) {
            callback.onDurationChanged(duration);
        }
    }

    @Override
    public void onError(int errorCode, String errorMessage) {
        errorCounter++;

        // 播放错误处理
        String errorMsg = "错误码: " + errorCode + ", 错误信息: " + errorMessage;
        if (errorCounter >= MAX_ERROR_COUNT) {
            errorMsg += " (连续错误次数过多，停止播放)";
        }
        Log.e("MusicPlayerService", "播放错误: " + errorMsg);
        Exception error = new Exception(errorMsg);

        // 通知UI层发生了错误
        for (PlayerCallback callback : callbacks) {
            callback.onError(error);
        }
        // 根据错误类型采取不同策略
        if (errorCode == -38) { // MediaPlayer特定错误，可能是文件格式不支持
            Log.d("MusicPlayerService", "检测到MediaPlayer错误-38，建议切换到ExoPlayer播放引擎");

            try {
                // 重新初始化播放器管理器，使用ExoPlayer (支持Android 16 SDK 35)
                Log.d("MusicPlayerService", "正在切换到ExoPlayer引擎");
                musicPlayerManager.release();
                musicPlayerManager = new MusicPlayerManager(this, MusicPlayerManager.PlayerEngineType.EXO_PLAYER);
                musicPlayerManager.initialize();
                musicPlayerManager.setServiceCallback(this);

                // 如果有当前歌曲，尝试用新的播放器播放
                if (currentPosition >= 0 && currentPosition < playlist.size()) {
                    // 短暂延迟，确保播放器初始化完成
                    new Handler().postDelayed(() -> {
                        Log.d("MusicPlayerService", "使用ExoPlayer重新尝试播放当前歌曲");
                        playSong(playlist.get(currentPosition));
                    }, 500);
                    return;
                }
            } catch (Exception e) {
                Log.e("MusicPlayerService", "切换到ExoPlayer时出错: " + e.getMessage(), e);
            }
        }

        // 常规错误处理逻辑
        if (errorCounter < MAX_ERROR_COUNT) {
            // 低于错误阈值，尝试播放下一首
            Log.d("MusicPlayerService", "尝试播放下一首歌曲");
            playNext();
        } else {
            // 超过最大错误次数，停止播放并重置错误计数
            Log.d("MusicPlayerService", "连续错误次数过多，停止播放");
            stop();

            // 重置当前播放位置，避免再次尝试播放同一首歌曲
            currentPosition = -1;

            // 重置错误计数器，以便用户下次可以再次尝试播放
            errorCounter = 0;
        }
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

        void onDurationChanged(int duration);
    }
}
