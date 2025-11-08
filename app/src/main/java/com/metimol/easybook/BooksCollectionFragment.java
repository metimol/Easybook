package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
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
    private String categoryId;
    private String categoryName;
    private FloatingActionButton fabScrollToTop;
    private View no_internet_view_collections;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            categoryId = getArguments().getString("categoryId");
            categoryName = getArguments().getString("categoryName");
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

        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        booksCollectionRecyclerView = view.findViewById(R.id.booksCollectionRecyclerView);
        fabScrollToTop = view.findViewById(R.id.fab_scroll_to_top_collections);
        no_internet_view_collections = view.findViewById(R.id.no_internet_view_collections);

        ImageView ivBack = view.findViewById(R.id.iv_collection_back);
        TextView tvTitle = view.findViewById(R.id.textViewCollectionTitle);
        ConstraintLayout collections_container = view.findViewById(R.id.collections_container);
        Button btn_retry_collections = view.findViewById(R.id.btn_retry_collections);

        Context context = requireContext();

        if (categoryName != null) {
            tvTitle.setText(categoryName);
        }
        ivBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        viewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
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
                requireView().findViewById(R.id.progressBarCollections).setVisibility(View.VISIBLE);
            } else {
                requireView().findViewById(R.id.progressBarCollections).setVisibility(View.GONE);
            }

            if (!isLoading) {
                Boolean isError = viewModel.getLoadError().getValue();
                if (isError != null && isError) {
                    showErrorView();
                } else {
                    showContent();
                }
            }
        });

        if (categoryId != null) {
            viewModel.fetchBooksByGenre(categoryId);
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
            if (categoryId != null) {
                viewModel.fetchBooksByGenre(categoryId);
            }
        });
    }

    private void showErrorView() {
        no_internet_view_collections.setVisibility(View.VISIBLE);
        booksCollectionRecyclerView.setVisibility(View.GONE);
    }

    private void showContent() {
        no_internet_view_collections.setVisibility(View.GONE);
        booksCollectionRecyclerView.setVisibility(View.VISIBLE);
    }

    private void setupRecyclerView() {
        bookAdapter = new BookAdapter();
        int spanCount = 3;
        booksCollectionRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));

        int spacingInPixels = dpToPx(12, requireContext());
        int edgeSpacingInPixels = dpToPx(0, requireContext());

        booksCollectionRecyclerView.addItemDecoration(new GridSpacingItemDecoration(
                spanCount,
                spacingInPixels,
                false,
                edgeSpacingInPixels
        ));

        booksCollectionRecyclerView.setAdapter(bookAdapter);

        booksCollectionRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
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
        });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewModel.resetBookList();
    }
}