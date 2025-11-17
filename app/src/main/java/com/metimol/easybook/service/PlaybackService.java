package com.metimol.easybook.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.RenderersFactory;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import com.metimol.easybook.R;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.BookFile;
import com.metimol.easybook.database.AppDatabase;
import com.metimol.easybook.database.AudiobookDao;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class PlaybackService extends MediaSessionService {
    private MediaSession mediaSession;
    private ExoPlayer player;

    private static final String CHANNEL_ID = "PlaybackServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    public final MutableLiveData<Book> currentBook = new MutableLiveData<>(null);
    public final MutableLiveData<BookFile> currentChapter = new MutableLiveData<>(null);
    public final MutableLiveData<Boolean> isPlaying = new MutableLiveData<>(false);
    public final MutableLiveData<Long> currentPosition = new MutableLiveData<>(0L);
    public final MutableLiveData<Long> totalDuration = new MutableLiveData<>(0L);
    public final MutableLiveData<Boolean> hasNext = new MutableLiveData<>(false);
    public final MutableLiveData<Boolean> hasPrevious = new MutableLiveData<>(false);

    private final Handler progressHandler = new Handler(Looper.getMainLooper());
    private Runnable progressUpdater;

    private final IBinder binder = new PlaybackBinder();

    private AudiobookDao audiobookDao;
    private ExecutorService databaseExecutor;

    public class PlaybackBinder extends Binder {
        public PlaybackService getService() {
            return PlaybackService.this;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(@NonNull Intent intent) {
        super.onBind(intent);
        return binder;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        audiobookDao = AppDatabase.getDatabase(this).audiobookDao();
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
                        bufferForPlaybackAfterRebufferMs
                )
                .build();

        RenderersFactory renderersFactory = new DefaultRenderersFactory(this);

        AudioAttributes audioAttributes = new AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                .build();

        player = new ExoPlayer.Builder(this)
                .setLoadControl(loadControl)
                .setRenderersFactory(renderersFactory)
                .setAudioAttributes(audioAttributes, true)
                .build();

        mediaSession = new MediaSession.Builder(this, player).build();

        player.addListener(new Player.Listener() {
            @Override
            public void onIsPlayingChanged(boolean playing) {
                isPlaying.postValue(playing);
                if (playing) {
                    startProgressUpdater();
                    startForeground(NOTIFICATION_ID, buildNotification());
                } else {
                    stopProgressUpdater();
                    saveCurrentBookProgress();
                    stopForeground(false);
                }
            }

            @Override
            public void onMediaItemTransition(@Nullable MediaItem mediaItem, int reason) {
                saveCurrentBookProgress();
                if (mediaItem != null && currentBook.getValue() != null) {
                    int newIndex = player.getCurrentMediaItemIndex();
                    List<BookFile> chapters = currentBook.getValue().getFiles().getFull();
                    if (newIndex >= 0 && newIndex < chapters.size()) {
                        BookFile chapter = chapters.get(newIndex);
                        currentChapter.setValue(chapter);
                        long duration = player.getDuration();
                        totalDuration.postValue(duration > 0 ? duration : 0L);

                        hasNext.postValue(player.hasNextMediaItem());
                        hasPrevious.postValue(player.hasPreviousMediaItem());

                        startForeground(NOTIFICATION_ID, buildNotification());
                    }
                }
            }

            @Override
            public void onPlaybackStateChanged(int playbackState) {
                if (playbackState == Player.STATE_READY) {
                    long duration = player.getDuration();
                    totalDuration.postValue(duration > 0 ? duration : 0L);
                    currentPosition.postValue(player.getCurrentPosition() > 0 ? player.getCurrentPosition() : 0L);
                    hasNext.postValue(player.hasNextMediaItem());
                    hasPrevious.postValue(player.hasPreviousMediaItem());
                } else if (playbackState == Player.STATE_ENDED) {
                    isPlaying.postValue(false);
                    stopProgressUpdater();
                    if (!player.hasNextMediaItem()) {
                        markCurrentBookAsFinished();
                    }
                }
            }
        });

        progressUpdater = new Runnable() {
            @Override
            public void run() {
                if (player.isPlaying()) {
                    long pos = player.getCurrentPosition();
                    currentPosition.postValue(pos > 0 ? pos : 0L);
                    progressHandler.postDelayed(this, 500);
                }
            }
        };
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

    @Override
    public void onDestroy() {
        stopProgressUpdater();
        saveCurrentBookProgress();
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
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }

    private Notification buildNotification() {
        MediaMetadata metadata = player.getMediaMetadata();
        String title = "Аудиокнига";
        String artist = "Easybook";

        if (metadata.title != null) {
            title = metadata.title.toString();
        }
        if (metadata.artist != null) {
            artist = metadata.artist.toString();
        }

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(artist)
                .setSmallIcon(R.drawable.ic_play)
                .setOngoing(true)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .build();
    }

    private void saveCurrentBookProgress() {
        Book book = currentBook.getValue();
        BookFile chapter = currentChapter.getValue();
        long position = player.getCurrentPosition();

        if (book == null || chapter == null || position < 0) {
            return;
        }

        String bookId = book.getId();
        String chapterId = String.valueOf(chapter.getId());
        long timestamp = (position > 0) ? position : 0L;
        long lastListened = System.currentTimeMillis();

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
                audiobookDao.insertBook(dbBook);
            } else {
                audiobookDao.updateBookProgress(bookId, chapterId, timestamp, lastListened, false);
            }
        });
    }

    private void markCurrentBookAsFinished() {
        Book book = currentBook.getValue();
        if (book == null) return;

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
                audiobookDao.insertBook(dbBook);
            } else {
                audiobookDao.updateFinishedStatus(bookId, true);
                audiobookDao.updateBookProgress(bookId, null, 0, System.currentTimeMillis(), true);
            }
        });
    }

    private void setupPlayerWithBook(Book book, int chapterIndex, long timestamp) {
        if (book == null || book.getFiles() == null || book.getFiles().getFull() == null) {
            return;
        }

        boolean isSameBook = book.getId().equals(currentBook.getValue() != null ? currentBook.getValue().getId() : null);

        currentBook.setValue(book);
        List<BookFile> chapters = book.getFiles().getFull();

        if (chapterIndex < 0 || chapterIndex >= chapters.size()) {
            chapterIndex = 0;
        }
        currentChapter.setValue(chapters.get(chapterIndex));

        if (!isSameBook) {
            List<MediaItem> mediaItems = new ArrayList<>();
            String artist = (book.getAuthors() != null && !book.getAuthors().isEmpty()) ?
                    book.getAuthors().get(0).getName() + " " + book.getAuthors().get(0).getSurname() : null;
            for (BookFile chapter : chapters) {
                MediaMetadata mediaMetadata = new MediaMetadata.Builder()
                        .setTitle(chapter.getTitle())
                        .setAlbumTitle(book.getName())
                        .setArtist(artist)
                        .build();
                MediaItem mediaItem = new MediaItem.Builder()
                        .setMediaId(String.valueOf(chapter.getId()))
                        .setUri(chapter.getUrl())
                        .setMediaMetadata(mediaMetadata)
                        .build();
                mediaItems.add(mediaItem);
            }
            player.setMediaItems(mediaItems, chapterIndex, timestamp);
            player.prepare();
        } else {
            player.seekTo(chapterIndex, timestamp);
            player.prepare();
        }
    }

    public void prepareBookFromProgress(Book book, int chapterIndex, long timestamp) {
        setupPlayerWithBook(book, chapterIndex, timestamp);
    }

    public void playBookFromProgress(Book book, int chapterIndex, long timestamp) {
        if (book == null || book.getFiles() == null || book.getFiles().getFull() == null) {
            return;
        }

        setupPlayerWithBook(book, chapterIndex, timestamp);

        final BookFile startingChapter = currentChapter.getValue();
        databaseExecutor.execute(() -> {
            String bookId = book.getId();
            String chapterId = String.valueOf(startingChapter.getId());
            long lastListened = System.currentTimeMillis();

            if (!audiobookDao.bookExists(bookId)) {
                com.metimol.easybook.database.Book dbBook = new com.metimol.easybook.database.Book();
                dbBook.id = bookId;
                dbBook.isFavorite = false;
                dbBook.isFinished = false;
                dbBook.currentChapterId = chapterId;
                dbBook.currentTimestamp = timestamp;
                dbBook.lastListened = lastListened;
                audiobookDao.insertBook(dbBook);
            } else {
                audiobookDao.updateFinishedStatus(bookId, false);
                audiobookDao.updateBookProgress(bookId, chapterId, timestamp, lastListened, false);
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
        }
    }

    public void skipToPrevious() {
        if (player.hasPreviousMediaItem()) {
            player.seekToPreviousMediaItem();
        }
    }

    public void seekTo(long position) {
        player.seekTo(position);
        currentPosition.postValue(position);
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
}