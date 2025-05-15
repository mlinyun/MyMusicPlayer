package com.mlinyun.mymusicplayer.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import java.io.IOException;

/**
 * MediaPlayer实现的播放引擎
 * 基于Android原生MediaPlayer实现IPlayerEngine接口
 */
public class MediaPlayerImpl implements IPlayerEngine {
    private static final String TAG = "MediaPlayerImpl";

    private MediaPlayer mediaPlayer;
    private Context context;
    private Uri currentUri;

    // 回调接口
    private OnCompletionListener onCompletionListener;
    private OnErrorListener onErrorListener;
    private OnPreparedListener onPreparedListener;

    /**
     * 构造函数
     *
     * @param context 应用上下文
     */
    public MediaPlayerImpl(Context context) {
        this.context = context;
    }

    @Override
    public void initialize() {
        releaseMediaPlayer();
        mediaPlayer = new MediaPlayer();

        // 设置音频属性
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mediaPlayer.setAudioAttributes(
                    new AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
            );
        } else {
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        }

        // 设置监听器
        mediaPlayer.setOnCompletionListener(mp -> {
            if (onCompletionListener != null) {
                onCompletionListener.onCompletion();
            }
        });

        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Log.e(TAG, "MediaPlayer error: what=" + what + ", extra=" + extra);
            if (onErrorListener != null) {
                onErrorListener.onError(what, extra);
                return true; // 错误已处理
            }
            return false; // 错误未处理
        });

        mediaPlayer.setOnPreparedListener(mp -> {
            if (onPreparedListener != null) {
                onPreparedListener.onPrepared();
            }
        });
    }

    @Override
    public void prepare(Uri uri) {
        if (mediaPlayer == null) {
            initialize();
        }

        try {
            // 重置播放器以便重新使用
            mediaPlayer.reset();
            currentUri = uri;

            // 设置数据源并准备播放
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.prepareAsync(); // 异步准备，防止阻塞UI线程
        } catch (IOException e) {
            Log.e(TAG, "Error preparing media player", e);
            if (onErrorListener != null) {
                onErrorListener.onError(MediaPlayer.MEDIA_ERROR_UNKNOWN, 0);
            }
        } catch (IllegalStateException e) {
            Log.e(TAG, "MediaPlayer in illegal state", e);
            // 尝试重新初始化播放器
            initialize();
            prepare(uri);
        }
    }

    @Override
    public void play() {
        if (mediaPlayer != null && !mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.start();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error starting playback", e);
                if (currentUri != null) {
                    // 如果播放失败，尝试重新准备并播放
                    prepare(currentUri);
                }
            }
        }
    }

    @Override
    public void pause() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            try {
                mediaPlayer.pause();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error pausing playback", e);
            }
        }
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping playback", e);
            }
        }
    }

    @Override
    public void seekTo(int position) {
        if (mediaPlayer != null) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    mediaPlayer.seekTo(position, MediaPlayer.SEEK_CLOSEST);
                } else {
                    mediaPlayer.seekTo(position);
                }
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error seeking to position", e);
            }
        }
    }

    @Override
    public void release() {
        releaseMediaPlayer();
    }

    /**
     * 释放MediaPlayer资源
     */
    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error stopping media player before release", e);
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public int getCurrentPosition() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error getting current position", e);
                return 0;
            }
        }
        return 0;
    }

    @Override
    public int getDuration() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.getDuration();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error getting duration", e);
                return 0;
            }
        }
        return 0;
    }

    @Override
    public void setVolume(float leftVolume, float rightVolume) {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.setVolume(leftVolume, rightVolume);
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error setting volume", e);
            }
        }
    }

    @Override
    public boolean isPlaying() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.isPlaying();
            } catch (IllegalStateException e) {
                Log.e(TAG, "Error checking playback state", e);
                return false;
            }
        }
        return false;
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
}