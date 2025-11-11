package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.google.android.material.tabs.TabLayout;
import com.metimol.easybook.adapter.EpisodeAdapter;
import com.metimol.easybook.api.models.Author;
import com.metimol.easybook.api.models.Book;

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

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        assert getArguments() != null;
        bookID = getArguments().getString("bookId");

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }

    @Nullable
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

        progressDrawable = new CircularProgressDrawable(requireContext());
        Context context = requireContext();

        progressDrawable.setStrokeWidth(5f);
        progressDrawable.setCenterRadius(30f);
        progressDrawable.start();

        ivBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        setupEpisodesRecyclerView();
        setupTabs();

        viewModel.getIsBookLoading().observe(getViewLifecycleOwner(), isLoading -> {
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        viewModel.getSelectedBookDetails().observe(getViewLifecycleOwner(), this::updateUI);

        if (viewModel.getSelectedBookDetails().getValue() == null ||
                !viewModel.getSelectedBookDetails().getValue().getId().equals(bookID)) {
            viewModel.fetchBookDetails(bookID);
        }

        sharedViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            book_info_main_container.setPaddingRelative(
                    book_info_main_container.getPaddingStart(),
                    height + dpToPx(20, context),
                    book_info_main_container.getPaddingEnd(),
                    book_info_main_container.getPaddingBottom()
            );
        });
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
            bookAuthor.setText(String.format("%s %s", author.getName(), author.getSurname()));
        } else {
            bookAuthor.setText(R.string.unknown);
        }

        if (book.getReaders() != null && !book.getReaders().isEmpty()) {
            Author reader = book.getReaders().get(0);
            bookReader.setText(String.format("%s %s", reader.getName(), reader.getSurname()));
        } else {
            bookReader.setText(R.string.unknown);
        }

        bookDuration.setText(formatTotalDuration(book.getTotalDuration()));

        if (book.getDescription() != null && !book.getDescription().isEmpty()) {
            bookDescription.setText(book.getDescription());
            bookDescription.setVisibility(View.VISIBLE);
        } else {
            bookDescription.setVisibility(View.GONE);
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

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewModel.clearBookDetails();
    }
}
