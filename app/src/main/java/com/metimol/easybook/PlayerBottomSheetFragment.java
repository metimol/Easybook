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
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
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
    private CardView btnPrevCard, btnNextCard;

    private CircularProgressDrawable progressDrawable;
    private boolean isUserSeeking = false;

    private ConstraintLayout playerContentGroup;
    private View emptyPlayerView;
    private View pager_indicator;

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
        btnPrevCard = view.findViewById(R.id.btn_prev_card);
        btnNextCard = view.findViewById(R.id.btn_next_card);
        playerContentGroup = view.findViewById(R.id.player_content_group);
        emptyPlayerView = view.findViewById(R.id.empty_player_view);
        pager_indicator = view.findViewById(R.id.pager_indicator);

        setupClickListeners();

        viewModel.getPlaybackService().observe(getViewLifecycleOwner(), service -> {
            if (service == null) {
                playerContentGroup.setVisibility(View.GONE);
                pager_indicator.setVisibility(View.GONE);
                emptyPlayerView.setVisibility(View.VISIBLE);
                return;
            }
            this.playbackService = service;
            observeServiceLiveData();
            updateInitialUIState();
        });
    }

    private void updateInitialUIState() {
        if (playbackService == null) {
            playerContentGroup.setVisibility(View.GONE);
            pager_indicator.setVisibility(View.GONE);
            emptyPlayerView.setVisibility(View.VISIBLE);
            return;
        }

        Book currentBook = playbackService.currentBook.getValue();
        if (currentBook == null) {
            playerContentGroup.setVisibility(View.GONE);
            pager_indicator.setVisibility(View.GONE);
            emptyPlayerView.setVisibility(View.VISIBLE);
        } else {
            playerContentGroup.setVisibility(View.VISIBLE);
            pager_indicator.setVisibility(View.VISIBLE);
            emptyPlayerView.setVisibility(View.GONE);

            updateBookUI(currentBook);
            updateChapterUI(playbackService.currentChapter.getValue());
            updatePlayPauseButton(Boolean.TRUE.equals(playbackService.isPlaying.getValue()));
            updateDurationUI(playbackService.totalDuration.getValue() != null ? playbackService.totalDuration.getValue() : 0L);
            updateProgressUI(playbackService.currentPosition.getValue() != null ? playbackService.currentPosition.getValue() : 0L);
            btnNextCard.setVisibility(Boolean.TRUE.equals(playbackService.hasNext.getValue()) ? View.VISIBLE : View.INVISIBLE);
            btnPrevCard.setVisibility(Boolean.TRUE.equals(playbackService.hasPrevious.getValue()) ? View.VISIBLE : View.INVISIBLE);
        }
    }

    private void setupClickListeners() {
        btnPlayPause.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.togglePlayPause();
            }
        });

        btnNextCard.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.skipToNext();
            }
        });

        btnPrevCard.setOnClickListener(v -> {
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
        playbackService.currentBook.observe(getViewLifecycleOwner(), book -> {
            if (book == null) {
                playerContentGroup.setVisibility(View.GONE);
                emptyPlayerView.setVisibility(View.VISIBLE);
            } else {
                playerContentGroup.setVisibility(View.VISIBLE);
                emptyPlayerView.setVisibility(View.GONE);
                updateBookUI(book);
            }
        });
        playbackService.currentChapter.observe(getViewLifecycleOwner(), this::updateChapterUI);
        playbackService.isPlaying.observe(getViewLifecycleOwner(), this::updatePlayPauseButton);
        playbackService.totalDuration.observe(getViewLifecycleOwner(), this::updateDurationUI);
        playbackService.currentPosition.observe(getViewLifecycleOwner(), this::updateProgressUI);
        playbackService.hasNext.observe(getViewLifecycleOwner(), hasNext -> btnNextCard.setVisibility(hasNext ? View.VISIBLE : View.INVISIBLE));
        playbackService.hasPrevious.observe(getViewLifecycleOwner(), hasPrevious -> btnPrevCard.setVisibility(hasPrevious ? View.VISIBLE : View.INVISIBLE));
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
                .load(book.getDefaultPosterMain())
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