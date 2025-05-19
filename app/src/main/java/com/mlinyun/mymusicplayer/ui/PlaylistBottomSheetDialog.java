package com.mlinyun.mymusicplayer.ui;

import android.content.Context;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.adapter.SongAdapter;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.viewmodel.PlayerViewModel;

import java.util.List;

/**
 * 播放队列底部对话框
 * 显示当前的播放队列，支持选择歌曲播放和从队列中删除
 */
public class PlaylistBottomSheetDialog extends BottomSheetDialogFragment implements SongAdapter.OnSongClickListener {

    private PlayerViewModel viewModel;
    private SongAdapter adapter;
    private TextView tvPlaylistCount;
    private RecyclerView recyclerView;

    // 用于将事件传递给宿主Fragment
    private PlaylistDialogCallback callback;

    // 静态工厂方法
    public static PlaylistBottomSheetDialog newInstance() {
        return new PlaylistBottomSheetDialog();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // 检查宿主Fragment是否实现了回调接口
        try {
            callback = (PlaylistDialogCallback) getParentFragment();
        } catch (ClassCastException e) {
            // 此处不需要强制实现接口，因为我们可以直接在ViewModel中操作
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_playlist, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 初始化ViewModel
        viewModel = ((PlaybackFragment) requireParentFragment()).getViewModel();

        // 初始化视图
        initViews(view);

        // 设置适配器
        setupAdapter();

        // 加载播放列表数据
        loadPlaylistData();
    }

    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.rvQueueSongs);
        tvPlaylistCount = view.findViewById(R.id.tvPlaylistCount);

        // 设置关闭按钮
        ImageButton ibClosePlaylist = view.findViewById(R.id.ibClosePlaylist);
        ibClosePlaylist.setOnClickListener(v -> dismiss());

        // 设置清空列表按钮
        Button btnClearPlaylist = view.findViewById(R.id.btnClearPlaylist);
        btnClearPlaylist.setOnClickListener(v -> {
            viewModel.clearPlaylist();
            Toast.makeText(requireContext(), R.string.playlist_cleared, Toast.LENGTH_SHORT).show();
            dismiss(); // 清空后关闭对话框
        });
    }

    private void setupAdapter() {
        // 创建适配器
        adapter = new SongAdapter(requireContext(), this);

        // 设置布局管理器
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        // 给RecyclerView添加自定义长按监听，用于删除队列项
        recyclerView.addOnItemTouchListener(new RecyclerView.OnItemTouchListener() {
            GestureDetector gestureDetector = new GestureDetector(requireContext(), new GestureDetector.SimpleOnGestureListener() {
                @Override
                public void onLongPress(MotionEvent e) {
                    // 找到长按的项
                    View childView = recyclerView.findChildViewUnder(e.getX(), e.getY());
                    if (childView != null) {
                        int position = recyclerView.getChildAdapterPosition(childView);
                        if (position != RecyclerView.NO_POSITION) {
                            Song song = adapter.getSongAt(position);
                            if (song != null) {
                                onSongLongClick(position, song);
                            }
                        }
                    }
                }
            });

            @Override
            public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
                gestureDetector.onTouchEvent(e);
                return false;
            }

            @Override
            public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            }

            @Override
            public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            }
        });
    }

    private void loadPlaylistData() {
        // 立即更新一次UI，确保初始显示正确
        updatePlaylistUI();

        // 设置播放列表观察者，确保实时更新
        viewModel.getPlaylist().observe(getViewLifecycleOwner(), playlist -> {
            updatePlaylistUI();
        });

        // 设置当前播放索引观察者
        viewModel.getCurrentSongIndex().observe(getViewLifecycleOwner(), currentIndex -> {
            if (currentIndex != null) {
                adapter.setCurrentPlayingPosition(currentIndex);
            } else {
                adapter.setCurrentPlayingPosition(-1);
            }
        });
    }

    /**
     * 更新播放列表UI
     */
    private void updatePlaylistUI() {
        List<Song> playlist = viewModel.getPlaylist().getValue();
        if (playlist != null) {
            adapter.updateSongs(playlist);
            tvPlaylistCount.setText(getString(R.string.playlist_count, playlist.size()));
        }
    }

    @Override
    public void onSongClick(int position, Song song) {
        // 播放选中的歌曲
        viewModel.playAtIndex(position);
        // 通知UI更新
        if (callback != null) {
            callback.onPlaylistItemSelected();
        }
        dismiss();  // 选择后关闭对话框
    }

    @Override
    public void onSongLongClick(int position, Song song) {
        // 长按删除队列中的歌曲
        viewModel.removeSongAtIndex(position);
        // 显示提示
        Toast.makeText(requireContext(),
                getString(R.string.song_removed, song.getTitle()),
                Toast.LENGTH_SHORT).show();
    }

    /**
     * 回调接口，用于与宿主Fragment通信
     */
    public interface PlaylistDialogCallback {
        void onPlaylistItemSelected();
    }
}
