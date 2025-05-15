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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
    private ConstraintLayout miniPlayerContainer;
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
                return true;
            } else if (itemId == R.id.navigation_playlist) {
                viewPager.setCurrentItem(1, false);
                currentPagePosition = 1;
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
                } else if (position == 1) {
                    bottomNav.setSelectedItemId(R.id.navigation_playlist);
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
            viewPager.setCurrentItem(0, false);
            bottomNav.setSelectedItemId(R.id.navigation_player);
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
            miniPlayerContainer.setVisibility(View.VISIBLE);
            tvMiniTitle.setText(song.getTitle());
            tvMiniArtist.setText(song.getArtist());
            
            // 加载专辑封面
            if (song.getAlbumArtUri() != null) {
                Glide.with(this)
                        .load(song.getAlbumArtUri())
                        .placeholder(R.drawable.default_album)
                        .error(R.drawable.default_album)
                        .into(ivMiniAlbumArt);
            } else {
                ivMiniAlbumArt.setImageResource(R.drawable.default_album);
            }
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
     */
    private void checkAndRequestPermissions() {
        String[] permissions;
        
        // 根据Android版本确定需要请求的权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13及以上需要READ_MEDIA_AUDIO权限
            permissions = new String[]{
                    Manifest.permission.READ_MEDIA_AUDIO
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11及以上
            permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE
            };
        } else {
            // Android 10及以下
            permissions = new String[]{
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            };
        }
        
        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }
        
        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_PERMISSION_CODE);
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
                Toast.makeText(this, R.string.storage_permission_message, Toast.LENGTH_SHORT).show();
            }
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
     * 活动销毁时调用
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
