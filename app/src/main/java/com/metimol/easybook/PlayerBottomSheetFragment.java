package com.metimol.easybook;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.metimol.easybook.api.models.Author;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.BookFile;
import com.metimol.easybook.service.PlaybackService;

public class PlayerBottomSheetFragment extends BottomSheetDialogFragment {

    private MainViewModel viewModel;
    private PlaybackService playbackService;

    private ImageView ivCover;
    private TextView tvEpisodeTitle, tvBookAuthor, tvCurrentTime, tvTotalTime;
    private SeekBar playerSeekBar;
    private FloatingActionButton btnPlayPause;
    private ImageView btnPrev, btnNext;

    private CircularProgressDrawable progressDrawable;
    private boolean isUserSeeking = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);

        progressDrawable = new CircularProgressDrawable(requireContext());
        progressDrawable.setStrokeWidth(5f);
        progressDrawable.setCenterRadius(30f);
        progressDrawable.start();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ivCover = view.findViewById(R.id.iv_large_cover);
        tvEpisodeTitle = view.findViewById(R.id.tv_episode_title);
        tvBookAuthor = view.findViewById(R.id.tv_book_author);
        tvCurrentTime = view.findViewById(R.id.tv_current_time);
        tvTotalTime = view.findViewById(R.id.tv_total_time);
        playerSeekBar = view.findViewById(R.id.player_seek_bar);
        btnPlayPause = view.findViewById(R.id.btn_play_pause);
        btnPrev = view.findViewById(R.id.btn_prev);
        btnNext = view.findViewById(R.id.btn_next);

        setupClickListeners();

        viewModel.getPlaybackService().observe(getViewLifecycleOwner(), service -> {
            if (service == null) return;
            this.playbackService = service;
            observeServiceLiveData();
        });
    }

    private void setupClickListeners() {
        btnPlayPause.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.togglePlayPause();
            }
        });

        btnNext.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.skipToNext();
            }
        });

        btnPrev.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.skipToPrevious();
            }
        });

        playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    tvCurrentTime.setText(PlaybackService.formatDuration(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (playbackService != null) {
                    playbackService.seekTo(seekBar.getProgress());
                }
                isUserSeeking = false;
            }
        });
    }

    private void observeServiceLiveData() {
        playbackService.currentBook.observe(getViewLifecycleOwner(), this::updateBookUI);
        playbackService.currentChapter.observe(getViewLifecycleOwner(), this::updateChapterUI);
        playbackService.isPlaying.observe(getViewLifecycleOwner(), this::updatePlayPauseButton);
        playbackService.totalDuration.observe(getViewLifecycleOwner(), this::updateDurationUI);
        playbackService.currentPosition.observe(getViewLifecycleOwner(), this::updateProgressUI);
    }

    private void updateBookUI(Book book) {
        if (book == null || !isAdded()) return;

        if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
            Author author = book.getAuthors().get(0);
            tvBookAuthor.setText(String.format("%s %s", author.getName(), author.getSurname()));
        } else {
            tvBookAuthor.setText(R.string.unknown);
        }

        Glide.with(requireContext())
                .load(book.getDefaultPoster())
                .placeholder(progressDrawable)
                .error(R.drawable.ic_placeholder_book)
                .fallback(R.drawable.ic_placeholder_book)
                .into(ivCover);
    }

    private void updateChapterUI(BookFile chapter) {
        if (chapter == null) return;
        tvEpisodeTitle.setText(chapter.getTitle());
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (isPlaying) {
            btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            btnPlayPause.setImageResource(R.drawable.ic_player_icon);
        }
    }

    private void updateDurationUI(long duration) {
        if (duration <= 0) return;
        tvTotalTime.setText(PlaybackService.formatDuration(duration));
        playerSeekBar.setMax((int) duration);
    }

    private void updateProgressUI(long position) {
        if (isUserSeeking) return;
        tvCurrentTime.setText(PlaybackService.formatDuration(position));
        playerSeekBar.setProgress((int) position);
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                BottomSheetBehavior.from(bottomSheet).setSkipCollapsed(true);
            }
        });
        return dialog;
    }
}