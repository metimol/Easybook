package com.metimol.easybook.service;

import static com.metimol.easybook.MainActivity.APP_PREFERENCES;
import static com.metimol.easybook.SettingsFragment.SPEED_KEY;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.PlaybackParameters;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.okhttp.OkHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.ui.PlayerNotificationManager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;

import com.metimol.easybook.MainActivity;
import com.metimol.easybook.R;
import com.metimol.easybook.api.ApiClient;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.BookFile;
import com.metimol.easybook.database.AppDatabase;
import com.metimol.easybook.database.AudiobookDao;
import com.metimol.easybook.database.Chapter;
import com.metimol.easybook.firebase.FirebaseRepository;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlaybackService extends MediaSessionService {
    public static final int TIMER_OFF = 0;
    public static final int TIMER_5 = 5;
    public static final int TIMER_15 = 15;
    public static final int TIMER_30 = 30;
    public static final int TIMER_45 = 45;
    public static final int TIMER_60 = 60;
    public static final int TIMER_END_OF_CHAPTER = -1;

    private FirebaseRepository firebaseRepository;
    private MediaSession mediaSession;
    private ExoPlayer player;
    @OptIn(markerClass = UnstableApi.class)
    private PlayerNotificationManager notificationManager;

    private static final String CHANNEL_ID = "PlaybackServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public final MutableLiveData<Book> currentBook = new MutableLiveData<>(null);
    public final MutableLiveData<BookFile> currentChapter = new MutableLiveData<>(null);
    public final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    public final MutableLiveData<Long> currentPosition = new MutableLiveData<>(0L);
    public final MutableLiveData<Long> totalDuration = new MutableLiveData<>(0L);
    public final MutableLiveData<Boolean> hasNext = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> hasPrevious = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    public final MutableLiveData<Long> bufferedPosition = new MutableLiveData<>(0L);
    public final MutableLiveData<Float> playbackSpeed = new MutableLiveData<>(1.0f);

    public final MutableLiveData<Boolean> isSleepTimerActive = new MutableLiveData<>(false);
    public final MutableLiveData<Integer> activeSleepTimerMode = new MutableLiveData<>(TIMER_OFF);

    private long sleepTimerEndTime = 0;
    private boolean sleepTimerEndOfChapter = false;

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressUpdater;

    private final IBinder binder = new PlaybackBinder();

    private AudiobookDao audiobookDao;
    private ExecutorService databaseExecutor;
    private boolean isFinishCheckPending = false;

    private long lastPrevClickTime = 0;

    public class PlaybackBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        super.onBind(intent);
        return binder;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        audiobookDao = AppDatabase.getDatabase(this).audiobookDao();
        firebaseRepository = new FirebaseRepository(audiobookDao);
        databaseExecutor = Executors.newSingleThreadExecutor();

        int minBufferMs = 60_000;
        int maxBufferMs = 300_000;
        int bufferForPlaybackMs = 5_000;
        int bufferForPlaybackAfterRebufferMs = 10_000;

        LoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                        minBufferMs,
                        maxBufferMs,
                        bufferForPlaybackMs,
                        bufferForPlaybackAfterRebufferMs)
                .setBackBuffer(10000, true)
                .build();

        RenderersFactory renderersFactory = new DefaultRenderersFactory(this);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build();

        OkHttpDataSource.Factory okHttpFactory =
                new OkHttpDataSource.Factory(ApiClient.getOkHttpClient(this));

        DefaultDataSource.Factory dataSourceFactory =
                new DefaultDataSource.Factory(this, okHttpFactory);

        java.util.Map<String, String> defaultHeaders = new java.util.HashMap<>();
        defaultHeaders.put("Referer", ApiClient.REFERER);
        okHttpFactory.setDefaultRequestProperties(defaultHeaders);

        androidx.media3.exoplayer.source.DefaultMediaSourceFactory mediaSourceFactory =
                new androidx.media3.exoplayer.source.DefaultMediaSourceFactory(dataSourceFactory);

        player = new ExoPlayer.Builder(this)
                .setMediaSourceFactory(mediaSourceFactory)
                .setLoadControl(loadControl)
                .setRenderersFactory(renderersFactory)
                .setAudioAttributes(audioAttributes, true)
                .build();

        mediaSession = new MediaSession.Builder(this, player)
                .setId("EasyBookPlaybackSession")
                .build();

        notificationManager = new PlayerNotificationManager.Builder(
                this,
                NOTIFICATION_ID,
                CHANNEL_ID)
                .setMediaDescriptionAdapter(new PlayerNotificationManager.MediaDescriptionAdapter() {
                    @NonNull
                    @Override
                    public CharSequence getCurrentContentTitle(@NonNull Player player) {
                        MediaMetadata metadata = player.getMediaMetadata();
                        return Objects.requireNonNullElseGet(metadata.title, () -> getString(R.string.audiobook));
                    }

                    @Override
                    public PendingIntent createCurrentContentIntent(@NonNull Player player) {
                        Intent intent = new Intent(PlaybackService.this, MainActivity.class);
                        return PendingIntent.getActivity(
                                PlaybackService.this,
                                0,
                                intent,
                                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    }

                    @Override
                    public CharSequence getCurrentContentText(@NonNull Player player) {
                        MediaMetadata metadata = player.getMediaMetadata();
                        return Objects.requireNonNullElseGet(metadata.artist, () -> getString(R.string.app_name));
                    }

                    @Override
                    public CharSequence getCurrentSubText(@NonNull Player player) {
                        MediaMetadata metadata = player.getMediaMetadata();
                        if (metadata.albumTitle != null) {
                            return metadata.albumTitle;
                        }
                        return null;
                    }

                    @Nullable
                    @Override
                    public android.graphics.Bitmap getCurrentLargeIcon(
                            @NonNull Player player,
                            @NonNull PlayerNotificationManager.BitmapCallback callback) {
                        Book book = currentBook.getValue();
                        if (book != null && book.getDefaultPosterMain() != null) {
                            Glide.with(PlaybackService.this)
                                    .asBitmap()
                                    .load(book.getDefaultPosterMain())
                                    .into(new CustomTarget<Bitmap>() {
                                        @Override
                                        public void onResourceReady(@NonNull Bitmap resource,
                                                                    @Nullable Transition<? super Bitmap> transition) {
                                            callback.onBitmap(resource);
                                        }

                                        @Override
                                        public void onLoadCleared(@Nullable Drawable placeholder) {
                                        }
                                    });
                        }
                        return null;
                    }
                })
                .setNotificationListener(new PlayerNotificationManager.NotificationListener() {
                    @Override
                    public void onNotificationCancelled(int notificationId, boolean dismissedByUser) {
                        stopForeground(true);
                        stopSelf();
                    }

                    @Override
                    public void onNotificationPosted(
                            int notificationId,
                            @NonNull Notification notification,
                            boolean ongoing) {
                        if (ongoing) {
                            startForeground(notificationId, notification);
                        } else {
                            stopForeground(false);
                        }
                    }
                })
                .setSmallIconResourceId(R.drawable.ic_play)
                .build();

        notificationManager.setPlayer(player);
        notificationManager.setMediaSessionToken(mediaSession.getPlatformToken());
        notificationManager.setUseNextAction(true);
        notificationManager.setUsePreviousAction(true);
        notificationManager.setUsePlayPauseActions(true);
        notificationManager.setUseStopAction(false);

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean playing) {
                isPlaying.postValue(playing);
                if (playing) {
                    startProgressUpdater();
                    isLoading.postValue(false);
                } else {
                    stopProgressUpdater();

                    if (!player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED
                            && player.getPlaybackState() != Player.STATE_IDLE) {
                        long currentPos = player.getCurrentPosition();
                        long newPos = currentPos - 2000;
                        if (newPos < 0) {
                            newPos = 0;
                        }
                        player.seekTo(newPos);
                    }

                    saveCurrentBookProgress();
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                if (sleepTimerEndOfChapter && reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    player.pause();
                    cancelSleepTimer();
                    activeSleepTimerMode.postValue(TIMER_OFF);
                }

                saveCurrentBookProgress();
                if (mediaItem != null && currentBook.getValue() != null) {
                    int newIndex = player.getCurrentMediaItemIndex();
                    List<BookFile> chapters = currentBook.getValue().getFiles();
                    if (newIndex >= 0 && newIndex < chapters.size()) {
                        BookFile chapter = chapters.get(newIndex);
                        currentChapter.setValue(chapter);
                        long duration = player.getDuration();
                        totalDuration.postValue(duration > 0 ? duration : 0L);

                        updateNavigationVisibility();
                    }
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    long duration = player.getDuration();
                    totalDuration.postValue(duration > 0 ? duration : 0L);
                    currentPosition.postValue(player.getCurrentPosition() > 0 ? player.getCurrentPosition() : 0L);
                    bufferedPosition.postValue(player.getBufferedPosition() > 0 ? player.getBufferedPosition() : 0L);
                    updateNavigationVisibility();
                    isLoading.postValue(false);
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying.postValue(false);
                    stopProgressUpdater();
                    if (!player.hasNextMediaItem()) {
                        markCurrentBookAsFinished();
                    }
                    isLoading.postValue(false);

                    if (sleepTimerEndOfChapter) {
                        cancelSleepTimer();
                        activeSleepTimerMode.postValue(TIMER_OFF);
                    }
                } else if (playbackState == Player.STATE_BUFFERING) {
                    isLoading.postValue(true);
                } else if (playbackState == Player.STATE_IDLE) {
                    isLoading.postValue(false);
                }
            }
        });

        progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (player.isPlaying()) {
                    long pos = player.getCurrentPosition();
                    long bufferedPos = player.getBufferedPosition();
                    currentPosition.postValue(pos > 0 ? pos : 0L);
                    bufferedPosition.postValue(bufferedPos > 0 ? bufferedPos : 0L);

                    if (sleepTimerEndTime > 0 && System.currentTimeMillis() >= sleepTimerEndTime) {
                        player.pause();
                        cancelSleepTimer();
                        activeSleepTimerMode.postValue(TIMER_OFF);
                    }

                    checkBookFinishedCondition(pos);

                    progressHandler.postDelayed(this, 500);
                }
            }
        };

        SharedPreferences prefs = getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        float savedSpeed = prefs.getFloat(SPEED_KEY, 1.0f);
        setPlaybackSpeed(savedSpeed);
    }

    public void setSleepTimerMinutes(int minutes) {
        sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000L);
        sleepTimerEndOfChapter = false;
        isSleepTimerActive.postValue(true);
        activeSleepTimerMode.postValue(minutes);
    }

    public void setSleepTimerEndOfChapter() {
        sleepTimerEndTime = 0;
        sleepTimerEndOfChapter = true;
        isSleepTimerActive.postValue(true);
        activeSleepTimerMode.postValue(TIMER_END_OF_CHAPTER);
    }

    public void cancelSleepTimer() {
        sleepTimerEndTime = 0;
        sleepTimerEndOfChapter = false;
        isSleepTimerActive.postValue(false);
        activeSleepTimerMode.postValue(TIMER_OFF);
    }

    private void checkBookFinishedCondition(long currentPosition) {
        if (player == null || player.hasNextMediaItem() || isFinishCheckPending) {
            return;
        }

        long duration = player.getDuration();
        if (duration <= 0) {
            return;
        }

        long remainingTimeMs = duration - currentPosition;
        long threeMinutesMs = 180_000L;

        if (remainingTimeMs > 0 && remainingTimeMs <= threeMinutesMs) {
            markCurrentBookAsFinished();
        }
    }

    private void startProgressUpdater() {
        progressHandler.removeCallbacks(progressUpdater);
        progressHandler.post(progressUpdater);
    }

    private void stopProgressUpdater() {
        progressHandler.removeCallbacks(progressUpdater);
    }

    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo) {
        return mediaSession;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onDestroy() {
        stopProgressUpdater();
        saveCurrentBookProgress();

        if (notificationManager != null) {
            notificationManager.setPlayer(null);
        }

        if (player != null) {
            player.release();
            player = null;
        }

        if (mediaSession != null) {
            mediaSession.release();
            mediaSession = null;
        }

        super.onDestroy();
    }

    private void createNotificationChannel() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Playback Service Channel",
                NotificationManager.IMPORTANCE_LOW);
        serviceChannel.setDescription("Media playback controls");
        serviceChannel.setShowBadge(false);
        serviceChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);

        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private void saveCurrentBookProgress() {
        Book book = currentBook.getValue();
        BookFile chapter = currentChapter.getValue();
        long position = player.getCurrentPosition();

        if (book == null || chapter == null || position < 0) {
            return;
        }

        String bookId = book.getId();
        String chapterId = String.valueOf(chapter.getIndex());
        long timestamp = (position > 0) ? position : 0L;
        long lastListened = System.currentTimeMillis();

        int percentage = calculateProgressPercentage(book, chapter, timestamp);

        databaseExecutor.execute(() -> {
            boolean bookExists = audiobookDao.bookExists(bookId);
            if (!bookExists) {
                com.metimol.easybook.database.Book dbBook = new com.metimol.easybook.database.Book();
                dbBook.id = bookId;
                dbBook.isFavorite = false;
                dbBook.isFinished = false;
                dbBook.currentChapterId = chapterId;
                dbBook.currentTimestamp = timestamp;
                dbBook.lastListened = lastListened;
                dbBook.progressPercentage = percentage;
                audiobookDao.insertBook(dbBook);
            } else {
                audiobookDao.updateBookProgress(bookId, chapterId, timestamp, lastListened, false, percentage);
            }

            com.metimol.easybook.database.Book updatedBook = audiobookDao.getBookById(bookId);
            if (updatedBook != null) {
                firebaseRepository.updateBookInCloud(updatedBook);
            }
        });
    }

    private int calculateProgressPercentage(Book book, BookFile currentChapter, long positionMs) {
        if (book.getTotalDuration() <= 0 || book.getFiles() == null || book.getFiles().isEmpty()) {
            return 0;
        }

        long totalPlayedMs = 0;
        List<BookFile> files = book.getFiles();

        for (BookFile file : files) {
            if (file.getIndex() < currentChapter.getIndex()) {
                totalPlayedMs += file.getDuration() * 1000L;
            }
        }

        totalPlayedMs += positionMs;
        long totalBookDurationMs = book.getTotalDuration() * 1000L;

        if (totalBookDurationMs <= 0)
            return 0;

        int percent = (int) ((totalPlayedMs * 100) / totalBookDurationMs);
        return Math.min(Math.max(percent, 0), 100);
    }

    private void markCurrentBookAsFinished() {
        Book book = currentBook.getValue();
        if (book == null || isFinishCheckPending)
            return;
        isFinishCheckPending = true;

        String bookId = book.getId();
        databaseExecutor.execute(() -> {
            boolean bookExists = audiobookDao.bookExists(bookId);
            if (!bookExists) {
                com.metimol.easybook.database.Book dbBook = new com.metimol.easybook.database.Book();
                dbBook.id = bookId;
                dbBook.isFavorite = false;
                dbBook.isFinished = true;
                dbBook.currentChapterId = null;
                dbBook.currentTimestamp = 0;
                dbBook.lastListened = System.currentTimeMillis();
                dbBook.progressPercentage = 100;
                audiobookDao.insertBook(dbBook);
            } else {
                com.metimol.easybook.database.Book dbBook = audiobookDao.getBookById(bookId);
                if (dbBook != null && !dbBook.isFinished) {
                    audiobookDao.updateFinishedStatus(bookId, true, 100);
                    audiobookDao.updateBookProgress(bookId, null, 0, System.currentTimeMillis(), true, 100);
                }
            }

            com.metimol.easybook.database.Book updatedBook = audiobookDao.getBookById(bookId);
            if (updatedBook != null) {
                firebaseRepository.updateBookInCloud(updatedBook);
            }
        });
    }

    private void setupPlayerWithBook(Book book, int chapterIndex, long timestamp) {
        if (book == null || book.getFiles() == null || book.getFiles().isEmpty()) {
            return;
        }

        boolean isSameBook = book.getId()
                .equals(currentBook.getValue() != null ? currentBook.getValue().getId() : null);

        isFinishCheckPending = false;
        lastPrevClickTime = 0;

        currentBook.setValue(book);
        List<BookFile> chapters = book.getFiles();

        if (chapterIndex < 0 || chapterIndex >= chapters.size()) {
            chapterIndex = 0;
        }
        currentChapter.setValue(chapters.get(chapterIndex));

        if (!isSameBook) {
            int finalChapterIndex1 = chapterIndex;
            databaseExecutor.execute(() -> {
                List<Chapter> dbChapters = audiobookDao.getChaptersForBook(book.getId());
                List<MediaItem> mediaItems = new ArrayList<>();
                String artist = (book.getAuthors() != null && !book.getAuthors().isEmpty())
                        ? book.getAuthors().get(0).getName() + " " + book.getAuthors().get(0).getSurname()
                        : null;

                for (BookFile chapter : chapters) {
                    String localPath = null;
                    if (dbChapters != null) {
                        for (Chapter dbCh : dbChapters) {
                            if (chapter.getIndex() == dbCh.chapterIndex) {
                                localPath = dbCh.localPath;
                                break;
                            }
                        }
                    }

                    Uri mediaUri;
                    if (localPath != null && new File(localPath).exists()) {
                        mediaUri = Uri.fromFile(new File(localPath));
                    } else {
                        mediaUri = Uri.parse(chapter.getUrl());
                    }

                    MediaMetadata.Builder metadataBuilder = new MediaMetadata.Builder()
                            .setTitle(chapter.getTitle())
                            .setAlbumTitle(book.getName())
                            .setArtist(artist);

                    if (book.getDefaultPosterMain() != null) {
                        metadataBuilder.setArtworkUri(Uri.parse(book.getDefaultPosterMain()));
                    }

                    MediaMetadata mediaMetadata = metadataBuilder.build();
                    MediaItem mediaItem = new MediaItem.Builder()
                            .setMediaId(String.valueOf(chapter.getIndex()))
                            .setUri(mediaUri)
                            .setMediaMetadata(mediaMetadata)
                            .build();
                    mediaItems.add(mediaItem);
                }

                final int finalChapterIndex = finalChapterIndex1;
                new Handler(Looper.getMainLooper()).post(() -> {
                    player.setMediaItems(mediaItems, finalChapterIndex, timestamp);
                    player.prepare();
                });
            });
        } else {
            player.seekTo(chapterIndex, timestamp);
            player.prepare();
        }
    }

    public void prepareBookFromProgress(Book book, int chapterIndex, long timestamp) {
        setupPlayerWithBook(book, chapterIndex, timestamp);
    }

    public void playBookFromProgress(Book book, int chapterIndex, long timestamp) {
        if (book == null || book.getFiles() == null || book.getFiles().isEmpty()) {
            return;
        }

        setupPlayerWithBook(book, chapterIndex, timestamp);

        final BookFile startingChapter = currentChapter.getValue();
        databaseExecutor.execute(() -> {
            String bookId = book.getId();
            assert startingChapter != null;
            String chapterId = String.valueOf(startingChapter.getIndex());
            long lastListened = System.currentTimeMillis();

            int percentage = calculateProgressPercentage(book, startingChapter, timestamp);

            if (!audiobookDao.bookExists(bookId)) {
                com.metimol.easybook.database.Book dbBook = new com.metimol.easybook.database.Book();
                dbBook.id = bookId;
                dbBook.isFavorite = false;
                dbBook.isFinished = false;
                dbBook.currentChapterId = chapterId;
                dbBook.currentTimestamp = timestamp;
                dbBook.lastListened = lastListened;
                dbBook.progressPercentage = percentage;
                audiobookDao.insertBook(dbBook);
            } else {
                audiobookDao.updateFinishedStatus(bookId, false, percentage);
                audiobookDao.updateBookProgress(bookId, chapterId, timestamp, lastListened, false, percentage);
            }

            com.metimol.easybook.database.Book updatedBook = audiobookDao.getBookById(bookId);
            if (updatedBook != null) {
                firebaseRepository.updateBookInCloud(updatedBook);
            }
        });

        player.play();
        startService(new Intent(this, PlaybackService.class));
    }

    public void togglePlayPause() {
        if (player.isPlaying()) {
            player.pause();
        } else {
            if (player.getPlaybackState() == Player.STATE_IDLE || player.getPlaybackState() == Player.STATE_ENDED) {
                player.prepare();
            }
            player.play();
        }
    }

    public void skipToNext() {
        if (player.hasNextMediaItem()) {
            player.seekToNextMediaItem();
            player.play();
        } else {
            player.seekTo(player.getDuration());
        }
    }

    public void skipToPrevious() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastPrevClickTime < 500) {
            return;
        }
        lastPrevClickTime = currentTime;

        long position = player.getCurrentPosition();
        if (position > 5000) {
            seekTo(0);
        } else {
            if (player.hasPreviousMediaItem()) {
                player.seekToPreviousMediaItem();
                player.play();
            } else {
                seekTo(0);
            }
        }
    }

    public void seekTo(long position) {
        player.seekTo(position);
        currentPosition.postValue(position);
    }

    public void fastForward() {
        if (player != null) {
            long currentPosition = player.getCurrentPosition();
            long duration = player.getDuration();
            long newPosition = currentPosition + 10000;
            if (newPosition > duration) {
                newPosition = duration;
            }
            player.seekTo(newPosition);
            this.currentPosition.postValue(newPosition);
        }
    }

    public void rewind() {
        if (player != null) {
            long currentPosition = player.getCurrentPosition();
            long newPosition = currentPosition - 10000;
            if (newPosition < 0) {
                newPosition = 0;
            }
            player.seekTo(newPosition);
            this.currentPosition.postValue(newPosition);
        }
    }

    public static String formatDuration(long millis) {
        if (millis <= 0) {
            return "00:00";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;

        if (hours > 0) {
            return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
        }
    }

    private void updateNavigationVisibility() {
        Book book = currentBook.getValue();
        boolean showNav = false;
        if (book != null && book.getFiles() != null && !book.getFiles().isEmpty()) {
            showNav = book.getFiles().size() > 1;
        }
        hasNext.postValue(showNav);
        hasPrevious.postValue(showNav);
    }

    public void setPlaybackSpeed(float speed) {
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(speed, 1.0f));
            playbackSpeed.postValue(speed);
        }
    }
}