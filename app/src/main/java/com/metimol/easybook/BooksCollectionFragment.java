package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.metimol.easybook.adapter.BookAdapter;
import com.metimol.easybook.utils.GridSpacingItemDecoration;

public class BooksCollectionFragment extends Fragment {

    private MainViewModel viewModel;
    private BookAdapter bookAdapter;
    private RecyclerView booksCollectionRecyclerView;
    private String categoryId;
    private String categoryName;

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

        ImageView ivBack = view.findViewById(R.id.iv_collection_back);
        TextView tvTitle = view.findViewById(R.id.textViewCollectionTitle);
        ConstraintLayout categoriesContainer = view.findViewById(R.id.categories_container);
        Context context = requireContext();

        if (categoryName != null) {
            tvTitle.setText(categoryName);
        }
        ivBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        viewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            categoriesContainer.setPaddingRelative(
                    categoriesContainer.getPaddingStart(),
                    height + dpToPx(20, context),
                    categoriesContainer.getPaddingEnd(),
                    categoriesContainer.getPaddingBottom()
            );
        });

        setupRecyclerView();
        observeBooks();
        // observeLoading(); // TODO: Add progressbar

        if (categoryId != null) {
            viewModel.fetchBooksByGenre(categoryId);
        }
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