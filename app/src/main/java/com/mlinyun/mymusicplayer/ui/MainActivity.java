package com.mlinyun.mymusicplayer.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.adapter.MainPagerAdapter;
import com.mlinyun.mymusicplayer.model.Lyrics;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.player.PlayerState;
import com.mlinyun.mymusicplayer.viewmodel.PlayerViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 主活动类
 * 应用入口，负责导航和底部迷你播放器
 */
public class MainActivity extends AppCompatActivity {

    // 标签常量
    private static final String TAG = "MainActivity";

    // 当前页面位置
    private int currentPagePosition = 0;

    // 权限请求码
    private static final int REQUEST_PERMISSION_CODE = 100;

    // UI组件
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private CardView miniPlayerContainer;
    private ImageView ivMiniAlbumArt;
    private TextView tvMiniTitle;
    private TextView tvMiniArtist;
    private TextView tvMiniLyric; // 添加单行歌词显示
    private ImageButton ibMiniPlayPause;
    private ImageButton ibMiniNext;
    private android.widget.ProgressBar pbMiniProgress; // 添加迷你进度条控件

    // ViewModel
    private PlayerViewModel viewModel;

    /**
     * 活动创建时调用
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 日志过滤，减少系统级警告（如ashmem废弃警告）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            System.setProperty("log.tag.AshmemMemory", "SILENT");
        }

        // 初始化UI组件
        initViews();

        // 获取ViewModel
        viewModel = new ViewModelProvider(this).get(PlayerViewModel.class);

        // 设置ViewPager和底部导航
        setupViewPagerAndNavigation();

        // 设置迷你播放器
        setupMiniPlayer();

        // 观察ViewModel数据变化
        observeViewModel();

        // 检查并请求权限
        checkAndRequestPermissions();
    }

    /**
     * 初始化UI组件
     */
    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        bottomNav = findViewById(R.id.bottomNavigationView);
        miniPlayerContainer = findViewById(R.id.miniPlayerContainer);
        ivMiniAlbumArt = findViewById(R.id.ivMiniAlbumArt);
        tvMiniTitle = findViewById(R.id.tvMiniTitle);
        tvMiniArtist = findViewById(R.id.tvMiniArtist);
        tvMiniLyric = findViewById(R.id.tvMiniLyric); // 初始化单行歌词TextView
        ibMiniPlayPause = findViewById(R.id.ibMiniPlayPause);
        ibMiniNext = findViewById(R.id.ibMiniNext);
        pbMiniProgress = findViewById(R.id.pbMiniProgress); // 初始化迷你进度条
    }

    /**
     * 设置ViewPager和底部导航
     */
    private void setupViewPagerAndNavigation() {
        // 创建适配器
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        // 禁用ViewPager2的滑动
        viewPager.setUserInputEnabled(false);

        // 设置底部导航监听器
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.navigation_player) {
                viewPager.setCurrentItem(0, false);
                currentPagePosition = 0;
                // 在播放页面隐藏迷你播放器
                miniPlayerContainer.setVisibility(View.GONE);
                return true;
            } else if (itemId == R.id.navigation_playlist) {
                viewPager.setCurrentItem(1, false);
                currentPagePosition = 1;
                // 如果有歌曲正在播放，则在播放列表页面显示迷你播放器
                Song currentSong = viewModel != null ? viewModel.getCurrentSong().getValue() : null;
                if (currentSong != null) {
                    miniPlayerContainer.setVisibility(View.VISIBLE);
                    // 确保更新迷你播放器内容
                    updateMiniPlayer(currentSong);
                }
                return true;
            }
            return false;
        });

        // 设置ViewPager页面切换监听器
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                currentPagePosition = position;
                if (position == 0) {
                    bottomNav.setSelectedItemId(R.id.navigation_player);
                    miniPlayerContainer.setVisibility(View.GONE); // 在播放页面隐藏迷你播放器
                } else if (position == 1) {
                    bottomNav.setSelectedItemId(R.id.navigation_playlist);
                    // 如果有歌曲正在播放，则在播放列表页面显示迷你播放器
                    Song currentSong = viewModel != null ? viewModel.getCurrentSong().getValue() : null;
                    if (currentSong != null) {
                        miniPlayerContainer.setVisibility(View.VISIBLE);
                        // 确保更新迷你播放器内容
                        updateMiniPlayer(currentSong);
                    }
                }
            }
        });
    }

    /**
     * 设置迷你播放器
     */
    private void setupMiniPlayer() {
        // 迷你播放器点击事件，切换到播放界面
        miniPlayerContainer.setOnClickListener(v -> {
            navigateToPlayback();
        });

        // 播放/暂停按钮点击事件
        ibMiniPlayPause.setOnClickListener(v -> {
            viewModel.togglePlayPause();
        });

        // 下一首按钮点击事件
        ibMiniNext.setOnClickListener(v -> {
            viewModel.playNext();
        });
    }

    /**
     * 导航到播放页面
     * 公开此方法，使Fragment可以调用
     */
    public void navigateToPlayback() {
        viewPager.setCurrentItem(0, false);
        bottomNav.setSelectedItemId(R.id.navigation_player);
        // 在播放页面隐藏迷你播放器
        miniPlayerContainer.setVisibility(View.GONE);
    }

    /**
     * 观察ViewModel数据变化
     */
    private void observeViewModel() {
        // 观察当前歌曲
        viewModel.getCurrentSong().observe(this, this::updateMiniPlayer);

        // 观察播放状态
        viewModel.getPlayerState().observe(this, this::updatePlayState);

        // 观察播放进度
        viewModel.getPlaybackPosition().observe(this, this::updatePlaybackProgress);

        // 观察总时长
        viewModel.getDuration().observe(this, duration -> {
            // 当获取到总时长时，更新进度条的最大值
            if (duration != null && duration > 0) {
                pbMiniProgress.setMax(100);
            }
        });
    }

    /**
     * 更新迷你播放器
     */
    private void updateMiniPlayer(Song song) {
        if (song != null) {
            // 设置迷你播放器内容，无论显示与否都更新
            tvMiniTitle.setText(song.getTitle());
            tvMiniArtist.setText(song.getArtist());

            // 根据播放状态设置显示内容
            PlayerState state = viewModel.getPlayerState().getValue();
            if (state == PlayerState.PLAYING) {
                tvMiniTitle.setVisibility(View.GONE);
                tvMiniArtist.setVisibility(View.GONE);
                tvMiniLyric.setVisibility(View.VISIBLE);

                // 获取当前播放位置对应的歌词
                Integer position = viewModel.getPlaybackPosition().getValue();
                if (position != null) {
                    updateMiniLyric(position);
                }
            } else {
                tvMiniTitle.setVisibility(View.VISIBLE);
                tvMiniArtist.setVisibility(View.VISIBLE);
                tvMiniLyric.setVisibility(View.GONE);
            }

            // 只在播放列表页面显示迷你播放器
            if (currentPagePosition == 1) {
                miniPlayerContainer.setVisibility(View.VISIBLE);
            }

            // 加载专辑封面，确保使用默认图片
            if (song.getAlbumArtUri() != null) {
                RequestOptions options = new RequestOptions()
                        .centerCrop()
                        .placeholder(R.drawable.default_album)
                        .error(R.drawable.default_album);

                if (song.isLocalAlbumArt()) {
                    // 本地文件使用file:///路径加载
                    Glide.with(this)
                            .load(song.getAlbumArtUri())
                            .apply(options)
                            .diskCacheStrategy(DiskCacheStrategy.NONE) // 不缓存到磁盘
                            .skipMemoryCache(false) // 但使用内存缓存
                            .into(ivMiniAlbumArt);
                } else {
                    // 媒体库封面
                    Glide.with(this)
                            .load(song.getAlbumArtUri())
                            .apply(options)
                            .into(ivMiniAlbumArt);
                }
            } else {
                // 没有专辑封面时使用默认图片
                ivMiniAlbumArt.setImageResource(R.drawable.default_album);
            }

            // 使迷你播放器封面可点击，点击后切换到播放界面
            ivMiniAlbumArt.setOnClickListener(v -> {
                navigateToPlayback();
            });
        } else {
            miniPlayerContainer.setVisibility(View.GONE);
        }
    }

    /**
     * 更新播放状态
     */
    private void updatePlayState(PlayerState state) {
        if (state == PlayerState.PLAYING) {
            ibMiniPlayPause.setImageResource(R.drawable.ic_pause);

            // 播放时显示歌词，隐藏歌曲信息
            tvMiniTitle.setVisibility(View.GONE);
            tvMiniArtist.setVisibility(View.GONE);
            tvMiniLyric.setVisibility(View.VISIBLE);

            // 获取当前播放位置对应的歌词
            Integer position = viewModel.getPlaybackPosition().getValue();
            if (position != null) {
                updateMiniLyric(position);
            }
        } else {
            ibMiniPlayPause.setImageResource(R.drawable.ic_play);

            // 暂停时显示歌曲信息，隐藏歌词
            tvMiniTitle.setVisibility(View.VISIBLE);
            tvMiniArtist.setVisibility(View.VISIBLE);
            tvMiniLyric.setVisibility(View.GONE);
        }
    }

    /**
     * 更新迷你播放器的播放进度
     */
    private void updatePlaybackProgress(Integer position) {
        if (position == null) return;

        // 获取总时长
        Integer duration = viewModel.getDuration().getValue();
        if (duration != null && duration > 0) {
            // 计算进度百分比
            int progress = (int) ((position * 100L) / duration);
            pbMiniProgress.setProgress(progress);

            // 同时更新歌词
            updateMiniLyric(position);
        }
    }

    /**
     * 更新迷你播放器的歌词
     */
    private void updateMiniLyric(int position) {
        // 只有在播放状态时才更新歌词
        if (viewModel.getPlayerState().getValue() != PlayerState.PLAYING) {
            return;
        }

        // 获取当前歌词
        Lyrics lyrics = viewModel.getCurrentLyrics().getValue();
        if (lyrics == null || lyrics.getLyricLines().isEmpty()) {
            tvMiniLyric.setText("暂无歌词");
            return;
        }

        // 查找当前时间点对应的歌词行
        String currentLyric = lyrics.getLyricForTime(position);
        if (currentLyric != null && !currentLyric.isEmpty()) {
            tvMiniLyric.setText(currentLyric);
        } else {
            // 如果当前没有匹配的歌词行，显示提示信息
            tvMiniLyric.setText("正在播放...");
        }
    }

    /**
     * 检查并请求必要的权限
     * 公开此方法，使Fragment可以调用
     */
    public void checkAndRequestPermissions() {
        // 需要请求的权限列表
        List<String> permissionsNeeded = new ArrayList<>();

        // 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上版本使用细化的媒体权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_IMAGES);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11和12使用READ_MEDIA_AUDIO权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_MEDIA_AUDIO);
            }
        } else {
            // Android 10及以下版本使用READ_EXTERNAL_STORAGE权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }

        // 如果需要请求权限
        if (!permissionsNeeded.isEmpty()) {
            String[] permissions = permissionsNeeded.toArray(new String[0]);

            // 检查是否需要显示权限解释
            boolean shouldShowRationale = false;
            for (String permission : permissions) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                    shouldShowRationale = true;
                    break;
                }
            }

            if (shouldShowRationale) {
                // 显示权限解释对话框
                showPermissionExplanationDialog();
            } else {
                // 直接请求权限
                ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CODE);
            }
        } else {
            // 已有权限，初始化音乐扫描
            initMusicScan();
        }
    }

    /**
     * 权限请求结果处理
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQUEST_PERMISSION_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (!allGranted) {
                // 检查是否勾选了"不再询问"
                boolean showRationale = false;
                for (String permission : permissions) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permission)) {
                        showRationale = true;
                        break;
                    }
                }

                if (showRationale) {
                    // 用户拒绝了权限，但没有勾选"不再询问"，显示对话框解释原因
                    showPermissionExplanationDialog();
                } else {
                    // 用户拒绝了权限，且勾选了"不再询问"，显示对话框引导用户去设置中开启权限
                    showGoToSettingsDialog();
                }
            } else {
                // 权限已授予，初始化音乐扫描
                initMusicScan();
            }
        }
    }

    /**
     * 显示权限解释对话框
     */
    private void showPermissionExplanationDialog() {
        // 使用自定义布局的对话框
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_permission_request, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // 设置授权按钮点击事件
        Button btnGrantPermission = dialogView.findViewById(R.id.btnGrantPermission);
        btnGrantPermission.setOnClickListener(v -> {
            // 再次请求权限
            String[] permissions = getRequiredPermissions();
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CODE);
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 显示前往设置的对话框
     */
    private void showGoToSettingsDialog() {
        // 使用自定义布局的对话框
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_permission_request, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        // 设置按钮文本和点击事件
        Button btnGrantPermission = dialogView.findViewById(R.id.btnGrantPermission);
        btnGrantPermission.setText(R.string.permission_button_settings);
        btnGrantPermission.setOnClickListener(v -> {
            // 跳转到应用设置页面
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
            dialog.dismiss();
        });

        dialog.show();
    }

    /**
     * 权限获取后初始化音乐扫描
     */
    private void initMusicScan() {
        // 权限已授予，初始化音乐扫描
        if (viewModel != null) {
            // 先从缓存中获取歌曲
            viewModel.refreshSongsList();

            // 如果是首次使用或需要更新，扫描媒体库获取最新歌曲
            viewModel.scanMusic();
        }
    }

    /**
     * 导航到播放列表页面
     */
    public void navigateToPlaylist() {
        viewPager.setCurrentItem(1, false);
        bottomNav.setSelectedItemId(R.id.navigation_playlist);
    }

    /**
     * 导航到播放页面
     */
    public void navigateToPlayer() {
        viewPager.setCurrentItem(0, false);
        bottomNav.setSelectedItemId(R.id.navigation_player);
    }

    /**
     * 活动恢复时调用
     */
    @Override
    protected void onResume() {
        super.onResume();

        // 从系统设置返回时重新检查权限
        checkAndRequestPermissions();
    }

    /**
     * 活动销毁时调用
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * 获取当前Android版本所需的权限
     */
    private String[] getRequiredPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上需要READ_MEDIA_AUDIO权限
            return new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO,
                    Manifest.permission.READ_MEDIA_IMAGES,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上
            return new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            // Android 10及以下
            return new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
    }
}
