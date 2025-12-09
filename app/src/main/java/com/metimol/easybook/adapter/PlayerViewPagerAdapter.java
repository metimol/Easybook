package com.metimol.easybook.adapter;

import static com.metimol.easybook.MainActivity.APP_PREFERENCES;
import static com.metimol.easybook.SettingsFragment.SPEED_KEY;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
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
import java.util.List;
import java.util.Locale;
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
    private int lastSelectedSpeedIndex = -1;

    public PlayerViewPagerAdapter(LifecycleOwner lifecycleOwner, EpisodeAdapter episodeAdapter,
            Runnable onChapterClick) {
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

        holder.btnSpeed.setOnClickListener(v -> showSpeedDialog(holder.itemView.getContext()));

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
                    long bufferedPos = playbackService.bufferedPosition.getValue() != null
                            ? playbackService.bufferedPosition.getValue()
                            : 0L;
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
            if (controlsViewHolder != null)
                controlsViewHolder.itemView.setVisibility(View.GONE);
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
            updateDurationUI(
                    playbackService.totalDuration.getValue() != null ? playbackService.totalDuration.getValue() : 0L);
            updateProgressUI(
                    playbackService.currentPosition.getValue() != null ? playbackService.currentPosition.getValue()
                            : 0L);
            updateLoadingState(Boolean.TRUE.equals(playbackService.isLoading.getValue()));
            updateBufferedPosition(
                    playbackService.bufferedPosition.getValue() != null ? playbackService.bufferedPosition.getValue()
                            : 0L);
            updateTimerState(Boolean.TRUE.equals(playbackService.isSleepTimerActive.getValue()));
            updateSpeedUI(
                    playbackService.playbackSpeed.getValue() != null ? playbackService.playbackSpeed.getValue() : 1.0f);
            controlsViewHolder.btnNextCard
                    .setVisibility(Boolean.TRUE.equals(playbackService.hasNext.getValue()) ? View.VISIBLE : View.GONE);
            controlsViewHolder.btnPrevCard.setVisibility(
                    Boolean.TRUE.equals(playbackService.hasPrevious.getValue()) ? View.VISIBLE : View.GONE);
        }
    }

    private void updateChaptersPageUI() {
        if (playbackService == null || chaptersViewHolder == null) {
            if (chaptersViewHolder != null)
                chaptersViewHolder.itemView.setVisibility(View.GONE);
            return;
        }

        Book currentBook = playbackService.currentBook.getValue();
        if (currentBook == null) {
            chaptersViewHolder.itemView.setVisibility(View.GONE);
        } else {
            chaptersViewHolder.itemView.setVisibility(View.VISIBLE);
            if (currentBook.getFiles() != null && !currentBook.getFiles().isEmpty()) {
                episodeAdapter.submitList(currentBook.getFiles());
            } else {
                episodeAdapter.submitList(new ArrayList<>());
            }
            updateChapterUI(playbackService.currentChapter.getValue());
        }
    }

    private void observeServiceLiveData() {
        if (playbackService == null)
            return;

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
        playbackService.playbackSpeed.observe(lifecycleOwner, this::updateSpeedUI);
        playbackService.hasNext.observe(lifecycleOwner, hasNext -> {
            if (controlsViewHolder != null)
                controlsViewHolder.btnNextCard.setVisibility(hasNext ? View.VISIBLE : View.GONE);
        });
        playbackService.hasPrevious.observe(lifecycleOwner, hasPrevious -> {
            if (controlsViewHolder != null)
                controlsViewHolder.btnPrevCard.setVisibility(hasPrevious ? View.VISIBLE : View.GONE);
        });
    }

    private void updateBookUI(Book book) {
        if (book == null || controlsViewHolder == null)
            return;

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
        if (chapter == null)
            return;
        if (controlsViewHolder != null) {
            controlsViewHolder.tvEpisodeTitle.setText(chapter.getTitle());
        }
        episodeAdapter.setSelectedChapterId(String.valueOf(chapter.getIndex()));
    }

    private void updatePlayPauseButton(boolean isPlaying) {
        if (controlsViewHolder == null)
            return;
        if (isPlaying) {
            controlsViewHolder.btnPlayPause.setImageResource(R.drawable.ic_pause);
        } else {
            controlsViewHolder.btnPlayPause.setImageResource(R.drawable.ic_play);
        }
    }

    private void updateDurationUI(long duration) {
        if (duration <= 0 || controlsViewHolder == null)
            return;
        controlsViewHolder.tvTotalTime.setText(PlaybackService.formatDuration(duration));
        controlsViewHolder.playerSeekBar.setMax((int) duration);
    }

    private void updateProgressUI(long position) {
        if (isUserSeeking || controlsViewHolder == null)
            return;
        controlsViewHolder.tvCurrentTime.setText(PlaybackService.formatDuration(position));
        controlsViewHolder.playerSeekBar.setProgress((int) position);
    }

    private void updateLoadingState(boolean isLoading) {
        if (controlsViewHolder == null)
            return;
        if (isLoading) {
            controlsViewHolder.btnPlayPause.setVisibility(View.INVISIBLE);
            controlsViewHolder.loadingProgressBar.setVisibility(View.VISIBLE);
        } else {
            controlsViewHolder.btnPlayPause.setVisibility(View.VISIBLE);
            controlsViewHolder.loadingProgressBar.setVisibility(View.GONE);
        }
    }

    private void updateBufferedPosition(long position) {
        if (isUserSeeking || controlsViewHolder == null)
            return;
        controlsViewHolder.playerSeekBar.setSecondaryProgress((int) position);
    }

    private void updateTimerState(boolean isActive) {
        if (controlsViewHolder == null)
            return;
        if (isActive) {
            int activeColor = ContextCompat.getColor(controlsViewHolder.itemView.getContext(), R.color.green);
            ImageViewCompat.setImageTintList(controlsViewHolder.btnSleepTimer, ColorStateList.valueOf(activeColor));
        } else {
            int inactiveColor = ContextCompat.getColor(controlsViewHolder.itemView.getContext(), R.color.light_grey);
            ImageViewCompat.setImageTintList(controlsViewHolder.btnSleepTimer, ColorStateList.valueOf(inactiveColor));
        }
    }

    private void updateSpeedUI(float speed) {
        if (controlsViewHolder == null)
            return;
        controlsViewHolder.btnSpeed.setText(String.format(Locale.US, "%.2fx", speed));
    }

    private void showSleepTimerDialog(android.content.Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.CustomBottomSheetDialogTheme);
        dialog.setContentView(R.layout.fragment_timer_dialog);

        if (mainViewModel == null || playbackService == null)
            return;

        int currentMode = playbackService.activeSleepTimerMode.getValue() != null
                ? playbackService.activeSleepTimerMode.getValue()
                : PlaybackService.TIMER_OFF;

        int greenColor = ContextCompat.getColor(context, R.color.green);

        TextView timer_off = dialog.findViewById(R.id.timer_off);
        TextView timer_5 = dialog.findViewById(R.id.timer_5);
        TextView timer_15 = dialog.findViewById(R.id.timer_15);
        TextView timer_30 = dialog.findViewById(R.id.timer_30);
        TextView timer_45 = dialog.findViewById(R.id.timer_45);
        TextView timer_60 = dialog.findViewById(R.id.timer_60);
        TextView timer_end_of_chapter = dialog.findViewById(R.id.timer_end_of_chapter);

        TextView activeView = switch (currentMode) {
            case PlaybackService.TIMER_5 -> timer_5;
            case PlaybackService.TIMER_15 -> timer_15;
            case PlaybackService.TIMER_30 -> timer_30;
            case PlaybackService.TIMER_45 -> timer_45;
            case PlaybackService.TIMER_60 -> timer_60;
            case PlaybackService.TIMER_END_OF_CHAPTER -> timer_end_of_chapter;
            default -> timer_off;
        };

        if (activeView != null) {
            activeView.setTextColor(greenColor);
        }

        Objects.requireNonNull(timer_off).setOnClickListener(v -> {
            mainViewModel.cancelSleepTimer();
            dialog.dismiss();
        });

        Objects.requireNonNull(timer_5).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(5);
            dialog.dismiss();
        });

        Objects.requireNonNull(timer_15).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(15);
            dialog.dismiss();
        });

        Objects.requireNonNull(timer_30).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(30);
            dialog.dismiss();
        });

        Objects.requireNonNull(timer_45).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(45);
            dialog.dismiss();
        });

        Objects.requireNonNull(timer_60).setOnClickListener(v -> {
            mainViewModel.setSleepTimer(60);
            dialog.dismiss();
        });

        Objects.requireNonNull(timer_end_of_chapter).setOnClickListener(v -> {
            mainViewModel.setSleepTimerEndOfChapter();
            dialog.dismiss();
        });

        dialog.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showSpeedDialog(Context context) {
        BottomSheetDialog dialog = new BottomSheetDialog(context, R.style.CustomBottomSheetDialogTheme);
        dialog.setContentView(R.layout.fragment_speed_dialog);

        HorizontalScrollView scrollView = dialog.findViewById(R.id.speed_scroll_view);
        LinearLayout container = dialog.findViewById(R.id.speed_container);
        TextView speedValueText = dialog.findViewById(R.id.speed_value_text);

        if (scrollView == null || container == null || speedValueText == null)
            return;

        SharedPreferences prefs = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        float currentSpeed = prefs.getFloat(SPEED_KEY, 1.0f);
        if (currentSpeed < 0.25f)
            currentSpeed = 0.25f;
        if (currentSpeed > 5.0f)
            currentSpeed = 5.0f;

        List<Float> speedValues = new ArrayList<>();
        for (int i = 25; i <= 500; i += 5) {
            speedValues.add(i / 100.0f);
        }

        Typeface typeface = ResourcesCompat.getFont(context, R.font.sf_pro);

        for (int i = 0; i < speedValues.size(); i++) {
            float speed = speedValues.get(i);
            int valueInt = (int) (speed * 100);

            LinearLayout itemLayout = new LinearLayout(context);
            itemLayout.setOrientation(LinearLayout.VERTICAL);
            itemLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            LinearLayout.LayoutParams itemParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);

            if (i < speedValues.size() - 1) {
                itemParams.setMarginEnd(dpToPx(8));
            }
            itemLayout.setLayoutParams(itemParams);

            boolean isMajor = (valueInt % 10 == 0);
            boolean showText = (valueInt % 20 == 0);

            View line = new View(context);
            int height = isMajor ? dpToPx(16) : dpToPx(8);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(dpToPx(2), height);
            line.setLayoutParams(lineParams);
            line.setBackgroundColor(ContextCompat.getColor(context, R.color.light_grey));
            itemLayout.addView(line);

            TextView text = new TextView(context);
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            textParams.topMargin = dpToPx(8);
            text.setLayoutParams(textParams);
            text.setText(String.format(Locale.US, "%.1f", speed));
            text.setTextSize(12);
            text.setTextColor(ContextCompat.getColor(context, R.color.light_grey));
            text.setTypeface(typeface);

            if (showText) {
                text.setVisibility(View.VISIBLE);
            } else {
                text.setVisibility(View.INVISIBLE);
            }

            itemLayout.addView(text);
            container.addView(itemLayout);
        }

        final float finalCurrentSpeed = currentSpeed;
        final int greenColor = ContextCompat.getColor(context, R.color.green);
        final int greyColor = ContextCompat.getColor(context, R.color.light_grey);

        scrollView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                scrollView.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                int scrollViewWidth = scrollView.getWidth();
                int centerOffset = scrollViewWidth / 2;
                container.setPadding(centerOffset, 0, centerOffset, 0);

                int closestIndex = -1;
                float minDiff = Float.MAX_VALUE;
                for (int i = 0; i < speedValues.size(); i++) {
                    float diff = Math.abs(speedValues.get(i) - finalCurrentSpeed);
                    if (diff < minDiff) {
                        minDiff = diff;
                        closestIndex = i;
                    }
                }

                if (closestIndex != -1 && closestIndex < container.getChildCount()) {
                    View targetView = container.getChildAt(closestIndex);
                    targetView.post(() -> {
                        int scrollX = (targetView.getLeft() + targetView.getRight()) / 2 - centerOffset;
                        scrollView.scrollTo(scrollX, 0);
                    });
                }
            }
        });

        scrollView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                int centerX = scrollView.getScrollX() + (scrollView.getWidth() / 2);
                int closestIndex = -1;
                int minDistance = Integer.MAX_VALUE;

                for (int i = 0; i < container.getChildCount(); i++) {
                    View child = container.getChildAt(i);
                    int childCenter = (child.getLeft() + child.getRight()) / 2;
                    int distance = Math.abs(centerX - childCenter);

                    if (distance < minDistance) {
                        minDistance = distance;
                        closestIndex = i;
                    }
                }

                if (closestIndex != -1) {
                    View targetView = container.getChildAt(closestIndex);
                    int scrollViewWidth = scrollView.getWidth();
                    int centerOffset = scrollViewWidth / 2;
                    int targetScrollX = (targetView.getLeft() + targetView.getRight()) / 2 - centerOffset;
                    scrollView.smoothScrollTo(targetScrollX, 0);
                    return true;
                }
            }
            return false;
        });

        scrollView.setOnScrollChangeListener((v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            int centerX = scrollX + (scrollView.getWidth() / 2);
            int closestIndex = -1;
            int minDistance = Integer.MAX_VALUE;

            for (int i = 0; i < container.getChildCount(); i++) {
                View child = container.getChildAt(i);
                int childCenter = (child.getLeft() + child.getRight()) / 2;
                int distance = Math.abs(centerX - childCenter);

                if (distance < minDistance) {
                    minDistance = distance;
                    closestIndex = i;
                }

                updateChildVisuals(child, false, greyColor, i);
            }

            if (closestIndex != -1) {
                View closestChild = container.getChildAt(closestIndex);
                updateChildVisuals(closestChild, true, greenColor, closestIndex);

                float selectedSpeed = speedValues.get(closestIndex);
                speedValueText.setText(String.format(Locale.US, "%.2f", selectedSpeed));

                if (lastSelectedSpeedIndex != closestIndex) {
                    Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(VibrationEffect.createOneShot(10, VibrationEffect.DEFAULT_AMPLITUDE));
                    }
                    lastSelectedSpeedIndex = closestIndex;
                }

                if (mainViewModel != null) {
                    mainViewModel.updatePlaybackSpeed(selectedSpeed);
                }
                prefs.edit().putFloat(SPEED_KEY, selectedSpeed).apply();
            }
        });
        dialog.setOnDismissListener(dialogInterface -> lastSelectedSpeedIndex = -1);
        dialog.show();
    }

    private void updateChildVisuals(View child, boolean isActive, int activeColor, int index) {
        if (child instanceof LinearLayout group) {
            View line = group.getChildAt(0);
            TextView text = (TextView) group.getChildAt(1);

            int valueInt = 25 + (index * 5);
            boolean isMajor = (valueInt % 10 == 0);
            boolean showText = (valueInt % 20 == 0);

            ViewGroup.LayoutParams params = line.getLayoutParams();
            if (isActive) {
                params.height = dpToPx(24);
                line.setLayoutParams(params);
                line.setBackgroundColor(activeColor);

                text.setVisibility(View.INVISIBLE);
            } else {
                params.height = isMajor ? dpToPx(16) : dpToPx(8);
                line.setLayoutParams(params);
                line.setBackgroundColor(Color.LTGRAY);

                if (showText) {
                    text.setVisibility(View.VISIBLE);
                    text.setTextColor(Color.LTGRAY);
                } else {
                    text.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private int dpToPx(int dp) {
        return (int) (dp * android.content.res.Resources.getSystem().getDisplayMetrics().density);
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
        TextView btnSpeed;

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
            btnSpeed = itemView.findViewById(R.id.btn_speed);

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