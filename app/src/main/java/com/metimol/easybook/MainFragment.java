package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.APP_PREFERENCES;
import static com.metimol.easybook.MainActivity.dpToPx;

import java.util.Calendar;
import java.util.List;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.metimol.easybook.adapter.BookAdapter;
import com.metimol.easybook.adapter.CategoryAdapter;

public class MainFragment extends Fragment {
    TextView tvName;
    EditText search;
    public static final String IS_FIRST_START_KEY = "is_first_start";
    private RecyclerView shortCategoriesRecyclerView;
    private MainViewModel mainViewModel;
    private CategoryAdapter categoryAdapter;
    private BookAdapter bookAdapter;

    private final long SEARCH_DELAY = 500L;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        tvName = view.findViewById(R.id.tvName);
        search = view.findViewById(R.id.search);
        shortCategoriesRecyclerView = requireView().findViewById(R.id.shortCategoriesRecyclerView);

        ImageView clear_search = view.findViewById(R.id.clear_search);
        ConstraintLayout header = view.findViewById(R.id.header);
        TextView viewCategories = view.findViewById(R.id.viewCategories);

        Context context = requireContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        NavController navController = NavHostFragment.findNavController(this);

        if (sharedPreferences.getBoolean(IS_FIRST_START_KEY, true)) {
            navController.navigate(R.id.action_mainFragment_to_startScreenFragment);
        }

        mainViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            header.setPaddingRelative(
                    header.getPaddingStart(),
                    height + dpToPx(20, context),
                    header.getPaddingEnd(),
                    header.getPaddingBottom()
            );
        });

        clear_search.setOnClickListener(v -> search.setText(""));

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                String currentText = s.toString();
                if (currentText.isEmpty()) {
                    clear_search.setVisibility(View.GONE);
                } else {
                    clear_search.setVisibility(View.VISIBLE);
                }

                searchRunnable = () -> {
                    mainViewModel.searchBooks(currentText);
                };
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY);
            }
        });

        viewCategories.setOnClickListener(v -> navController.navigate(R.id.action_mainFragment_to_categoriesFragment));

        setupCategoriesRecyclerView();
        observeCategories();
        mainViewModel.fetchCategories();

        setupBooksRecyclerView();
        observeBooks();

        mainViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            requireView().findViewById(R.id.progressBar).setVisibility(isLoading ? View.VISIBLE : View.GONE);
        });

        mainViewModel.fetchBooks();
    }

    @Override
    public void onResume() {
        super.onResume();
        setGreetingText();
    }

    private void setupCategoriesRecyclerView() {
        categoryAdapter = new CategoryAdapter();
        shortCategoriesRecyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        shortCategoriesRecyclerView.setAdapter(categoryAdapter);
    }

    private void observeCategories() {
        mainViewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null && !categories.isEmpty()) {
                List<Category> shortList;
                if (categories.size() > 5) {
                    shortList = categories.subList(0, 5);
                } else {
                    shortList = categories;
                }
                categoryAdapter.submitList(shortList);
            }
        });
    }

    private void setupBooksRecyclerView() {
        bookAdapter = new BookAdapter();
        RecyclerView booksRecyclerView = requireView().findViewById(R.id.booksRecyclerView);
        booksRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        booksRecyclerView.setAdapter(bookAdapter);
        booksRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && layoutManager.findLastCompletelyVisibleItemPosition() == bookAdapter.getItemCount() - 1) {
                    mainViewModel.fetchBooks();
                }
            }
        });
    }

    private void observeBooks() {
        mainViewModel.getBooks().observe(getViewLifecycleOwner(), books -> {
            if (books != null) {
                bookAdapter.submitList(books);
            }
        });
    }

    private void setGreetingText() {
        if (tvName == null) {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        int hourOfDay = calendar.get(Calendar.HOUR_OF_DAY);
        int greetingResId;
        if (hourOfDay >= 5 && hourOfDay < 12) {
            greetingResId = R.string.good_morning;
        }
        else if (hourOfDay >= 12 && hourOfDay < 18) {
            greetingResId = R.string.good_afternoon;
        }
        else if (hourOfDay >= 18 && hourOfDay < 22) {
            greetingResId = R.string.good_evening;
        }
        else {
            greetingResId = R.string.good_night;
        }
        tvName.setText(greetingResId);
    }
}