package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.metimol.easybook.adapter.BookAdapter;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.utils.GridSpacingItemDecoration;

import java.util.List;

public class BooksCollectionFragment extends Fragment {
    private MainViewModel viewModel;
    private BookAdapter bookAdapter;
    private RecyclerView booksCollectionRecyclerView;
    private FloatingActionButton fabScrollToTop;
    private View no_internet_view_collections;
    private View empty_collection_view;
    private ProgressBar progressBar;
    private Button btn_go_to_downloads;

    private String sourceId;
    private String sourceName;
    private String sourceType;

    private boolean isPaginationEnabled = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {

            if ("FAVORITES".equals(getArguments().getString("sourceType"))) {
                sourceType = "FAVORITES";
                sourceName = getString(R.string.bookmarks);
            } else if ("LISTENED".equals(getArguments().getString("sourceType"))) {
                sourceType = "LISTENED";
                sourceName = getString(R.string.listened);
            } else if ("LISTENING".equals(getArguments().getString("sourceType"))) {
                sourceType = "LISTENING";
                sourceName = getString(R.string.listen);
            } else if ("DOWNLOADED".equals(getArguments().getString("sourceType"))) {
                sourceType = "DOWNLOADED";
                sourceName = getString(R.string.downloaded);
            } else if (getArguments().containsKey("categoryId")) {
                sourceType = "GENRE";
                sourceId = getArguments().getString("categoryId");
                sourceName = getArguments().getString("categoryName");
            } else {
                sourceType = getArguments().getString("sourceType");
                sourceId = getArguments().getString("sourceId");
                sourceName = getArguments().getString("sourceName");
            }
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_books_collection, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        MainViewModel sharedViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        booksCollectionRecyclerView = view.findViewById(R.id.booksCollectionRecyclerView);
        fabScrollToTop = view.findViewById(R.id.fab_scroll_to_top_collections);
        no_internet_view_collections = view.findViewById(R.id.no_internet_view_collections);
        empty_collection_view = view.findViewById(R.id.empty_collection_view);
        progressBar = view.findViewById(R.id.progressBarCollections);
        btn_go_to_downloads = view.findViewById(R.id.btn_go_to_downloads);

        ImageView ivBack = view.findViewById(R.id.iv_collection_back);
        TextView tvTitle = view.findViewById(R.id.textViewCollectionTitle);
        ConstraintLayout collections_container = view.findViewById(R.id.collections_container);
        Button btn_retry_collections = view.findViewById(R.id.btn_retry_collections);

        Context context = requireContext();

        if (sourceName != null) {
            tvTitle.setText(sourceName);
        }
        ivBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        sharedViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            collections_container.setPaddingRelative(
                    collections_container.getPaddingStart(),
                    height + dpToPx(20, context),
                    collections_container.getPaddingEnd(),
                    collections_container.getPaddingBottom()
            );
        });

        setupRecyclerView();
        observeBooks();

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            List<Book> currentBooks = viewModel.getBooks().getValue();
            boolean isListEmpty = (currentBooks == null || currentBooks.isEmpty());

            if (isLoading && isListEmpty) {
                progressBar.setVisibility(View.VISIBLE);
                booksCollectionRecyclerView.setVisibility(View.GONE);
                no_internet_view_collections.setVisibility(View.GONE);
                empty_collection_view.setVisibility(View.GONE);
            } else {
                progressBar.setVisibility(View.GONE);
            }

            if (!isLoading) {
                updateVisibility();
            }
        });

        viewModel.getLoadError().observe(getViewLifecycleOwner(), isError -> {
            if (Boolean.FALSE.equals(viewModel.getIsLoading().getValue())) {
                updateVisibility();
            }
        });

        if (sourceType != null) {
            boolean isSameRequest = viewModel.isCurrentRequest(sourceType, sourceId);

            switch (sourceType) {
                case "GENRE" -> {
                    isPaginationEnabled = true;
                    if (!isSameRequest) viewModel.fetchBooksByGenre(sourceId);
                }
                case "SERIES" -> {
                    isPaginationEnabled = true;
                    if (!isSameRequest) viewModel.fetchBooksBySeries(sourceId);
                }
                case "AUTHOR" -> {
                    isPaginationEnabled = true;
                    if (!isSameRequest) viewModel.fetchBooksByAuthor(sourceId);
                }
                case "READER" -> {
                    isPaginationEnabled = true;
                    if (!isSameRequest) viewModel.fetchBooksByReader(sourceId);
                }
                case "FAVORITES" -> {
                    isPaginationEnabled = false;
                    if (!isSameRequest) viewModel.fetchFavoriteBooksFromApi();
                }
                case "LISTENED" -> {
                    isPaginationEnabled = false;
                    if (!isSameRequest) viewModel.fetchListenedBooksFromApi();
                }
                case "LISTENING" -> {
                    isPaginationEnabled = false;
                    if (!isSameRequest) viewModel.fetchListeningBooksFromApi();
                }
                case "DOWNLOADED" -> {
                    isPaginationEnabled = false;
                    if (!isSameRequest) viewModel.fetchDownloadedBooks();
                }
            }
        }

        booksCollectionRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy > 0 && fabScrollToTop.getVisibility() == View.VISIBLE) {
                    fabScrollToTop.hide();
                } else if (dy < 0 && fabScrollToTop.getVisibility() != View.VISIBLE) {
                    fabScrollToTop.show();
                }

                if (!recyclerView.canScrollVertically(-1) && fabScrollToTop.getVisibility() == View.VISIBLE) {
                    fabScrollToTop.hide();
                }
            }
        });

        fabScrollToTop.setOnClickListener(v -> {
            RecyclerView.LayoutManager layoutManager = booksCollectionRecyclerView.getLayoutManager();
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;

            assert linearLayoutManager != null;
            int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
            int scrollThreshold = 30;
            int jumpToPosition = 10;

            if (firstVisibleItemPosition > scrollThreshold) {
                booksCollectionRecyclerView.scrollToPosition(jumpToPosition);

                booksCollectionRecyclerView.post(() -> {
                    booksCollectionRecyclerView.smoothScrollToPosition(0);
                });

            } else {
                booksCollectionRecyclerView.smoothScrollToPosition(0);
            }
        });

        btn_retry_collections.setOnClickListener(v -> {
            if (sourceType != null) {
                switch (sourceType) {
                    case "GENRE" -> viewModel.fetchBooksByGenre(sourceId);
                    case "SERIES" -> viewModel.fetchBooksBySeries(sourceId);
                    case "AUTHOR" -> viewModel.fetchBooksByAuthor(sourceId);
                    case "READER" -> viewModel.fetchBooksByReader(sourceId);
                    case "FAVORITES" -> viewModel.fetchFavoriteBooksFromApi();
                    case "LISTENED" -> viewModel.fetchListenedBooksFromApi();
                    case "LISTENING" -> viewModel.fetchListeningBooksFromApi();
                    case "DOWNLOADED" -> viewModel.fetchDownloadedBooks();
                }
            }
        });

        btn_go_to_downloads.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("sourceType", "DOWNLOADED");
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_booksCollectionFragment_to_booksCollectionFragment, bundle);
        });
    }

    private void updateVisibility() {
        if (Boolean.TRUE.equals(viewModel.getIsLoading().getValue()) &&
                (viewModel.getBooks().getValue() == null || viewModel.getBooks().getValue().isEmpty())) {
            return;
        }

        Boolean isError = viewModel.getLoadError().getValue();
        List<Book> currentBooks = viewModel.getBooks().getValue();
        boolean isListEmpty = (currentBooks == null || currentBooks.isEmpty());

        if (isError != null && isError) {
            no_internet_view_collections.setVisibility(View.VISIBLE);
            booksCollectionRecyclerView.setVisibility(View.GONE);
            empty_collection_view.setVisibility(View.GONE);
            if("DOWNLOADED".equals(sourceType)) {
                btn_go_to_downloads.setVisibility(View.GONE);
            } else {
                btn_go_to_downloads.setVisibility(View.VISIBLE);
            }
        } else if (isListEmpty) {
            no_internet_view_collections.setVisibility(View.GONE);
            booksCollectionRecyclerView.setVisibility(View.GONE);
            empty_collection_view.setVisibility(View.VISIBLE);
        } else {
            no_internet_view_collections.setVisibility(View.GONE);
            booksCollectionRecyclerView.setVisibility(View.VISIBLE);
            empty_collection_view.setVisibility(View.GONE);
        }
    }

    private void setupRecyclerView() {
        bookAdapter = new BookAdapter();
        bookAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);

        int spanCount = 0;
        RecyclerView.LayoutManager manager = booksCollectionRecyclerView.getLayoutManager();
        if (manager instanceof GridLayoutManager) {
            spanCount = ((GridLayoutManager) manager).getSpanCount();
        }
        if (spanCount == 0) {
            spanCount = 3;
        }
        booksCollectionRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));

        int spacingInPixels = dpToPx(12, requireContext());
        int edgeSpacingInPixels = dpToPx(0, requireContext());

        if (booksCollectionRecyclerView.getItemDecorationCount() == 0) {
            booksCollectionRecyclerView.addItemDecoration(new GridSpacingItemDecoration(
                    spanCount,
                    spacingInPixels,
                    false,
                    edgeSpacingInPixels
            ));
        }

        booksCollectionRecyclerView.setAdapter(bookAdapter);

        bookAdapter.setOnBookClickListener(book -> {
            Bundle bundle = new Bundle();
            bundle.putString("bookId", book.getId());
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_booksCollectionFragment_to_bookInfoFragment, bundle);
        });

        booksCollectionRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (!isPaginationEnabled) {
                    return;
                }

                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && layoutManager.findLastCompletelyVisibleItemPosition() == bookAdapter.getItemCount() - 1) {
                    viewModel.loadMoreBooks();
                }
            }
        });
    }

    private void observeBooks() {
        viewModel.getBooks().observe(getViewLifecycleOwner(), books -> {
            if (books != null) {
                bookAdapter.submitList(books);
            }
            updateVisibility();
        });
    }
}