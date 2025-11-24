package com.metimol.easybook.adapter;

import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.metimol.easybook.MainViewModel;
import com.metimol.easybook.R;
import com.metimol.easybook.api.models.Author;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.BookFile;
import com.metimol.easybook.service.PlaybackService;

import java.util.ArrayList;
import java.util.Objects;

public class PlayerViewPagerAdapter extends RecyclerView.Adapter<PlayerViewPagerAdapter.PageViewHolder> {

    private static final int CONTROLS_PAGE = 0;
    private static final int CHAPTERS_PAGE = 1;

    private final LifecycleOwner lifecycleOwner;
    private final EpisodeAdapter episodeAdapter;
    private PlaybackService playbackService;
    private boolean isUserSeeking = false;

    private ControlsViewHolder controlsViewHolder;
    private ChaptersViewHolder chaptersViewHolder;
    private final Runnable onChapterClick;

    private MainViewModel mainViewModel;

    public PlayerViewPagerAdapter(LifecycleOwner lifecycleOwner, EpisodeAdapter episodeAdapter, Runnable onChapterClick) {
        this.lifecycleOwner = lifecycleOwner;
        this.episodeAdapter = episodeAdapter;
        this.onChapterClick = onChapterClick;
    }

    public void setMainViewModel(MainViewModel mainViewModel) {
        this.mainViewModel = mainViewModel;
    }

