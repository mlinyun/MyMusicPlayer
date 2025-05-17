package com.mlinyun.mymusicplayer.ui;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mlinyun.mymusicplayer.R;
import com.mlinyun.mymusicplayer.adapter.SongAdapter;
import com.mlinyun.mymusicplayer.model.Song;
import com.mlinyun.mymusicplayer.viewmodel.PlayerViewModel;

import java.util.List;

/**
 * 播放列表界面Fragment
 * 显示歌曲列表，支持搜索、排序和选择播放
 */
public class PlaylistFragment extends Fragment implements SongAdapter.OnSongClickListener {    // UI组件
    private RecyclerView recyclerView;
    private EditText etSearch;
    private Spinner spinnerSort;
    private View emptyView;
    private TextView tvSongsCount;
    private FloatingActionButton fabAddMusic;
    private ProgressBar progressBar;
    private TextView tvPlaylistHeader;
    private TextView tvSearchResultHeader;

    // 数据适配器
    private SongAdapter adapter;

    // ViewModel
    private PlayerViewModel viewModel;

    /**
     * 创建视图
     */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist, container, false);
        initViews(view);
        return view;
    }

    /**
     * 当视图创建完成时调用
     */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 获取ViewModel
        viewModel = new ViewModelProvider(requireActivity()).get(PlayerViewModel.class);

        // 设置适配器
        setupAdapter();

        // 设置搜索功能
        setupSearchFilter();

        // 设置排序spinner
        setupSortSpinner();

        // 设置扫描按钮
        setupScanButton();

        // 观察数据变化
        observeViewModel();
    }

    /**
     * 初始化视图组件
     */
    private void initViews(View view) {
        recyclerView = view.findViewById(R.id.rvSongs);
        etSearch = view.findViewById(R.id.etSearch);
        spinnerSort = view.findViewById(R.id.spinnerSort);
        emptyView = view.findViewById(R.id.emptyView);
        tvSongsCount = view.findViewById(R.id.tvSongCount);
        fabAddMusic = view.findViewById(R.id.fabAddMusic);
        progressBar = view.findViewById(R.id.progressBar);
        tvPlaylistHeader = view.findViewById(R.id.tvPlaylistHeader);
        tvSearchResultHeader = view.findViewById(R.id.tvSearchResultHeader);
        Button btnRequestPermission = view.findViewById(R.id.btnRequestPermission);
        btnRequestPermission.setOnClickListener(v -> {
            // 直接请求权限
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).checkAndRequestPermissions();
            }
        });

        // 设置RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setHasFixedSize(true);
    }

    /**
     * 设置歌曲列表适配器
     */
    private void setupAdapter() {
        adapter = new SongAdapter(requireContext(), this);
        recyclerView.setAdapter(adapter);
    }

    /**
     * 设置搜索过滤功能
     */
    private void setupSearchFilter() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // 不需要实现
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // 更新搜索过滤
                viewModel.setSearchFilter(s.toString());

                // 根据搜索状态显示/隐藏标题
                updateHeaderVisibility(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {
                // 不需要实现
            }
        });

        // 使用TextInputLayout的清除按钮功能
        // TextInputLayout已经通过endIconMode="clear_text"提供了清除功能
    }

    /**
     * 设置排序下拉选择器
     */
    private void setupSortSpinner() {
        // 创建排序选项适配器
        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                requireContext(),
                R.array.sort_options,
                android.R.layout.simple_spinner_item
        );
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerSort.setAdapter(spinnerAdapter);

        // 设置选择监听器
        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                // 根据选择的位置设置排序方式
                setSortMethodByPosition(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 不需要实现
            }
        });
    }

    /**
     * 根据位置设置排序方式
     */
    private void setSortMethodByPosition(int position) {
        switch (position) {
            case 0: // 按标题升序
                viewModel.setSortMethod(PlayerViewModel.SortMethod.TITLE_ASC);
                break;
            case 1: // 按标题降序
                viewModel.setSortMethod(PlayerViewModel.SortMethod.TITLE_DESC);
                break;
            case 2: // 按艺术家升序
                viewModel.setSortMethod(PlayerViewModel.SortMethod.ARTIST_ASC);
                break;
            case 3: // 按艺术家降序
                viewModel.setSortMethod(PlayerViewModel.SortMethod.ARTIST_DESC);
                break;
            case 4: // 按专辑升序
                viewModel.setSortMethod(PlayerViewModel.SortMethod.ALBUM_ASC);
                break;
            case 5: // 按专辑降序
                viewModel.setSortMethod(PlayerViewModel.SortMethod.ALBUM_DESC);
                break;
            case 6: // 按时长升序
                viewModel.setSortMethod(PlayerViewModel.SortMethod.DURATION_ASC);
                break;
            case 7: // 按时长降序
                viewModel.setSortMethod(PlayerViewModel.SortMethod.DURATION_DESC);
                break;
        }
    }

    /**
     * 设置扫描按钮
     */
    private void setupScanButton() {
        fabAddMusic.setOnClickListener(v -> {
            // 检查是否有存储权限
            if (hasStoragePermission()) {
                // 有权限，开始扫描音乐
                Toast.makeText(requireContext(), R.string.scanning_music, Toast.LENGTH_SHORT).show();
                progressBar.setVisibility(View.VISIBLE);
                viewModel.scanMusic();
            } else {
                // 没有权限，显示权限请求对话框
                showPermissionRequestDialog();
            }
        });
    }

    /**
     * 检查是否有存储权限
     */
    private boolean hasStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_AUDIO
            ) == PackageManager.PERMISSION_GRANTED;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED;
        }
    }

    /**
     * 显示权限请求对话框
     */
    private void showPermissionRequestDialog() {
        // 使用自定义布局的对话框
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_permission_request, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setView(dialogView)
                .setCancelable(true)
                .create();

        // 设置授权按钮点击事件
        Button btnGrantPermission = dialogView.findViewById(R.id.btnGrantPermission);
        btnGrantPermission.setOnClickListener(v -> {
            // 请求权限，跳转到MainActivity处理权限请求
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).checkAndRequestPermissions();
                dialog.dismiss();
            }
        });

        dialog.show();
    }

    /**
     * 观察ViewModel数据变化
     */
    private void observeViewModel() {
        // 观察歌曲列表
        viewModel.getFilteredSongs().observe(getViewLifecycleOwner(), songs -> {
            updateSongsList(songs);
            progressBar.setVisibility(View.GONE);
        });
        // 观察当前播放歌曲
        viewModel.getCurrentSong().observe(getViewLifecycleOwner(), song -> {
            if (song != null) {
                // 查找歌曲在适配器中的位置
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    Song adapterSong = adapter.getSongAt(i);
                    if (adapterSong != null && adapterSong.getId().equals(song.getId())) {
                        adapter.setCurrentPlayingPosition(i);
                        break;
                    }
                }
            } else {
                // 没有歌曲播放时，清除当前播放位置
                adapter.setCurrentPlayingPosition(-1);
            }
        });

        // 观察扫描状态
        viewModel.getScanningStatus().observe(getViewLifecycleOwner(), isScanning -> {
            progressBar.setVisibility(isScanning ? View.VISIBLE : View.GONE);
        });

        // 观察扫描结果消息
        viewModel.getScanResultMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                viewModel.clearScanResultMessage();
            }
        });
    }

    /**
     * 更新歌曲列表
     */
    private void updateSongsList(List<Song> songs) {
        adapter.updateSongs(songs);
        // 更新歌曲计数
        tvSongsCount.setText(getString(R.string.songs_count, songs.size()));
        // 显示/隐藏空列表提示
        if (songs.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
            tvPlaylistHeader.setVisibility(View.GONE);
            tvSearchResultHeader.setVisibility(View.GONE);
        } else {
            emptyView.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            // 更新标题显示
            String searchText = viewModel.getSearchFilter().getValue();
            updateHeaderVisibility(searchText);
        }
    }

    /**
     * 歌曲项点击事件处理
     * 点击后播放歌曲并自动跳转到播放页面
     */
    @Override
    public void onSongClick(int position, Song song) {
        // 检查是否为搜索结果中的歌曲
        if (song.isSearchResult()) {
            // 搜索结果点击 - 将歌曲添加到播放列表并播放
            addSongToPlaylistAndPlay(song);
        } else {            // 播放列表中的歌曲 - 直接播放
            viewModel.playSong(song);

            // 显示正在播放的提示信息
            Toast.makeText(requireContext(), getString(R.string.now_playing_song, song.getTitle()), Toast.LENGTH_SHORT).show();
        }

        // 跳转到播放页面
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).navigateToPlayback();
        }
    }

    /**
     * 将歌曲添加到播放列表并播放
     */
    private void addSongToPlaylistAndPlay(Song song) {
        // 创建非搜索结果版本的歌曲
        Song playlistSong = new Song(
                song.getId(), song.getTitle(), song.getArtist(),
                song.getAlbum(), song.getDuration(), song.getPath(),
                song.getAlbumArtUri()
        );
        // 确保设置为非搜索结果
        playlistSong.setSearchResult(false);

        // 添加到播放列表并播放
        viewModel.addSongAndPlay(playlistSong);

        // 显示添加到播放列表的提示信息
        Toast.makeText(requireContext(), getString(R.string.added_to_playlist, song.getTitle()), Toast.LENGTH_SHORT).show();

        // 清空搜索框，以便用户可以看到更新后的播放列表
        if (viewModel.getFilteredSongs().getValue() != null &&
                viewModel.getFilteredSongs().getValue().size() <= 1) {
            etSearch.setText("");
        }
    }

    /**
     * 歌曲项长按事件处理
     */
    @Override
    public void onSongLongClick(int position, Song song) {
        // 可以在这里实现长按操作，如显示更多选项菜单
    }

    /**
     * 添加到播放列表按钮点击事件处理
     */
    @Override
    public void onAddToPlaylistClick(int position, Song song) {
        if (song.isSearchResult()) {
            // 创建非搜索结果版本的歌曲
            Song playlistSong = new Song(
                    song.getId(), song.getTitle(), song.getArtist(),
                    song.getAlbum(), song.getDuration(), song.getPath(),
                    song.getAlbumArtUri()
            );
            // 确保设置为非搜索结果
            playlistSong.setSearchResult(false);

            // 仅添加到播放列表，不立即播放
            viewModel.addSong(playlistSong);

            // 显示添加到播放列表的提示信息
            Toast.makeText(requireContext(), getString(R.string.added_to_playlist, song.getTitle()), Toast.LENGTH_SHORT).show();

            // 通知适配器数据已更改，以更新UI显示
            adapter.notifyDataSetChanged();
        }
    }

    /**
     * Fragment恢复时调用
     */
    @Override
    public void onResume() {
        super.onResume();

        // 只在有权限的情况下刷新歌曲列表
        if (hasStoragePermission()) {
            viewModel.refreshSongsList();
        }

        // 更新标题显示
        String searchText = viewModel.getSearchFilter().getValue();
        updateHeaderVisibility(searchText);
    }

    /**
     * 根据搜索状态更新标题的可见性
     *
     * @param searchText 搜索文本
     */
    private void updateHeaderVisibility(String searchText) {
        boolean isSearching = searchText != null && !searchText.isEmpty();

        // 检查歌曲列表是否为空
        List<Song> songs = viewModel.getFilteredSongs().getValue();
        boolean hasResults = songs != null && !songs.isEmpty();

        if (!hasResults) {
            // 如果没有结果，隐藏所有标题
            tvPlaylistHeader.setVisibility(View.GONE);
            tvSearchResultHeader.setVisibility(View.GONE);
            return;
        }

        // 当搜索时，显示本地音乐标题；否则显示播放列表标题
        tvPlaylistHeader.setVisibility(isSearching ? View.GONE : View.VISIBLE);

        // 检查是否有搜索结果
        boolean hasSearchResults = false;
        if (isSearching && songs != null) {
            for (Song song : songs) {
                if (song.isSearchResult()) {
                    hasSearchResults = true;
                    break;
                }
            }
        }

        // 只有在搜索并且有搜索结果时显示搜索结果标题
        tvSearchResultHeader.setVisibility(hasSearchResults ? View.VISIBLE : View.GONE);
    }
}
