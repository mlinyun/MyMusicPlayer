package com.mlinyun.mymusicplayer.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.annotation.DrawableRes;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.player.PlayerState;
import com.mlinyun.mymusicplayer.ui.MainActivity;

import java.io.IOException;
import java.io.InputStream;

/**
 * 播放器通知管理器
 * 负责创建和更新播放器的通知栏界面
 * 提供音乐播放控制和当前播放信息显示
 */
public class PlayerNotificationManager {

    // 通知相关常量
    private static final String CHANNEL_ID = "music_player_channel";
    private static final int NOTIFICATION_ID = 1;

    // 通知操作的Action常量
    public static final String ACTION_PLAY = "com.mlinyun.mymusicplayer.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.mlinyun.mymusicplayer.ACTION_PAUSE";
    public static final String ACTION_PREVIOUS = "com.mlinyun.mymusicplayer.ACTION_PREVIOUS";
    public static final String ACTION_NEXT = "com.mlinyun.mymusicplayer.ACTION_NEXT";
    public static final String ACTION_STOP = "com.mlinyun.mymusicplayer.ACTION_STOP";

    // 上下文和通知管理器
    private final Context context;
    private final NotificationManager notificationManager;

    // 媒体会话Token
    private MediaSessionCompat.Token mediaSessionToken;

    /**
     * 构造函数
     *
     * @param context 应用上下文
     */
    public PlayerNotificationManager(Context context) {
        this.context = context;
        this.notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        // 创建通知渠道，根据不同Android版本进行适配
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
        }

        android.util.Log.d("PlayerNotificationManager", "初始化通知管理器，Android版本: " + Build.VERSION.SDK_INT);
    }

    /**
     * 设置媒体会话Token
     *
     * @param token 媒体会话Token
     */
    public void setMediaSessionToken(MediaSessionCompat.Token token) {
        this.mediaSessionToken = token;
    }

    /**
     * 创建通知渠道（Android 8.0及以上需要）
     */
    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel channel;

        // 根据Android版本创建适当配置的通知渠道
        if (Build.VERSION.SDK_INT >= 35) { // Android 16 (API 35+)
            // Android 16+ 版本使用DEFAULT重要性，确保通知稳定显示
            channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音乐播放通知",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("显示当前播放的音乐信息并控制播放");
            channel.setShowBadge(true);
            // 禁用通知音效
            channel.enableVibration(false);
            channel.setSound(null, null);
            android.util.Log.d("PlayerNotificationManager", "创建Android 16专用通知渠道");
        } else {
            // 以前版本使用标准的低重要性通知渠道
            channel = new NotificationChannel(
                    CHANNEL_ID,
                    "音乐播放通知",
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("显示当前播放的音乐信息");
            channel.setShowBadge(false);
            android.util.Log.d("PlayerNotificationManager", "创建标准通知渠道");
        }

        // 设置通知渠道在锁屏上可见
        channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        notificationManager.createNotificationChannel(channel);
        android.util.Log.d("PlayerNotificationManager", "通知渠道创建成功: " + CHANNEL_ID);
    }

    /**
     * 获取通知ID
     *
     * @return 通知ID
     */
    public int getNotificationId() {
        return NOTIFICATION_ID;
    }

    /**
     * 创建并更新通知
     *
     * @param song  当前播放的歌曲
     * @param state 当前播放状态
     * @return 构建的通知对象
     */
    public Notification updateNotification(Song song, PlayerState state) {
        if (song == null) {
            return null;
        }

        try {
            // 创建点击通知时打开应用的Intent
            Intent contentIntent = new Intent(context, MainActivity.class);
            contentIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }

            PendingIntent contentPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    contentIntent,
                    flags
            );

            // 创建控制按钮的Intent
            PendingIntent previousIntent = createActionIntent(ACTION_PREVIOUS);
            PendingIntent playPauseIntent = createActionIntent(state == PlayerState.PLAYING ? ACTION_PAUSE : ACTION_PLAY);
            PendingIntent nextIntent = createActionIntent(ACTION_NEXT);
            PendingIntent stopIntent = createActionIntent(ACTION_STOP);

            // 获取专辑封面图像
            Bitmap albumArt = getAlbumArt(song);

            // 构建通知
            NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_music_note)
                    .setLargeIcon(albumArt)
                    .setContentTitle(song.getTitle())
                    .setContentText(song.getArtist())
                    .setSubText(song.getAlbum())
                    .setContentIntent(contentPendingIntent)
                    .setOngoing(state == PlayerState.PLAYING)
                    .setShowWhen(false)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            // 针对Android 16+设置前台服务类型
            if (Build.VERSION.SDK_INT >= 34) { // Android 14 (API 34) or higher
                builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
                builder.setCategory(Notification.CATEGORY_TRANSPORT);
            } else if (Build.VERSION.SDK_INT >= 33) { // Android 13 (API 33)
                builder.setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE);
            }

            // 如果有媒体会话令牌，设置样式
            if (mediaSessionToken != null) {
                builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                        .setMediaSession(mediaSessionToken)
                        .setShowActionsInCompactView(0, 1, 2)); // 显示前三个按钮
            }

            // 添加控制按钮
            builder.addAction(R.drawable.ic_previous, "上一首", previousIntent);

            // 根据状态添加播放/暂停按钮
            if (state == PlayerState.PLAYING) {
                builder.addAction(R.drawable.ic_pause, "暂停", playPauseIntent);
            } else {
                builder.addAction(R.drawable.ic_play, "播放", playPauseIntent);
            }

            builder.addAction(R.drawable.ic_next, "下一首", nextIntent);
            builder.addAction(R.drawable.ic_close, "停止", stopIntent);

            try {
                // 构建并返回通知
                Notification notification = builder.build();
                notificationManager.notify(NOTIFICATION_ID, notification);
                return notification;
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 创建控制按钮的PendingIntent
     *
     * @param action 操作类型
     * @return 构建的PendingIntent
     */
    private PendingIntent createActionIntent(String action) {
        Intent intent = new Intent(context, MusicPlayerService.class);
        intent.setAction(action);

        // 从Android M开始，可以使用FLAG_IMMUTABLE标志
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }

        return PendingIntent.getService(
                context,
                getRequestCode(action),
                intent,
                flags
        );
    }

    /**
     * 根据操作类型获取请求码，确保每个PendingIntent唯一
     *
     * @param action 操作类型
     * @return 请求码
     */
    private int getRequestCode(String action) {
        switch (action) {
            case ACTION_PLAY:
                return 1;
            case ACTION_PAUSE:
                return 2;
            case ACTION_PREVIOUS:
                return 3;
            case ACTION_NEXT:
                return 4;
            case ACTION_STOP:
                return 5;
            default:
                return 0;
        }
    }

    /**
     * 获取专辑封面图像
     *
     * @param song 歌曲对象
     * @return 专辑封面Bitmap，如果不存在则返回默认图像
     */
    private Bitmap getAlbumArt(Song song) {
        Bitmap defaultArt = BitmapFactory.decodeResource(context.getResources(), R.drawable.default_album); // 需要在资源中添加默认封面

        if (song == null || song.getAlbumArtUri() == null) {
            return defaultArt;
        }

        Uri albumArtUri = song.getAlbumArtUri();
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(albumArtUri);
            if (inputStream != null) {
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                inputStream.close();
                return bitmap != null ? bitmap : defaultArt;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return defaultArt;
    }

    /**
     * 取消通知
     */
    public void cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID);
    }
}
