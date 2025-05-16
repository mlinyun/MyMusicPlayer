package com.mlinyun.mymusicplayer.ui;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
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
public class PlaybackFragment extends Fragment {

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
    private LrcView lrcView;    // 新增UI组件变量
    private View albumContainer;
    private View lyricsCard;
    private LrcView lrcViewFullscreen;
    private boolean isShowingLyrics = false;

    // ViewModel
    private PlayerViewModel viewModel;

    // 专辑封面旋转动画
    private ObjectAnimator rotationAnimator;

    // 是否用户正在拖动进度条
    private boolean isUserSeeking = false;

    /**
     * 创建Fragment视图
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
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
        observeViewModel();
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
        lrcView = view.findViewById(R.id.lrcView);

        // 初始化新增的UI组件
        albumContainer = view.findViewById(R.id.albumContainer);
        lyricsCard = view.findViewById(R.id.lyricsCard);
        lrcViewFullscreen = view.findViewById(R.id.lrcViewFullscreen);

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
        // 播放/暂停按钮
        ibPlayPause.setOnClickListener(v -> viewModel.togglePlayPause());

        // 上一首按钮
        ibPrevious.setOnClickListener(v -> viewModel.playPrevious());

        // 下一首按钮
        ibNext.setOnClickListener(v -> viewModel.playNext());

        // 播放模式按钮
        ibPlayMode.setOnClickListener(v -> viewModel.togglePlayMode());
        // 播放列表按钮
        ibPlaylist.setOnClickListener(v -> {
            // 切换到播放列表页面
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).navigateToPlaylist();
            }
        });

        // 专辑封面点击显示歌词
        albumContainer.setOnClickListener(v -> toggleLyricsView(true));

        // 歌词视图点击返回专辑封面
        lyricsCard.setOnClickListener(v -> toggleLyricsView(false));

        // 进度条监听
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
        });

        // 歌词点击监听
        lrcView.setLrcViewListener(new LrcView.LrcViewListener() {
            @Override
            public void onLrcViewClick() {
                // 可以在这里添加点击歌词的处理逻辑
            }

            @Override
            public void onLrcLineTap(int line, com.mlinyun.mymusicplayer.model.LyricLine lrcLine) {
                // 点击歌词行时跳转到对应时间点
                viewModel.seekTo((int) lrcLine.getTimeMs());
            }
        });
    }

    /**
     * 切换歌词/专辑封面显示
     *
     * @param showLyrics 是否显示歌词
     */
    private void toggleLyricsView(boolean showLyrics) {
        if (isShowingLyrics == showLyrics) return; // 避免重复切换

        isShowingLyrics = showLyrics;

        View lyricsContainer = requireView().findViewById(R.id.lyricsContainer);

        if (showLyrics) {
            // 设置歌词卡片初始状态
            lyricsCard.setAlpha(0f);
            lyricsCard.setVisibility(View.VISIBLE);

            // 创建专辑淡出动画
            albumContainer.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                albumContainer.setVisibility(View.GONE);
                albumContainer.setAlpha(1f); // 重置透明度供下次使用
            });

            // 创建歌词淡入动画
            lyricsCard.animate().alpha(1f).setDuration(300);

            // 隐藏小型歌词预览
            lyricsContainer.animate().alpha(0f).setDuration(300).withEndAction(() -> {
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
        } else {
            // 设置专辑容器初始状态
            albumContainer.setAlpha(0f);
            albumContainer.setVisibility(View.VISIBLE);

            // 创建歌词淡出动画
            lyricsCard.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                lyricsCard.setVisibility(View.GONE);
                lyricsCard.setAlpha(1f); // 重置透明度供下次使用
            });

            // 创建专辑淡入动画
            albumContainer.animate().alpha(1f).setDuration(300);

            // 显示小型歌词预览
            lyricsContainer.setVisibility(View.VISIBLE);
            lyricsContainer.setAlpha(0f);
            lyricsContainer.animate().alpha(1f).setDuration(300);

            // 如果正在播放则恢复专辑旋转动画
            if (viewModel.getPlayerState().getValue() == PlayerState.PLAYING) {
                startAlbumRotation();
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
            Glide.with(this).load(song.getAlbumArtUri()).apply(RequestOptions.centerCropTransform()).placeholder(R.drawable.default_album).error(R.drawable.default_album).into(ivAlbumArt);
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
        lrcView.updateTime(position);

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
        // 更新小视图歌词
        lrcView.setLyrics(lyrics);

        // 如果正在显示歌词全屏视图，同步更新
        if (isShowingLyrics && lyricsCard.getVisibility() == View.VISIBLE) {
            lrcViewFullscreen.setLyrics(lyrics);
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
