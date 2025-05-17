package com.mlinyun.mymusicplayer.player;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

/**
 * 音频焦点处理类
 * 负责请求、监听和管理音频焦点，处理与其他应用的音频冲突
 */
public class AudioFocusHandler implements AudioManager.OnAudioFocusChangeListener {
    private static final String TAG = "AudioFocusHandler";

    private final AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private final AudioFocusCallback callback;
    private boolean hasAudioFocus = false;
    private boolean wasPlayingBeforeFocusLoss = false;

    /**
     * 构造函数
     *
     * @param context  应用上下文
     * @param callback 焦点状态回调
     */
    public AudioFocusHandler(Context context, AudioFocusCallback callback) {
        this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        this.callback = callback;

        // 对于Android O及以上版本，预先创建AudioFocusRequest对象
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(audioAttributes)
                    .setWillPauseWhenDucked(true)
                    .setOnAudioFocusChangeListener(this)
                    .build();
        }
    }

    /**
     * 请求音频焦点
     *
     * @return 是否成功获取音频焦点
     */
    public boolean requestAudioFocus() {
        int result;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            result = audioManager.requestAudioFocus(this,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            hasAudioFocus = true;
            return true;
        } else {
            Log.w(TAG, "Failed to request audio focus");
            return false;
        }
    }

    /**
     * 放弃音频焦点
     */
    public void abandonAudioFocus() {
        if (hasAudioFocus) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.abandonAudioFocusRequest(audioFocusRequest);
            } else {
                audioManager.abandonAudioFocus(this);
            }
            hasAudioFocus = false;
        }
    }

    /**
     * 记录播放状态
     *
     * @param isPlaying 是否正在播放
     */
    public void setPlayingState(boolean isPlaying) {
        this.wasPlayingBeforeFocusLoss = isPlaying;
    }

    /**
     * 获取当前是否持有音频焦点
     *
     * @return 是否持有音频焦点
     */
    public boolean hasAudioFocus() {
        return hasAudioFocus;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                // 获得长时间的焦点
                Log.d(TAG, "Audio focus: AUDIOFOCUS_GAIN");
                hasAudioFocus = true;

                // 恢复音量
                if (callback != null) {
                    callback.onAudioFocusGained(wasPlayingBeforeFocusLoss);
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS:
                // 长时间失去焦点，例如被另一个音频应用请求了
                Log.d(TAG, "Audio focus: AUDIOFOCUS_LOSS");
                hasAudioFocus = false;

                // 通知播放器暂停
                if (callback != null) {
                    callback.onAudioFocusLost();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                // 暂时失去焦点，例如来电
                Log.d(TAG, "Audio focus: AUDIOFOCUS_LOSS_TRANSIENT");
                hasAudioFocus = false;

                // 通知播放器暂停，但稍后可能会恢复
                if (callback != null) {
                    callback.onAudioFocusLostTransient();
                }
                break;

            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                // 暂时失去焦点，但可以继续播放，只是音量降低("ducking")
                Log.d(TAG, "Audio focus: AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");

                // 通知播放器降低音量
                if (callback != null) {
                    callback.onAudioFocusLostTransientCanDuck();
                }
                break;
        }
    }

    /**
     * 音频焦点状态回调接口
     */
    public interface AudioFocusCallback {
        /**
         * 获得音频焦点
         *
         * @param wasPlayingBeforeLoss 在失去焦点前是否正在播放
         */
        void onAudioFocusGained(boolean wasPlayingBeforeLoss);

        /**
         * 永久失去音频焦点
         */
        void onAudioFocusLost();

        /**
         * 暂时失去音频焦点
         */
        void onAudioFocusLostTransient();

        /**
         * 暂时失去音频焦点但可以降低音量继续播放
         */
        void onAudioFocusLostTransientCanDuck();
    }
}
