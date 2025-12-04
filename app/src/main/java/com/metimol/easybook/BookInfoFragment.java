package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.metimol.easybook.adapter.EpisodeAdapter;
import com.metimol.easybook.api.models.Author;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.api.models.Serie;
import com.metimol.easybook.service.PlaybackService;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class BookInfoFragment extends Fragment {
    String bookID;
    private MainViewModel viewModel;
    private ImageView bookCover;
    private TextView bookTitle, bookAuthor, bookReader, bookDuration, bookDescription;
    private ProgressBar progressBar;
    private CircularProgressDrawable progressDrawable;
    private TabLayout tabLayout;
    private ScrollView infoContentScroll;
    private RecyclerView episodesRecycler;
    private EpisodeAdapter episodeAdapter;

    private ImageView ivAddBookToBookmarks;
    private FloatingActionButton playFab;

    private CardView bookSeriesCard;
    private TextView bookSeriesName;
    private TextView bookSeriesCount;

    private boolean isCurrentlyFinished = false;
    private PlaybackService playbackService;
    private com.metimol.easybook.database.Book currentDbBook;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assert getArguments() != null;
        bookID = getArguments().getString("bookId");

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_book_details, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ConstraintLayout book_info_main_container = view.findViewById(R.id.book_info_main_container);
        ImageView ivBack = view.findViewById(R.id.iv_collection_back);
        MainViewModel sharedViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        ImageView ivMore = view.findViewById(R.id.iv_more);

        bookCover = view.findViewById(R.id.book_cover);
        bookTitle = view.findViewById(R.id.bookTitle);
        bookAuthor = view.findViewById(R.id.bookAuthor);
        bookReader = view.findViewById(R.id.bookReader);
        progressBar = view.findViewById(R.id.progressBarBookDetails);
        bookDuration = view.findViewById(R.id.bookDuration);
        bookDescription = view.findViewById(R.id.bookDescription);
        tabLayout = view.findViewById(R.id.tabLayout);
        infoContentScroll = view.findViewById(R.id.info_content_scroll);
        episodesRecycler = view.findViewById(R.id.episodes_recycler);
        ivAddBookToBookmarks = view.findViewById(R.id.iv_add_book_to_bookmarks);
        playFab = view.findViewById(R.id.play);

        bookSeriesCard = view.findViewById(R.id.book_series_card);
        bookSeriesName = view.findViewById(R.id.book_series_name);
        bookSeriesCount = view.findViewById(R.id.book_series_count);

        progressDrawable = new CircularProgressDrawable(requireContext());
        Context context = requireContext();

        progressDrawable.setStrokeWidth(5f);
        progressDrawable.setCenterRadius(30f);
        progressDrawable.start();

        ivBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        setupEpisodesRecyclerView();
        setupTabs();

        viewModel.getPlaybackService().observe(getViewLifecycleOwner(), service -> {
            this.playbackService = service;
            if (service != null) {
                service.currentBook.observe(getViewLifecycleOwner(), playingBook -> {
                    updatePlayPauseButtonState();
                });
                service.isPlaying.observe(getViewLifecycleOwner(), isPlaying -> {
                    updatePlayPauseButtonState();
                });
            }
            updatePlayPauseButtonState();
        });

        viewModel.getIsBookLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getSelectedBookDetails().observe(getViewLifecycleOwner(), displayedBook -> {
            updateUI(displayedBook);
            updatePlayPauseButtonState();
        });

        if (viewModel.getSelectedBookDetails().getValue() == null ||
                !viewModel.getSelectedBookDetails().getValue().getId().equals(bookID)) {
            viewModel.fetchBookDetails(bookID);
        }

        viewModel.loadBookProgress(bookID);
        viewModel.getBookProgress().observe(getViewLifecycleOwner(), dbBook -> {
            this.currentDbBook = dbBook;
        });

        sharedViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            book_info_main_container.setPaddingRelative(
                    book_info_main_container.getPaddingStart(),
                    height + dpToPx(20, context),
                    book_info_main_container.getPaddingEnd(),
                    book_info_main_container.getPaddingBottom()
            );
        });

        observeFavoriteStatus();
        ivAddBookToBookmarks.setOnClickListener(v -> viewModel.toggleFavoriteStatus());

        observeFinishedStatus();
        ivMore.setOnClickListener(this::showBookOptionsMenu);

        playFab.setOnClickListener(v -> {
            Book displayedBook = viewModel.getSelectedBookDetails().getValue();
            if (playbackService == null || displayedBook == null) {
                return;
            }

            Book playingBook = playbackService.currentBook.getValue();
            Boolean isPlaying = playbackService.isPlaying.getValue();
            if (isPlaying == null) isPlaying = false;

            boolean isThisBookInPlayer = (playingBook != null && displayedBook.getId().equals(playingBook.getId()));

            if (isThisBookInPlayer) {
                playbackService.togglePlayPause();
                if (!isPlaying) {
                    new PlayerBottomSheetFragment().show(getParentFragmentManager(), "PlayerBottomSheet");
                }
            } else {
                int chapterIndex = 0;
                long timestamp = 0;

                if (currentDbBook != null && currentDbBook.currentChapterId != null && !currentDbBook.isFinished) {
                    timestamp = currentDbBook.currentTimestamp;
                    String chapterId = currentDbBook.currentChapterId;

                    if (displayedBook.getFiles() != null && displayedBook.getFiles().getFull() != null) {
                        for (int i = 0; i < displayedBook.getFiles().getFull().size(); i++) {
                            if (String.valueOf(displayedBook.getFiles().getFull().get(i).getId()).equals(chapterId)) {
                                chapterIndex = i;
                                break;
                            }
                        }
                    }
                }

                playbackService.playBookFromProgress(displayedBook, chapterIndex, timestamp);
                new PlayerBottomSheetFragment().show(getParentFragmentManager(), "PlayerBottomSheet");
            }
        });
    }

    private void observeFavoriteStatus() {
        viewModel.getIsBookFavorite(bookID).observe(getViewLifecycleOwner(), isFavorite -> {
            if (isFavorite != null && isFavorite) {
                int activeColor = ContextCompat.getColor(requireContext(), R.color.green);
                ivAddBookToBookmarks.setImageResource(R.drawable.ic_bookmark_added);
                ImageViewCompat.setImageTintList(ivAddBookToBookmarks, ColorStateList.valueOf(activeColor));
            } else {
                int notActiveColor = ContextCompat.getColor(requireContext(), R.color.black);
                ivAddBookToBookmarks.setImageResource(R.drawable.ic_bookmark);
                ImageViewCompat.setImageTintList(ivAddBookToBookmarks, ColorStateList.valueOf(notActiveColor));
            }
        });
    }

    private void observeFinishedStatus() {
        viewModel.getIsBookFinished(bookID).observe(getViewLifecycleOwner(), isFinished -> {
            isCurrentlyFinished = (isFinished != null && isFinished);
        });
    }

    private void showBookOptionsMenu(View anchorView) {
        PopupMenu popup = new PopupMenu(requireContext(), anchorView);
        popup.getMenuInflater().inflate(R.menu.book_options_menu, popup.getMenu());

        MenuItem item = popup.getMenu().findItem(R.id.action_toggle_listened);
        if (isCurrentlyFinished) {
            item.setTitle(R.string.remove_from_listened);
        } else {
            item.setTitle(R.string.mark_as_listened);
        }

        popup.setOnMenuItemClickListener(menuItem -> {
            if (menuItem.getItemId() == R.id.action_toggle_listened) {
                viewModel.toggleFinishedStatus();
                return true;
            }
            return false;
        });

        popup.show();
    }


    private String formatTotalDuration(int totalSeconds) {
        if (totalSeconds <= 0) {
            return getString(R.string.unknown);
        }
        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;

        String hoursString = getResources().getQuantityString(R.plurals.hours_count, (int) hours, (int) hours);
        String minutesString = getResources().getQuantityString(R.plurals.minutes_count, (int) minutes, (int) minutes);

        if (hours > 0 && minutes > 0) {
            return String.format("%s %s", hoursString, minutesString);
        } else if (hours > 0) {
            return hoursString;
        } else {
            return minutesString;
        }
    }

    private void updateUI(Book book) {
        if (book == null || !isAdded()) {
            return;
        }

        bookTitle.setText(book.getName());

        if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
            Author author = book.getAuthors().get(0);
            String authorName = String.format("%s %s", author.getName(), author.getSurname());
            bookAuthor.setText(authorName);

            bookAuthor.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putString("sourceType", "AUTHOR");
                bundle.putString("sourceId", author.getId());
                bundle.putString("sourceName", authorName);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_bookInfoFragment_to_booksCollectionFragment, bundle);
            });
        } else {
            bookAuthor.setText(R.string.unknown);
            bookAuthor.setOnClickListener(null);
        }

        if (book.getReaders() != null && !book.getReaders().isEmpty()) {
            Author reader = book.getReaders().get(0);
            String readerName = String.format("%s %s", reader.getName(), reader.getSurname());
            bookReader.setText(readerName);

            bookReader.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putString("sourceType", "READER");
                bundle.putString("sourceId", reader.getId());
                bundle.putString("sourceName", readerName);
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_bookInfoFragment_to_booksCollectionFragment, bundle);
            });
        } else {
            bookReader.setText(R.string.unknown);
            bookReader.setOnClickListener(null);
        }

        bookDuration.setText(formatTotalDuration(book.getTotalDuration()));

        if (book.getDescription() != null && !book.getDescription().isEmpty()) {
            bookDescription.setText(book.getDescription());
            bookDescription.setVisibility(View.VISIBLE);
        } else {
            bookDescription.setVisibility(View.GONE);
        }

        Serie serie = book.getSerie();
        if (serie != null) {
            bookSeriesCard.setVisibility(View.VISIBLE);
            bookSeriesName.setText(serie.getName());

            String booksCountText;
            try {
                booksCountText = getResources().getQuantityString(
                        R.plurals.books_count, serie.getBooksCount(), serie.getBooksCount()
                );
            } catch (Exception e) {
                booksCountText = serie.getBooksCount() + " " + getString(R.string.books_fallback);
            }
            bookSeriesCount.setText(booksCountText);

            bookSeriesCard.setOnClickListener(v -> {
                Bundle bundle = new Bundle();
                bundle.putString("sourceType", "SERIES");
                bundle.putString("sourceId", serie.getId());
                bundle.putString("sourceName", serie.getName());
                NavHostFragment.findNavController(this)
                        .navigate(R.id.action_bookInfoFragment_to_booksCollectionFragment, bundle);
            });

        } else {
            bookSeriesCard.setVisibility(View.GONE);
        }

        Glide.with(requireContext())
                .load(book.getDefaultPoster())
                .placeholder(progressDrawable)
                .error(R.drawable.ic_placeholder_book)
                .fallback(R.drawable.ic_placeholder_book)
                .into(bookCover);

        if (book.getFiles() != null && book.getFiles().getFull() != null) {
            episodeAdapter.submitList(book.getFiles().getFull());
        } else {
            episodeAdapter.submitList(new ArrayList<>());
        }
    }

    private void setupEpisodesRecyclerView() {
        episodeAdapter = new EpisodeAdapter();
        episodesRecycler.setLayoutManager(new LinearLayoutManager(getContext()));
        episodesRecycler.setAdapter(episodeAdapter);

        episodeAdapter.setOnEpisodeClickListener((episode, position) -> {
            Book book = viewModel.getSelectedBookDetails().getValue();
            if (playbackService != null && book != null) {
                playbackService.playBookFromProgress(book, position, 0L);
                new PlayerBottomSheetFragment().show(getParentFragmentManager(), "PlayerBottomSheet");
            }
        });
    }

    private void setupTabs() {
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                if (tab.getPosition() == 0) {
                    infoContentScroll.setVisibility(View.VISIBLE);
                    episodesRecycler.setVisibility(View.GONE);
                } else {
                    infoContentScroll.setVisibility(View.GONE);
                    episodesRecycler.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {}

            @Override
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void updatePlayPauseButtonState() {
        if (playFab == null || viewModel == null) {
            return;
        }

        playFab.setImageResource(R.drawable.ic_play);

        if (playbackService == null) {
            return;
        }

        Book displayedBook = viewModel.getSelectedBookDetails().getValue();
        Book playingBook = playbackService.currentBook.getValue();
        Boolean isPlaying = playbackService.isPlaying.getValue();

        if (isPlaying == null) {
            isPlaying = false;
        }

        if (displayedBook != null && playingBook != null && displayedBook.getId().equals(playingBook.getId())) {
            if (isPlaying) {
                playFab.setImageResource(R.drawable.ic_pause);
            } else {
                playFab.setImageResource(R.drawable.ic_play);
            }
        } else {
            playFab.setImageResource(R.drawable.ic_play);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewModel.clearBookDetails();
    }
}