package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.metimol.easybook.adapter.CategoryAdapter;
import com.metimol.easybook.utils.GridSpacingItemDecoration;

public class CategoriesFragment extends Fragment {

    private MainViewModel viewModel;
    private RecyclerView categoriesRecyclerView;
    private CategoryAdapter categoryAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_categories, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        categoriesRecyclerView = view.findViewById(R.id.categoriesRecyclerView);

        viewModel.fetchCategories();

        MainViewModel mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        ConstraintLayout categories_container = view.findViewById(R.id.categories_container);
        ImageView iv_back = view.findViewById(R.id.iv_back);

        iv_back.setOnClickListener(v -> requireActivity().onBackPressed());

        mainViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            categories_container.setPaddingRelative(
                    categories_container.getPaddingStart(),
                    height + dpToPx(20, context),
                    categories_container.getPaddingEnd(),
                    categories_container.getPaddingBottom()
            );
        });

        setupRecyclerView();
        observeCategories();
    }

    private void setupRecyclerView() {
        categoryAdapter = new CategoryAdapter(R.layout.category_button_grid);

        categoriesRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        categoriesRecyclerView.setAdapter(categoryAdapter);

        int spanCount = 3;
        int spacing = (int) (20 * getResources().getDisplayMetrics().density);
        boolean includeEdge = false;
        int edgeSpacing = (int) (3 * getResources().getDisplayMetrics().density);

        categoriesRecyclerView.addItemDecoration(
                new GridSpacingItemDecoration(spanCount, spacing, includeEdge, edgeSpacing)
        );
    }

    private void observeCategories() {
        viewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null) {
                categoryAdapter.submitList(categories);
            }
        });
    }
}