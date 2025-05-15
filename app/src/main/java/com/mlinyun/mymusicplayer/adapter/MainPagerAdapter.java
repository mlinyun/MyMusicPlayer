package com.mlinyun.mymusicplayer.adapter;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import com.mlinyun.mymusicplayer.ui.PlaybackFragment;
import com.mlinyun.mymusicplayer.ui.PlaylistFragment;

/**
 * 主页面ViewPager适配器
 * 用于在播放页面和播放列表页面之间切换
 */
public class MainPagerAdapter extends FragmentStateAdapter {

    /**
     * 页面总数
     */
    private static final int FRAGMENT_COUNT = 2;

    /**
     * 页面索引常量
     */
    public static final int FRAGMENT_PLAYBACK = 0;
    public static final int FRAGMENT_PLAYLIST = 1;

    /**
     * 构造函数
     *
     * @param fragmentActivity 宿主活动
     */
    public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
        super(fragmentActivity);
    }

    /**
     * 创建对应位置的Fragment
     *
     * @param position 位置索引
     * @return 对应的Fragment
     */
    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case FRAGMENT_PLAYBACK:
                return new PlaybackFragment();
            case FRAGMENT_PLAYLIST:
                return new PlaylistFragment();
            default:
                throw new IllegalArgumentException("Invalid fragment position: " + position);
        }
    }

    /**
     * 获取页面总数
     *
     * @return 页面总数
     */
    @Override
    public int getItemCount() {
        return FRAGMENT_COUNT;
    }
}
