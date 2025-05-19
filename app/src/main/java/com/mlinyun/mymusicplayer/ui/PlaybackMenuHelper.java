package com.mlinyun.mymusicplayer.ui;

import android.content.Context;
import android.widget.Toast;

import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.viewmodel.PlayerViewModel;

/**
 * 这是一个辅助类，用于在播放页面显示菜单选项，允许用户将当前播放的搜索结果添加到播放列表
 */
public class PlaybackMenuHelper {

    /**
     * 将当前正在播放的搜索结果添加到播放列表
     *
     * @param context   上下文
     * @param viewModel 播放器ViewModel
     * @return 是否成功添加（如果当前歌曲不是搜索结果或已在播放列表中，则返回false）
     */
    public static boolean addCurrentSearchResultToPlaylist(Context context, PlayerViewModel viewModel) {
        // 获取当前正在播放的歌曲
        Song currentSong = viewModel.getCurrentSong().getValue();

        if (currentSong == null) {
            return false;
        }

        // 检查是否为搜索结果
        if (!currentSong.isSearchResult()) {
            Toast.makeText(context, "当前歌曲已在播放列表中", Toast.LENGTH_SHORT).show();
            return false;
        }

        // 创建非搜索结果版本的歌曲
        Song playlistSong = new Song(
                currentSong.getId(),
                currentSong.getTitle(),
                currentSong.getArtist(),
                currentSong.getAlbum(),
                currentSong.getDuration(),
                currentSong.getPath(),
                currentSong.getAlbumArtUri()
        );
        playlistSong.setSearchResult(false);
        // 添加到播放列表
        boolean added = viewModel.addSong(playlistSong);

        // 如果添加成功
        if (added) {
            // 更新当前播放的歌曲，将其标记为非搜索结果
            currentSong.setSearchResult(false);

            // 显示添加成功的提示
            Toast.makeText(context,
                    "已添加: " + currentSong.getTitle(),
                    Toast.LENGTH_SHORT).show();
            return true;
        } else {
            Toast.makeText(context, "无法添加到播放列表", Toast.LENGTH_SHORT).show();
            return false;
        }
    }
}
