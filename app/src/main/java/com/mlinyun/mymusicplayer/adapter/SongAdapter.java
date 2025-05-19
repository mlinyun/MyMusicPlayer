package com.mlinyun.mymusicplayer.adapter;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.model.Song;

import java.util.ArrayList;
import java.util.List;

/**
 * 歌曲列表适配器
 * 用于在RecyclerView中显示歌曲条目
 */
public class SongAdapter extends RecyclerView.Adapter<SongAdapter.SongViewHolder> {

    // 歌曲列表
    private List<Song> songs;

    // 上下文
    private final Context context;

    // 当前播放索引
    private int currentPlayingPosition = -1;

    // 点击监听器
    private final OnSongClickListener clickListener;

    /**
     * 构造函数
     *
     * @param context       上下文
     * @param clickListener 歌曲点击监听器
     */
    public SongAdapter(Context context, OnSongClickListener clickListener) {
        this.context = context;
        this.clickListener = clickListener;
        this.songs = new ArrayList<>();
    }

    /**
     * 创建ViewHolder
     */
    @NonNull
    @Override
    public SongViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_song, parent, false);
        return new SongViewHolder(view);
    }

    /**
     * 绑定ViewHolder数据
     */
    @Override
    public void onBindViewHolder(@NonNull SongViewHolder holder, @SuppressLint("RecyclerView") int position) {
        Song song = songs.get(position);

        // 设置歌曲信息
        holder.titleTextView.setText(song.getTitle());
        holder.artistTextView.setText(song.getArtist());
        holder.durationTextView.setText(formatDuration(song.getDuration()));

        // 检查是否为搜索结果并且已经在播放列表中
        boolean inPlaylist = false;
        if (song.isSearchResult()) {
            // 检查此搜索结果是否已在播放列表中
            for (Song existingSong : songs) {
                if (!existingSong.isSearchResult() && existingSong.getId().equals(song.getId())) {
                    inPlaylist = true;
                    break;
                }
            }
        }        // 显示或隐藏搜索结果标识
        if (song.isSearchResult()) {
            holder.searchBadgeView.setVisibility(View.VISIBLE);

            // 如果已在播放列表中，使用不同的背景和图标表示
            if (inPlaylist) {
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.colorInPlaylist));
                holder.addToPlaylistButton.setImageResource(R.drawable.ic_check);
            } else {
                // 使用不同的背景颜色来突出显示搜索结果
                holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.colorSearchResult));
                holder.addToPlaylistButton.setImageResource(R.drawable.ic_add);
            }

            // 显示添加到播放列表按钮
            holder.addToPlaylistButton.setVisibility(View.VISIBLE);
            // 设置添加按钮点击监听器
            boolean finalInPlaylist = inPlaylist;
            holder.addToPlaylistButton.setOnClickListener(v -> {
                if (clickListener != null && !finalInPlaylist) {
                    clickListener.onAddToPlaylistClick(position, song);
                }
            });
        } else {
            holder.searchBadgeView.setVisibility(View.GONE);
            // 非搜索结果项使用透明背景
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            // 隐藏添加到播放列表按钮
            holder.addToPlaylistButton.setVisibility(View.GONE);
        }
        // 加载专辑封面
        if (song.getAlbumArtUri() != null) {
            // 根据URI类型使用不同的加载方式
            RequestOptions options = new RequestOptions()
                    .circleCropTransform()
                    .placeholder(R.drawable.default_album)
                    .error(R.drawable.default_album);
            if (song.isLocalAlbumArt()) {
                // 本地文件使用file:///路径加载
                Glide.with(context)
                        .load(song.getAlbumArtUri())
                        .apply(options)
                        .diskCacheStrategy(DiskCacheStrategy.NONE) // 本地文件不缓存
                        .skipMemoryCache(false) // 但可以使用内存缓存
                        .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                            @Override
                            public boolean onLoadFailed(@Nullable com.bumptech.glide.load.engine.GlideException e, Object model,
                                                        com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                        boolean isFirstResource) {
                                Log.e("SongAdapter", "本地专辑封面加载失败: " + e.getMessage());
                                // 加载失败时，显示默认封面
                                holder.albumImageView.setImageResource(R.drawable.default_album);
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
                        .into(holder.albumImageView);
            } else {
                // 系统媒体库的封面通过content://加载
                Glide.with(context)
                        .load(song.getAlbumArtUri())
                        .apply(options)
                        .into(holder.albumImageView);
            }
        } else {
            holder.albumImageView.setImageResource(R.drawable.default_album);
        }        // 设置当前播放歌曲的高亮效果 - 优先级高于搜索结果
        if (position == currentPlayingPosition) {
            // 设置当前播放歌曲的背景
            holder.itemView.setBackgroundColor(ContextCompat.getColor(context, R.color.colorHighlight));
            holder.nowPlayingIndicator.setVisibility(View.VISIBLE);
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.colorAccent));
            // 当前播放的歌曲如果是搜索结果，仍然显示搜索徽章
            if (song.isSearchResult()) {
                holder.searchBadgeView.setVisibility(View.VISIBLE);
                holder.addToPlaylistButton.setVisibility(View.VISIBLE);

                if (inPlaylist) {
                    holder.addToPlaylistButton.setImageResource(R.drawable.ic_check);
                }
            } else {
                holder.addToPlaylistButton.setVisibility(View.GONE);
            }
        } else {
            // 非当前播放歌曲的背景
            if (!song.isSearchResult()) {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }
            holder.nowPlayingIndicator.setVisibility(View.GONE);
            holder.titleTextView.setTextColor(ContextCompat.getColor(context, R.color.colorText));
        }

        // 设置点击监听器
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSongClick(position, song);
            }
        });

        // 设置长按监听器
        holder.itemView.setOnLongClickListener(v -> {
            if (clickListener != null) {
                clickListener.onSongLongClick(position, song);
                return true;
            }
            return false;
        });
    }

    /**
     * 获取列表大小
     */
    @Override
    public int getItemCount() {
        return songs.size();
    }

    /**
     * 更新歌曲列表
     *
     * @param newSongs 新的歌曲列表
     */
    public void updateSongs(List<Song> newSongs) {
        this.songs = new ArrayList<>(newSongs);
        notifyDataSetChanged();
    }

    /**
     * 设置当前播放位置
     *
     * @param position 当前播放位置
     */
    public void setCurrentPlayingPosition(int position) {
        int oldPosition = currentPlayingPosition;
        currentPlayingPosition = position;

        // 更新两个位置的视图
        if (oldPosition >= 0) {
            notifyItemChanged(oldPosition);
        }
        if (position >= 0) {
            notifyItemChanged(position);
        }
    }

    /**
     * 获取当前播放位置
     *
     * @return 当前播放位置
     */
    public int getCurrentPlayingPosition() {
        return currentPlayingPosition;
    }

    /**
     * 添加单首歌曲
     *
     * @param song 要添加的歌曲
     */
    public void addSong(Song song) {
        songs.add(song);
        notifyItemInserted(songs.size() - 1);
    }

    /**
     * 移除歌曲
     *
     * @param position 要移除的位置
     */
    public void removeSong(int position) {
        if (position < 0 || position >= songs.size()) {
            return;
        }

        songs.remove(position);
        notifyItemRemoved(position);

        // 如果移除的是当前播放的歌曲，重置当前播放位置
        if (position == currentPlayingPosition) {
            currentPlayingPosition = -1;
        }
        // 如果移除位置在当前播放位置之前，需要调整当前播放位置
        else if (position < currentPlayingPosition) {
            currentPlayingPosition--;
        }
    }

    /**
     * 格式化歌曲时长
     *
     * @param durationMs 时长（毫秒）
     * @return 格式化后的时长字符串（mm:ss格式）
     */
    private String formatDuration(long durationMs) {
        long minutes = (durationMs / 1000) / 60;
        long seconds = (durationMs / 1000) % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    /**
     * 获取指定位置的歌曲
     *
     * @param position 位置
     * @return 歌曲对象
     */
    public Song getSongAt(int position) {
        if (position >= 0 && position < songs.size()) {
            return songs.get(position);
        }
        return null;
    }

    /**
     * 歌曲ViewHolder
     */
    static class SongViewHolder extends RecyclerView.ViewHolder {
        ImageView albumImageView;
        ImageView nowPlayingIndicator;
        TextView titleTextView;
        TextView artistTextView;
        TextView durationTextView;
        TextView searchBadgeView;
        ImageButton addToPlaylistButton;

        public SongViewHolder(@NonNull View itemView) {
            super(itemView);
            albumImageView = itemView.findViewById(R.id.iv_album_art);
            nowPlayingIndicator = itemView.findViewById(R.id.iv_now_playing);
            titleTextView = itemView.findViewById(R.id.tv_song_title);
            artistTextView = itemView.findViewById(R.id.tv_song_artist);
            durationTextView = itemView.findViewById(R.id.tv_song_duration);
            searchBadgeView = itemView.findViewById(R.id.tv_search_badge);
            addToPlaylistButton = itemView.findViewById(R.id.btn_add_to_playlist);
        }
    }

    /**
     * 歌曲点击监听接口
     */
    public interface OnSongClickListener {
        void onSongClick(int position, Song song);

        void onSongLongClick(int position, Song song);

        void onAddToPlaylistClick(int position, Song song);
    }
}
