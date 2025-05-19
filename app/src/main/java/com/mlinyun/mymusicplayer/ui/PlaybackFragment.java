package com.mlinyun.mymusicplayer.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.model.Lyrics;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.player.PlayMode;
import com.mlinyun.mymusicplayer.player.PlayerState;
import com.mlinyun.mymusicplayer.view.LrcView;
import com.mlinyun.mymusicplayer.viewmodel.PlayerViewModel;

/**
 * 播放界面Fragment
 * 负责显示和控制当前播放歌曲的界面
 */
public class PlaybackFragment extends Fragment implements PlaylistBottomSheetDialog.PlaylistDialogCallback {
    // 标签常量
    private static final String TAG = "PlaybackFragment";
    // UI组件
    private ImageView ivAlbumArt;
    private TextView tvSongTitle;
    private TextView tvArtist;
    private SeekBar seekBar;
    private TextView tvCurrentTime;
    private TextView tvTotalTime;
    private ImageButton ibPlayPause;
    private ImageButton ibPrevious;
    private ImageButton ibNext;
    private ImageButton ibPlayMode;
    private ImageButton ibPlaylist;
    private TextView tvCurrentLyric;
    private View albumContainer;
    private View lyricsCard;
    private LrcView lrcViewFullscreen;
    private ImageView ivHintToAlbum;
    // 已移除搜索结果指示器相关代码
    private boolean isShowingLyrics = false;


    // ViewModel
    private PlayerViewModel viewModel;
    // 专辑封面旋转动画
    private ObjectAnimator rotationAnimator;

    // 是否用户正在拖动进度条
    private boolean isUserSeeking = false;

    // 记录上次显示Toast提示的时间
    private long lastToastTime = 0;

    // 记录双击检测的上次点击时间
    private long lastClickTime = 0;

    // 声明为类的成员变量
    private GestureDetector gestureDetector;

    /**
     * 创建Fragment视图
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_playback, container, false);
    }

    /**
     * 视图创建完成后的初始化
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化UI组件
        initViews(view);

        // 获取ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);

        // 设置监听器
        setupListeners();

        // 设置专辑封面旋转动画
        setupRotationAnimation();

        // 观察ViewModel数据变化
        observeViewModel();        // 打印日志确认双击功能初始化
    }

    /**
     * 初始化UI组件
     */
    private void initViews(View view) {
        ivAlbumArt = view.findViewById(R.id.ivAlbumArt);
        tvSongTitle = view.findViewById(R.id.tvSongTitle);
        tvArtist = view.findViewById(R.id.tvArtist);
        seekBar = view.findViewById(R.id.seekBar);
        tvCurrentTime = view.findViewById(R.id.tvCurrentTime);
        tvTotalTime = view.findViewById(R.id.tvTotalTime);
        ibPlayPause = view.findViewById(R.id.ibPlayPause);
        ibPrevious = view.findViewById(R.id.ibPrevious);
        ibNext = view.findViewById(R.id.ibNext);
        ibPlayMode = view.findViewById(R.id.ibPlayMode);
        ibPlaylist = view.findViewById(R.id.ibPlaylist);
        tvCurrentLyric = view.findViewById(R.id.tvCurrentLyric);  // 替换为新的歌词文本视图
        albumContainer = view.findViewById(R.id.albumContainer);
        lyricsCard = view.findViewById(R.id.lyricsCard);
        lrcViewFullscreen = view.findViewById(R.id.lrcViewFullscreen);
        ivHintToAlbum = view.findViewById(R.id.ivHintToAlbum);
        // 搜索结果指示器已移除

        // 如果当前找不到返回专辑按钮，做兼容处理
        if (ivHintToAlbum == null) {
            android.util.Log.e("PlaybackFragment", "找不到返回专辑按钮(ID: ivHintToAlbum)");
        }

        // 设置默认状态
        tvCurrentTime.setText("00:00");
        tvTotalTime.setText("00:00");
        ibPlayPause.setImageResource(R.drawable.ic_play);

        // 初始化专辑/歌词容器状态
        albumContainer.setVisibility(View.VISIBLE);
        lyricsCard.setVisibility(View.GONE);
        isShowingLyrics = false;
    }

