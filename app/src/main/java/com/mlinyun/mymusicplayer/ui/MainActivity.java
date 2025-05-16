package com.mlinyun.mymusicplayer.ui;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.adapter.MainPagerAdapter;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.player.PlayerState;
import com.mlinyun.mymusicplayer.service.MusicPlayerService;
import com.mlinyun.mymusicplayer.viewmodel.PlayerViewModel;

/**
 * 主活动类
 * 应用入口，负责导航和底部迷你播放器
 */
public class MainActivity extends AppCompatActivity {

    // 权限请求码
    private static final int REQUEST_PERMISSION_CODE = 100;
    // UI组件
    private ViewPager2 viewPager;
    private BottomNavigationView bottomNav;
    private androidx.cardview.widget.CardView miniPlayerContainer;
    private ImageView ivMiniAlbumArt;
    private TextView tvMiniTitle;
    private TextView tvMiniArtist;
    private ImageButton ibMiniPlayPause;
    private ImageButton ibMiniNext;

    // ViewModel
    private PlayerViewModel viewModel;

    // 当前页面位置
    private int currentPagePosition = 0;

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
        ibMiniPlayPause = findViewById(R.id.ibMiniPlayPause);
        ibMiniNext = findViewById(R.id.ibMiniNext);
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
        });// 设置ViewPager页面切换监听器
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
    }

    /**
     * 更新迷你播放器
     */
    private void updateMiniPlayer(Song song) {
        if (song != null) {
            // 设置迷你播放器内容，无论显示与否都更新
            tvMiniTitle.setText(song.getTitle());
            tvMiniArtist.setText(song.getArtist());

            // 只在播放列表页面显示迷你播放器
            if (currentPagePosition == 1) {
                miniPlayerContainer.setVisibility(View.VISIBLE);
            }

            // 加载专辑封面，确保使用默认图片
            if (song.getAlbumArtUri() != null) {
                Glide.with(this)
                        .load(song.getAlbumArtUri())
                        .placeholder(R.drawable.default_album)
                        .error(R.drawable.default_album)
                        .centerCrop()
                        .into(ivMiniAlbumArt);
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
        } else {
            ibMiniPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    /**
     * 检查并请求权限
     * 公开此方法，使Fragment可以调用
     */
    public void checkAndRequestPermissions() {
        String[] permissions = getRequiredPermissions();

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (allPermissionsGranted) {
            // 已有权限，初始化音乐扫描
            initMusicScan();
        } else {
            // 请求权限前，先检查是否需要显示权限解释
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

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull android.view.MenuItem item) {
        if (item.getItemId() == R.id.action_request_permission) {
            // 请求权限
            checkAndRequestPermissions();
            return true;
        }
        return super.onOptionsItemSelected(item);
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
                    Manifest.permission.READ_MEDIA_AUDIO
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