    public void setPlaybackService(PlaybackService service) {
        this.playbackService = service;
        if (service != null) {
            observeServiceLiveData();
            updateControlsPageUI();
            updateChaptersPageUI();
        } else {
            if (controlsViewHolder != null) {
                controlsViewHolder.itemView.setVisibility(View.GONE);
            }
            if (chaptersViewHolder != null) {
                chaptersViewHolder.itemView.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemViewType(int position) {
        return position == 0 ? CONTROLS_PAGE : CHAPTERS_PAGE;
    }

    @NonNull
    @Override
    public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == CONTROLS_PAGE) {
            View view = inflater.inflate(R.layout.fragment_player_page_controls, parent, false);
            controlsViewHolder = new ControlsViewHolder(view);
            return controlsViewHolder;
        } else {
            View view = inflater.inflate(R.layout.fragment_player_page_chapters, parent, false);
            chaptersViewHolder = new ChaptersViewHolder(view);
            return chaptersViewHolder;
        }
    }

    @Override
    public void onBindViewHolder(@NonNull PageViewHolder holder, int position) {
        if (holder.getItemViewType() == CONTROLS_PAGE) {
            setupControlsPage((ControlsViewHolder) holder);
            updateControlsPageUI();
        } else {
            setupChaptersPage((ChaptersViewHolder) holder);
            updateChaptersPageUI();
        }
    }

    @Override
    public int getItemCount() {
        return 2;
    }

    private void setupControlsPage(ControlsViewHolder holder) {
        holder.btnPlayPause.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.togglePlayPause();
            }
        });

        holder.btnNextCard.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.skipToNext();
            }
        });

        holder.btnPrevCard.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.skipToPrevious();
            }
        });

        holder.btnRewind.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.rewind();
            }
        });

        holder.btnForward.setOnClickListener(v -> {
            if (playbackService != null) {
                playbackService.fastForward();
            }
        });

        holder.btnSleepTimer.setOnClickListener(v -> showSleepTimerDialog(holder.itemView.getContext()));

        holder.playerSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    holder.tvCurrentTime.setText(PlaybackService.formatDuration(progress));
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
                    long bufferedPos = playbackService.bufferedPosition.getValue() != null ? playbackService.bufferedPosition.getValue() : 0L;
                    updateBufferedPosition(bufferedPos);
                }
                isUserSeeking = false;
            }
        });
    }

    private void setupChaptersPage(ChaptersViewHolder holder) {
        holder.chaptersRecyclerView.setLayoutManager(new LinearLayoutManager(holder.itemView.getContext()));
        holder.chaptersRecyclerView.setAdapter(episodeAdapter);

        episodeAdapter.setOnEpisodeClickListener((episode, position) -> {
            Book book = playbackService.currentBook.getValue();
            if (playbackService != null && book != null) {
                playbackService.playBookFromProgress(book, position, 0L);
                if (onChapterClick != null) {
                    onChapterClick.run();
                }
            }
        });
    }

    private void updateControlsPageUI() {
        if (playbackService == null || controlsViewHolder == null) {
            if (controlsViewHolder != null) controlsViewHolder.itemView.setVisibility(View.GONE);
            return;
        }

        Book currentBook = playbackService.currentBook.getValue();
        if (currentBook == null) {
            controlsViewHolder.itemView.setVisibility(View.GONE);
        } else {
            controlsViewHolder.itemView.setVisibility(View.VISIBLE);
            updateBookUI(currentBook);
            updateChapterUI(playbackService.currentChapter.getValue());
            updatePlayPauseButton(Boolean.TRUE.equals(playbackService.isPlaying.getValue()));
            updateDurationUI(playbackService.totalDuration.getValue() != null ? playbackService.totalDuration.getValue() : 0L);
            updateProgressUI(playbackService.currentPosition.getValue() != null ? playbackService.currentPosition.getValue() : 0L);
            updateLoadingState(Boolean.TRUE.equals(playbackService.isLoading.getValue()));
            updateBufferedPosition(playbackService.bufferedPosition.getValue() != null ? playbackService.bufferedPosition.getValue() : 0L);
            updateTimerState(Boolean.TRUE.equals(playbackService.isSleepTimerActive.getValue()));
            controlsViewHolder.btnNextCard.setVisibility(Boolean.TRUE.equals(playbackService.hasNext.getValue()) ? View.VISIBLE : View.GONE);
            controlsViewHolder.btnPrevCard.setVisibility(Boolean.TRUE.equals(playbackService.hasPrevious.getValue()) ? View.VISIBLE : View.GONE);
        }
    }

    private void updateChaptersPageUI() {
        if (playbackService == null || chaptersViewHolder == null) {
            if (chaptersViewHolder != null) chaptersViewHolder.itemView.setVisibility(View.GONE);
            return;
        }

        Book currentBook = playbackService.currentBook.getValue();
        if (currentBook == null) {
            chaptersViewHolder.itemView.setVisibility(View.GONE);
        } else {
            chaptersViewHolder.itemView.setVisibility(View.VISIBLE);
            if (currentBook.getFiles() != null && currentBook.getFiles().getFull() != null) {
                episodeAdapter.submitList(currentBook.getFiles().getFull());
            } else {
                episodeAdapter.submitList(new ArrayList<>());
            }
            updateChapterUI(playbackService.currentChapter.getValue());
        }
    }


    private void observeServiceLiveData() {
        if (playbackService == null) return;

        playbackService.currentBook.observe(lifecycleOwner, book -> {
            updateControlsPageUI();
            updateChaptersPageUI();
        });
        playbackService.currentChapter.observe(lifecycleOwner, this::updateChapterUI);
        playbackService.isPlaying.observe(lifecycleOwner, this::updatePlayPauseButton);
        playbackService.totalDuration.observe(lifecycleOwner, this::updateDurationUI);
        playbackService.currentPosition.observe(lifecycleOwner, this::updateProgressUI);
        playbackService.isLoading.observe(lifecycleOwner, this::updateLoadingState);
        playbackService.bufferedPosition.observe(lifecycleOwner, this::updateBufferedPosition);
        playbackService.isSleepTimerActive.observe(lifecycleOwner, this::updateTimerState);
        playbackService.hasNext.observe(lifecycleOwner, hasNext -> {
            if (controlsViewHolder != null) controlsViewHolder.btnNextCard.setVisibility(hasNext ? View.VISIBLE : View.GONE);
        });
        playbackService.hasPrevious.observe(lifecycleOwner, hasPrevious -> {
            if (controlsViewHolder != null) controlsViewHolder.btnPrevCard.setVisibility(hasPrevious ? View.VISIBLE : View.GONE);
        });
    }

    private void updateBookUI(Book book) {
        if (book == null || controlsViewHolder == null) return;

        if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
            Author author = book.getAuthors().get(0);
            controlsViewHolder.tvBookAuthor.setText(String.format("%s %s", author.getName(), author.getSurname()));
        } else {
            controlsViewHolder.tvBookAuthor.setText(R.string.unknown);
        }

        Glide.with(controlsViewHolder.itemView.getContext())
                .load(book.getDefaultPosterMain())
                .placeholder(controlsViewHolder.progressDrawable)
                .error(R.drawable.ic_placeholder_book)
                .fallback(R.drawable.ic_placeholder_book)
                .into(controlsViewHolder.ivCover);
    }

    private void updateChapterUI(BookFile chapter) {
        if (chapter == null) return;
        if (controlsViewHolder != null) {
            controlsViewHolder.tvEpisodeTitle.setText(chapter.getTitle());
        }
        episodeAdapter.setSelectedChapterId(String.valueOf(chapter.getId()));
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (controlsViewHolder == null) return;
        if (isPlaying) {
            controlsViewHolder.btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            controlsViewHolder.btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void updateDurationUI(long duration) {
        if (duration <= 0 || controlsViewHolder == null) return;
        controlsViewHolder.tvTotalTime.setText(PlaybackService.formatDuration(duration));
        controlsViewHolder.playerSeekBar.setMax((int) duration);
    }

    private void updateProgressUI(long position) {
        if (isUserSeeking || controlsViewHolder == null) return;
        controlsViewHolder.tvCurrentTime.setText(PlaybackService.formatDuration(position));
        controlsViewHolder.playerSeekBar.setProgress((int) position);
    }

    private void updateLoadingState(boolean isLoading) {
        if (controlsViewHolder == null) return;
        if (isLoading) {
            controlsViewHolder.btnPlayPause.setVisibility(View.INVISIBLE);
            controlsViewHolder.loadingProgressBar.setVisibility(View.VISIBLE);
        } else {
            controlsViewHolder.btnPlayPause.setVisibility(View.VISIBLE);
            controlsViewHolder.loadingProgressBar.setVisibility(View.GONE);
        }
    }

    private void updateBufferedPosition(long position) {
        if (isUserSeeking || controlsViewHolder == null) return;
        controlsViewHolder.playerSeekBar.setSecondaryProgress((int) position);
    }

    private void updateTimerState(boolean isActive) {
        if (controlsViewHolder == null) return;
        if (isActive) {
            int activeColor = ContextCompat.getColor(controlsViewHolder.itemView.getContext(), R.color.green);
            ImageViewCompat.setImageTintList(controlsViewHolder.btnSleepTimer, ColorStateList.valueOf(activeColor));
        } else {
            int inactiveColor = ContextCompat.getColor(controlsViewHolder.itemView.getContext(), R.color.light_grey);
            ImageViewCompat.setImageTintList(controlsViewHolder.btnSleepTimer, ColorStateList.valueOf(inactiveColor));
        }
    }

    private void showSleepTimerDialog(android.content.Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.CustomBottomSheetDialogTheme);
        dialog.setContentView(R.layout.fragment_timer_dialog);

        if (mainViewModel == null) return;

        TextView timer_off = dialog.findViewById(R.id.timer_off);
        Objects.requireNonNull(timer_off).setOnClickListener(v -> {
            mainViewModel.cancelSleepTimer();
            dialog.dismiss();
        });

        TextView timer_5 = dialog.findViewById(R.id.timer_5);
        Objects.requireNonNull(timer_5).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(5);
            dialog.dismiss();
        });

        TextView timer_15 = dialog.findViewById(R.id.timer_15);
        Objects.requireNonNull(timer_15).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(15);
            dialog.dismiss();
        });

        TextView timer_30 = dialog.findViewById(R.id.timer_30);
        Objects.requireNonNull(timer_30).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(30);
            dialog.dismiss();
        });

        TextView timer_45 = dialog.findViewById(R.id.timer_45);
        Objects.requireNonNull(timer_45).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(45);
            dialog.dismiss();
        });

        TextView timer_60 = dialog.findViewById(R.id.timer_60);
        Objects.requireNonNull(timer_60).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(60);
            dialog.dismiss();
        });

        TextView timer_end_of_chapter = dialog.findViewById(R.id.timer_end_of_chapter);
        Objects.requireNonNull(timer_end_of_chapter).setOnClickListener(v -> {
            mainViewModel.setSleepTimerEndOfChapter();
            dialog.dismiss();
        });

        dialog.show();
    }


    static class PageViewHolder extends RecyclerView.ViewHolder {
        public PageViewHolder(@NonNull View itemView) {
            super(itemView);
        }
    }

    static class ControlsViewHolder extends PageViewHolder {
        ImageView ivCover;
        TextView tvEpisodeTitle, tvBookAuthor, tvCurrentTime, tvTotalTime;
        SeekBar playerSeekBar;
        FloatingActionButton btnPlayPause;
        CardView btnPrevCard, btnNextCard;
        ImageView btnRewind, btnForward;
        CircularProgressDrawable progressDrawable;
        ProgressBar loadingProgressBar;
        ImageView btnSleepTimer;

        public ControlsViewHolder(@NonNull View itemView) {
            super(itemView);
            ivCover = itemView.findViewById(R.id.iv_large_cover);
            tvEpisodeTitle = itemView.findViewById(R.id.tv_episode_title);
            tvBookAuthor = itemView.findViewById(R.id.tv_book_author);
            tvCurrentTime = itemView.findViewById(R.id.tv_current_time);
            tvTotalTime = itemView.findViewById(R.id.tv_total_time);
            playerSeekBar = itemView.findViewById(R.id.player_seek_bar);
            btnPlayPause = itemView.findViewById(R.id.btn_play_pause);
            btnPrevCard = itemView.findViewById(R.id.btn_prev_card);
            btnNextCard = itemView.findViewById(R.id.btn_next_card);
            btnRewind = itemView.findViewById(R.id.btn_rewind);
            btnForward = itemView.findViewById(R.id.btn_forward);
            loadingProgressBar = itemView.findViewById(R.id.loading_progress_bar);
            btnSleepTimer = itemView.findViewById(R.id.btn_sleep_timer);

            progressDrawable = new CircularProgressDrawable(itemView.getContext());
            progressDrawable.setStrokeWidth(5f);
            progressDrawable.setCenterRadius(30f);
            progressDrawable.start();
        }
    }

    static class ChaptersViewHolder extends PageViewHolder {
        RecyclerView chaptersRecyclerView;

        public ChaptersViewHolder(@NonNull View itemView) {
            super(itemView);
            chaptersRecyclerView = itemView.findViewById(R.id.chapters_recycler_view);
        }
    }
}