    /**
     * 设置监听器
     */
    private void setupListeners() {
        // 播放/暂停按钮点击监听
        ibPlayPause.setOnClickListener(v -> viewModel.togglePlayPause());

        // 上一曲按钮点击监听
        ibPrevious.setOnClickListener(v -> viewModel.playPrevious());

        // 下一曲按钮点击监听
        ibNext.setOnClickListener(v -> viewModel.playNext());

        // 播放模式切换监听
        ibPlayMode.setOnClickListener(v -> togglePlayMode());        // 播放列表按钮点击监听
        ibPlaylist.setOnClickListener(v -> {
            // 如果当前播放的是搜索结果，弹出添加到播放列表的选项
            Song currentSong = viewModel.getCurrentSong().getValue();
            if (currentSong != null && currentSong.isSearchResult()) {
                PlaybackMenuHelper.addCurrentSearchResultToPlaylist(requireContext(), viewModel);
            } else {
                // 显示播放队列底部对话框
                PlaylistBottomSheetDialog dialog = PlaylistBottomSheetDialog.newInstance();
                dialog.show(getChildFragmentManager(), "playlist_dialog");
            }
        });

        // 进度条变化监听
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    Integer duration = viewModel.getDuration().getValue();
                    if (duration != null) {
                        int position = progress * duration / 100;
                        tvCurrentTime.setText(viewModel.formatTime(position));
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                Integer duration = viewModel.getDuration().getValue();
                if (duration != null) {
                    int position = seekBar.getProgress() * duration / 100;
                    viewModel.seekTo(position);
                }
                isUserSeeking = false;
            }
        });        // 设置全屏歌词视图的点击监听
        // 这里不需要处理单击事件，因为我们已经用GestureDetector处理了触摸事件
        lrcViewFullscreen.setLrcViewListener(new LrcView.LrcViewListener() {
            @Override
            public void onLrcViewClick() {
                // 不执行任何操作，由GestureDetector处理触摸事件
            }

            @Override
            public void onLrcLineTap(int line, com.mlinyun.mymusicplayer.model.LyricLine lrcLine) {
                // 点击全屏歌词行时跳转到对应时间点
                viewModel.seekTo((int) lrcLine.getTimeMs());
            }
        });

        // 添加专辑封面容器的点击事件，点击切换到歌词视图
        albumContainer.setOnClickListener(v -> {
            toggleLyricsView(true);
        });
    }

    /**
     * 切换歌词/专辑封面显示
     *
     * @param showLyrics 是否显示歌词
     */
    private void toggleLyricsView(boolean showLyrics) {        // 记录当前状态

        if (isShowingLyrics == showLyrics) return; // 避免重复切换

        isShowingLyrics = showLyrics;

        View lyricsContainer = requireView().findViewById(R.id.lyricsContainer);
        View albumLyricsContainer = requireView().findViewById(R.id.albumLyricsContainer);

        if (showLyrics) {
            // 显示全屏歌词
            lyricsCard.setAlpha(0f);
            lyricsCard.setVisibility(View.VISIBLE);

            // 隐藏专辑图片和小型歌词预览
            albumLyricsContainer.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        albumLyricsContainer.setVisibility(View.GONE);
                        albumLyricsContainer.setAlpha(1f); // 重置透明度供下次使用
                    });

            // 创建歌词淡入动画
            lyricsCard.animate()
                    .alpha(1f)
                    .setDuration(300);

            // 隐藏小型歌词预览
            lyricsContainer.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        lyricsContainer.setVisibility(View.GONE);
                    });

            // 同步歌词到全屏歌词视图
            Lyrics lyrics = viewModel.getCurrentLyrics().getValue();
            lrcViewFullscreen.setLyrics(lyrics);

            // 同步当前播放进度
            Integer position = viewModel.getPlaybackPosition().getValue();
            if (position != null) {
                lrcViewFullscreen.updateTime(position);
            }

            // 暂停专辑旋转动画
            pauseAlbumRotation();
            // 显示Toast提示，双击歌词返回专辑视图
            Toast.makeText(requireContext(), "双击屏幕返回专辑视图，或点击右上角箭头返回", Toast.LENGTH_LONG).show();
            lastToastTime = System.currentTimeMillis();

            // 确保返回专辑按钮可见
            if (ivHintToAlbum != null) {
                ivHintToAlbum.setVisibility(View.VISIBLE);
                ivHintToAlbum.setAlpha(0.7f);
            }

            // 确保双击事件监听器正常工作
            ensureDoubleTapListenerSetup();
        } else {
            // 记录日志，帮助调试
            android.util.Log.d("PlaybackFragment", "切换回专辑视图");

            // 显示专辑图片和小型歌词预览
            albumLyricsContainer.setAlpha(0f);
            albumLyricsContainer.setVisibility(View.VISIBLE);

            // 创建歌词淡出动画
            lyricsCard.animate()
                    .alpha(0f)
                    .setDuration(300)
                    .withEndAction(() -> {
                        lyricsCard.setVisibility(View.GONE);
                        lyricsCard.setAlpha(1f); // 重置透明度供下次使用
                    });

            // 创建专辑淡入动画
            albumLyricsContainer.animate()
                    .alpha(1f)
                    .setDuration(300);

            // 显示小型歌词预览
            lyricsContainer.setVisibility(View.VISIBLE);
            lyricsContainer.setAlpha(0f);
            lyricsContainer.animate()
                    .alpha(1f)
                    .setDuration(300);
            // 如果正在播放则恢复专辑旋转动画
            if (viewModel.getPlayerState().getValue() == PlayerState.PLAYING) {
                startAlbumRotation();
            }

            // 隐藏返回专辑按钮
            if (ivHintToAlbum != null) {
                ivHintToAlbum.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 设置专辑封面旋转动画
     */
    private void setupRotationAnimation() {
        rotationAnimator = ObjectAnimator.ofFloat(ivAlbumArt, "rotation", 0f, 360f);
        rotationAnimator.setDuration(20000); // 20秒旋转一周
        rotationAnimator.setInterpolator(new LinearInterpolator());
        rotationAnimator.setRepeatCount(ValueAnimator.INFINITE);
    }

    /**
     * 观察ViewModel数据变化
     */
    private void observeViewModel() {
        // 观察当前歌曲
        viewModel.getCurrentSong().observe(getViewLifecycleOwner(), this::updateSongInfo);

        // 观察播放状态
        viewModel.getPlayerState().observe(getViewLifecycleOwner(), this::updatePlayState);

        // 观察播放进度
        viewModel.getPlaybackPosition().observe(getViewLifecycleOwner(), this::updatePlaybackPosition);

        // 观察歌曲总时长
        viewModel.getDuration().observe(getViewLifecycleOwner(), duration -> {
            if (duration != null) {
                tvTotalTime.setText(viewModel.formatTime(duration));
            }
        });

        // 观察播放模式
        viewModel.getPlayMode().observe(getViewLifecycleOwner(), this::updatePlayMode);

        // 观察歌词
        viewModel.getCurrentLyrics().observe(getViewLifecycleOwner(), this::updateLyrics);
    }

    /**
     * 更新歌曲信息
     */
    private void updateSongInfo(Song song) {
        if (song == null) {
            tvSongTitle.setText("");
            tvArtist.setText("");
            ivAlbumArt.setImageResource(R.drawable.default_album);
            return;
        }

        tvSongTitle.setText(song.getTitle());
        tvArtist.setText(song.getArtist());

        // 加载专辑封面，确保使用默认图片
        if (song.getAlbumArtUri() != null) {
            RequestOptions options = new RequestOptions()
                    .centerCrop()
                    .placeholder(R.drawable.default_album)
                    .error(R.drawable.default_album);
            if (song.isLocalAlbumArt()) {
                // 本地文件使用file:///路径加载，避免缓存问题
                Glide.with(this)
                        .load(song.getAlbumArtUri())
                        .apply(options)
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // 本地文件不使用磁盘缓存
                        .skipMemoryCache(false) // 但使用内存缓存提高性能
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model,
                                                        com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                        boolean isFirstResource) {
                                Log.e(TAG, "本地专辑封面加载失败: " + (e != null ? e.getMessage() : "未知错误"));
                                // 尝试回退到默认封面
                                ivAlbumArt.setImageResource(R.drawable.default_album);
                                return true;
                            }

                            @Override
                            public boolean onResourceReady(android.graphics.drawable.Drawable resource, Object model,
                                                           com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                           com.bumptech.glide.load.DataSource dataSource,
                                                           boolean isFirstResource) {
                                return false;
                            }
                        })
                        .into(ivAlbumArt);
            } else {                // 从媒体库加载
                Glide.with(this)
                        .load(song.getAlbumArtUri())
                        .apply(options)
                        .into(ivAlbumArt);
            }
        } else {
            // 没有专辑封面时使用默认图片
            ivAlbumArt.setImageResource(R.drawable.default_album);
        }

        // 如果正在显示歌词，切换回专辑视图
        if (isShowingLyrics) {
            toggleLyricsView(false);
        }
    }

    /**
     * 更新播放状态
     */
    private void updatePlayState(PlayerState state) {
        switch (state) {
            case PLAYING:
                ibPlayPause.setImageResource(R.drawable.ic_pause);
                startAlbumRotation();
                break;

            case PAUSED:
            case STOPPED:
            case COMPLETED:
                ibPlayPause.setImageResource(R.drawable.ic_play);
                pauseAlbumRotation();
                break;

            case ERROR:
                ibPlayPause.setImageResource(R.drawable.ic_play);
                pauseAlbumRotation();
                // 可以在这里添加错误处理逻辑
                break;
        }
    }

    /**
     * 更新播放进度
     */
    private void updatePlaybackPosition(Integer position) {
        if (position == null) return;

        // 如果用户正在拖动进度条，不更新UI
        if (isUserSeeking) return;

        // 更新进度条
        Integer duration = viewModel.getDuration().getValue();
        if (duration != null && duration > 0) {
            int progress = (int) ((position * 100L) / duration);
            seekBar.setProgress(progress);
        }

        // 更新时间文本
        tvCurrentTime.setText(viewModel.formatTime(position));

        // 更新小型歌词视图
        updateCurrentLyricText(position);

        // 如果正在显示歌词全屏视图，同步更新
        if (isShowingLyrics && lyricsCard.getVisibility() == View.VISIBLE) {
            lrcViewFullscreen.updateTime(position);
        }
    }

    /**
     * 更新播放模式
     */
    private void updatePlayMode(PlayMode mode) {
        if (mode == null) return;

        switch (mode) {
            case SEQUENCE:
                ibPlayMode.setImageResource(R.drawable.ic_repeat);
                break;

            case LOOP:
                ibPlayMode.setImageResource(R.drawable.ic_repeat_on);
                break;

            case SHUFFLE:
                ibPlayMode.setImageResource(R.drawable.ic_shuffle);
                break;

            case SINGLE_LOOP:
                ibPlayMode.setImageResource(R.drawable.ic_repeat_one);
                break;
        }
    }

    /**
     * 更新歌词
     */
    private void updateLyrics(Lyrics lyrics) {
        // 更新小视图歌词文本
        if (lyrics != null && viewModel.getPlaybackPosition().getValue() != null) {
            updateCurrentLyricText(viewModel.getPlaybackPosition().getValue());
        } else {
            tvCurrentLyric.setText("暂无歌词");
        }

        // 如果正在显示歌词全屏视图，同步更新
        if (isShowingLyrics && lyricsCard.getVisibility() == View.VISIBLE) {
            lrcViewFullscreen.setLyrics(lyrics);
        }
    }

    /**
     * 更新当前歌词文本
     * 从歌词对象中获取当前时间点应该显示的歌词行
     */
    private void updateCurrentLyricText(int position) {
        // 获取当前歌词
        Lyrics lyrics = viewModel.getCurrentLyrics().getValue();
        if (lyrics == null || lyrics.getLyricLines().isEmpty()) {
            tvCurrentLyric.setText("暂无歌词");
            return;
        }

        // 查找当前时间点对应的歌词行
        String currentLyric = lyrics.getLyricForTime(position);
        if (currentLyric != null && !currentLyric.isEmpty()) {
            tvCurrentLyric.setText(currentLyric);
        } else {
            // 如果当前没有匹配的歌词行，显示提示信息
            tvCurrentLyric.setText("正在播放...");
        }
    }

    /**
     * 开始专辑封面旋转动画
     */
    private void startAlbumRotation() {
        if (rotationAnimator != null) {
            // 保持当前旋转角度继续旋转
            if (rotationAnimator.isPaused()) {
                rotationAnimator.resume();
            } else if (!rotationAnimator.isStarted()) {
                rotationAnimator.start();
            }
        }
    }

    /**
     * 暂停专辑封面旋转动画
     */
    private void pauseAlbumRotation() {
        if (rotationAnimator != null && rotationAnimator.isStarted()) {
            rotationAnimator.pause();
        }
    }

    /**
     * Fragment恢复时调用
     */
    @Override
    public void onResume() {
        super.onResume();

        // 记录日志，帮助调试
        android.util.Log.d("PlaybackFragment", "onResume: isShowingLyrics=" + isShowingLyrics);

        // 确保歌词显示与当前状态同步
        if (isShowingLyrics) {
            // 同步当前播放进度到歌词
            Integer position = viewModel.getPlaybackPosition().getValue();
            if (position != null) {
                lrcViewFullscreen.updateTime(position);
            }

            // 确保歌词显示正确
            Lyrics lyrics = viewModel.getCurrentLyrics().getValue();
            if (lyrics != null && lrcViewFullscreen != null) {
                lrcViewFullscreen.setLyrics(lyrics);
            }            // 恢复双击提示（如果是用户第一次查看歌词）
            if (System.currentTimeMillis() - lastToastTime > 60000) { // 1分钟内不重复显示
                Toast.makeText(requireContext(), "双击屏幕返回专辑", Toast.LENGTH_LONG).show();
                lastToastTime = System.currentTimeMillis();
            }

            // 确保双击事件监听器正常工作
            ensureDoubleTapListenerSetup();

            // 设置备选的返回方法
            setupFallbackReturnMethod();
        } else {
            // 如果正在播放，确保专辑旋转动画继续
            if (viewModel.getPlayerState().getValue() == PlayerState.PLAYING) {
                startAlbumRotation();
            }
        }
    }

    /**
     * 切换播放模式
     * 在顺序播放、列表循环、随机播放和单曲循环之间切换
     */
    private void togglePlayMode() {
        // 调用ViewModel中的方法切换播放模式
        viewModel.togglePlayMode();

        // 获取当前播放模式
        PlayMode currentMode = viewModel.getPlayMode().getValue();

        // 显示提示信息
        if (currentMode != null) {
            String modeMessage;
            switch (currentMode) {
                case SEQUENCE:
                    modeMessage = getString(R.string.mode_sequence);
                    break;
                case LOOP:
                    modeMessage = getString(R.string.mode_loop);
                    break;
                case SHUFFLE:
                    modeMessage = getString(R.string.mode_shuffle);
                    break;
                case SINGLE_LOOP:
                    modeMessage = getString(R.string.mode_single_loop);
                    break;
                default:
                    modeMessage = getString(R.string.mode_sequence);
                    break;
            }

            // 显示Toast提示当前播放模式
            Toast.makeText(requireContext(), modeMessage, Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 确保双击事件监听器正确设置
     */
    private void ensureDoubleTapListenerSetup() {
        if (gestureDetector == null) {
            // 重新创建GestureDetector
            gestureDetector = new GestureDetector(requireContext(),
                    new GestureDetector.SimpleOnGestureListener() {
                        @Override
                        public boolean onDoubleTap(MotionEvent e) {
                            // 双击时返回专辑封面
                            if (isShowingLyrics) {
                                toggleLyricsView(false);
                                return true;
                            }
                            return false;
                        }

                        @Override
                        public boolean onSingleTapConfirmed(MotionEvent e) {
                            // 单击时不执行任何操作
                            return false;
                        }

                        @Override
                        public boolean onDown(MotionEvent e) {
                            // 必须返回true，表示关注该事件，否则其他事件不会被调用
                            return true;
                        }
                    });
        }

        // 重新设置歌词卡片的触摸监听器
        if (lyricsCard != null) {
            lyricsCard.setOnTouchListener((v, event) -> {                // 传递给GestureDetector处理
                boolean handled = gestureDetector.onTouchEvent(event);

                // 确保view执行点击事件
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.performClick();
                }

                // 总是返回true以拦截所有事件
                return true;
            });
        }
        // 同样为全屏歌词视图设置触摸监听器
        if (lrcViewFullscreen != null) {
            lrcViewFullscreen.setOnTouchListener((v, event) -> {
                // 让GestureDetector处理事件
                boolean handled = gestureDetector.onTouchEvent(event);

                // 确保view执行点击事件
                if (event.getAction() == MotionEvent.ACTION_UP) {
                    v.performClick();
                }

                // 返回true表示已处理
                return true;
            });
        }
        // 为返回专辑提示按钮设置点击监听器
        if (ivHintToAlbum != null) {
            android.util.Log.d("PlaybackFragment", "为返回专辑按钮设置监听器");
            ivHintToAlbum.setOnClickListener(v -> {
                android.util.Log.d("PlaybackFragment", "点击返回专辑提示按钮");
                if (isShowingLyrics) {
                    toggleLyricsView(false);
                }
            });

            // 确保按钮在歌词模式下可见
            if (isShowingLyrics) {
                ivHintToAlbum.setVisibility(View.VISIBLE);
                ivHintToAlbum.setAlpha(0.7f);
            } else {
                ivHintToAlbum.setVisibility(View.GONE);
            }
        } else {
            android.util.Log.e("PlaybackFragment", "ensureDoubleTapListenerSetup: 返回专辑按钮为null");
        }
    }

    /**
     * 设置备选的返回专辑视图方法
     */
    private void setupFallbackReturnMethod() {
        android.util.Log.d("PlaybackFragment", "设置备选返回专辑视图方法");

        // 为歌词卡片设置点击监听器
        if (lyricsCard != null) {
            lyricsCard.setOnClickListener(v -> {
                // 计算距离上次点击的时间间隔
                long clickTime = System.currentTimeMillis();
                if (clickTime - lastClickTime < 400) { // 400ms内的两次点击视为双击
                    android.util.Log.d("PlaybackFragment", "检测到连续点击，返回专辑视图");
                    if (isShowingLyrics) {
                        toggleLyricsView(false);
                    }
                }
                lastClickTime = clickTime;
            });
        }

        // 为全屏歌词视图添加长按返回功能作为备选方案
        if (lrcViewFullscreen != null) {
            lrcViewFullscreen.setOnLongClickListener(v -> {
                android.util.Log.d("PlaybackFragment", "检测到长按，返回专辑视图");
                if (isShowingLyrics) {
                    Toast.makeText(requireContext(), "长按返回专辑视图", Toast.LENGTH_SHORT).show();
                    toggleLyricsView(false);
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * 获取ViewModel实例
     * 提供给BottomSheetDialog使用
     *
     * @return PlayerViewModel
     */
    public PlayerViewModel getViewModel() {
        return viewModel;
    }

    /**
     * 实现PlaylistDialogCallback接口
     * 处理播放列表对话框中的事件
     */
    @Override
    public void onPlaylistItemSelected() {
        // 当从播放队列中选择歌曲时更新界面
        // 不需要特殊处理，因为我们已经通过LiveData观察了数据变化，UI会自动更新
    }

    /**
     * Fragment销毁时的清理
     */
    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // 停止动画
        if (rotationAnimator != null) {
            rotationAnimator.cancel();
        }
    }
}
