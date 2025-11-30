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
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;

import com.metimol.easybook.adapter.BookAdapter;
import com.metimol.easybook.adapter.CategoryAdapter;
import com.metimol.easybook.adapter.SeriesAdapter;
import com.metimol.easybook.api.models.Book;
import com.metimol.easybook.utils.GridSpacingItemDecoration;
import com.metimol.easybook.utils.HorizontalSpacingItemDecoration;

public class MainFragment extends Fragment {
    TextView tvName;
    EditText search;
    private ConstraintLayout header;
    private RecyclerView shortCategoriesRecyclerView;
    private ConstraintLayout categoriesHeader;
    private MainViewModel mainViewModel;
    private CategoryAdapter categoryAdapter;
    private RecyclerView booksRecyclerView;
    private FloatingActionButton fabScrollToTop;
    private BookAdapter bookAdapter;

    private RecyclerView seriesRecyclerView;
    private SeriesAdapter seriesAdapter;
    private TextView seriesHeader;
    private TextView books_header;

    private CardView searchCard;
    private CoordinatorLayout coordinator;
    private View noInternetView;
    private View not_found_view;

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
        header = view.findViewById(R.id.header);
        tvName = view.findViewById(R.id.tvName);
        search = view.findViewById(R.id.search);
        shortCategoriesRecyclerView = requireView().findViewById(R.id.shortCategoriesRecyclerView);
        categoriesHeader = requireView().findViewById(R.id.categories_header);
        searchCard = view.findViewById(R.id.search_card);
        coordinator = view.findViewById(R.id.coordinator);
        noInternetView = view.findViewById(R.id.no_internet_view);
        not_found_view = view.findViewById(R.id.not_found_view);
        booksRecyclerView = requireView().findViewById(R.id.booksRecyclerView);
        fabScrollToTop = view.findViewById(R.id.fab_scroll_to_top);

        seriesRecyclerView = view.findViewById(R.id.seriesRecyclerView);
        seriesHeader = view.findViewById(R.id.series_header);
        books_header = view.findViewById(R.id.books_header);

        ConstraintLayout clMainFragment = view.findViewById(R.id.clMainFragment);
        Button btnRetry = view.findViewById(R.id.btn_retry);
        ImageView clear_search = view.findViewById(R.id.clear_search);
        TextView viewCategories = view.findViewById(R.id.viewCategories);
        ConstraintLayout nav_main = view.findViewById(R.id.nav_main);
        ConstraintLayout nav_profile = view.findViewById(R.id.nav_profile);
        ConstraintLayout nav_player = view.findViewById(R.id.nav_player);

        Context context = requireContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        NavController navController = NavHostFragment.findNavController(this);

        boolean isGuest = sharedPreferences.getBoolean(LoginFragment.IS_GUEST_KEY, false);

        if (FirebaseAuth.getInstance().getCurrentUser() == null && !isGuest) {
            navController.navigate(R.id.action_mainFragment_to_startScreenFragment);
            return;
        }

        if (header!=null) {
            mainViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
                header.setPaddingRelative(
                        header.getPaddingStart(),
                        height + dpToPx(20, context),
                        header.getPaddingEnd(),
                        header.getPaddingBottom()
                );
            });
        } else {
            mainViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
                clMainFragment.setPaddingRelative(
                        clMainFragment.getPaddingStart(),
                        height,
                        clMainFragment.getPaddingEnd(),
                        clMainFragment.getPaddingBottom()
                );
            });
        }

        categoriesHeader.setVisibility(View.GONE);
        shortCategoriesRecyclerView.setVisibility(View.GONE);

        clear_search.setOnClickListener(v -> search.setText(""));
        nav_main.setOnClickListener(v -> reloadPage());
        nav_profile.setOnClickListener(v -> navController.navigate(R.id.action_mainFragment_to_profileFragment));
        nav_player.setOnClickListener(v -> {
            PlayerBottomSheetFragment playerFragment = new PlayerBottomSheetFragment();
            playerFragment.show(getParentFragmentManager(), "PlayerBottomSheet");
        });

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
                    categoriesHeader.setVisibility(View.GONE);
                    shortCategoriesRecyclerView.setVisibility(View.GONE);
                }

                searchRunnable = () -> {
                    mainViewModel.searchBooks(currentText);
                };
                searchHandler.postDelayed(searchRunnable, SEARCH_DELAY);
            }
        });

        booksRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
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
            RecyclerView.LayoutManager layoutManager = booksRecyclerView.getLayoutManager();
            LinearLayoutManager linearLayoutManager = (LinearLayoutManager) layoutManager;

            assert linearLayoutManager != null;
            int firstVisibleItemPosition = linearLayoutManager.findFirstVisibleItemPosition();
            int scrollThreshold = 30;
            int jumpToPosition = 10;

            if (firstVisibleItemPosition > scrollThreshold) {
                booksRecyclerView.scrollToPosition(jumpToPosition);

                booksRecyclerView.post(() -> {
                    booksRecyclerView.smoothScrollToPosition(0);
                });

            } else {
                booksRecyclerView.smoothScrollToPosition(0);
            }
        });

        viewCategories.setOnClickListener(v -> navController.navigate(R.id.action_mainFragment_to_categoriesFragment));

        btnRetry.setOnClickListener(v -> {
            mainViewModel.fetchCategories();
            mainViewModel.loadMoreBooks();
        });

        setupCategoriesRecyclerView();
        observeCategories();
        mainViewModel.fetchCategories();

        setupSeriesRecyclerView();
        observeSeries();

        setupBooksRecyclerView();
        observeBooks();

        mainViewModel.getIsLoading().observe(getViewLifecycleOwner(), isLoading -> {
            List<Book> currentBooks = mainViewModel.getBooks().getValue();
            boolean isListEmpty = (currentBooks == null || currentBooks.isEmpty());

            if (isLoading && isListEmpty) {
                requireView().findViewById(R.id.progressBar).setVisibility(View.VISIBLE);
                categoriesHeader.setVisibility(View.GONE);
                shortCategoriesRecyclerView.setVisibility(View.GONE);
            } else {
                requireView().findViewById(R.id.progressBar).setVisibility(View.GONE);
            }

            if (!isLoading) {
                Boolean isError = mainViewModel.getLoadError().getValue();
                if (isError != null && isError) {
                    showErrorView();
                } else {
                    showContent();
                }
            }
        });

        if (mainViewModel.isGenreSearchActive()) {
            mainViewModel.resetBookList();
        } else if (mainViewModel.getBooks().getValue() == null || mainViewModel.getBooks().getValue().isEmpty()) {
            mainViewModel.loadMoreBooks();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        if (header!=null) {
            setGreetingText();
        }
    }

    private void setupCategoriesRecyclerView() {
        categoryAdapter = new CategoryAdapter(R.layout.category_button_horizontal);
        shortCategoriesRecyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        shortCategoriesRecyclerView.setAdapter(categoryAdapter);

        int spacingInPixels = (int) (8 * getResources().getDisplayMetrics().density);

        shortCategoriesRecyclerView.addItemDecoration(
                new HorizontalSpacingItemDecoration(spacingInPixels)
        );

        categoryAdapter.setOnCategoryClickListener(category -> {
            Bundle bundle = new Bundle();bundle.putString("sourceType", "GENRE");
            bundle.putString("sourceId", category.getId());
            bundle.putString("sourceName", category.getName());

            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_mainFragment_to_booksCollectionFragment, bundle);
        });
    }

    private void observeCategories() {
        mainViewModel.getCategories().observe(getViewLifecycleOwner(), categories -> {
            if (categories != null && !categories.isEmpty()) {
                List<Category> shortList;
                if (categories.size() > 6) {
                    shortList = categories.subList(0, 6);
                } else {
                    shortList = categories;
                }
                categoryAdapter.submitList(shortList);
            }
        });
    }

    private void setupSeriesRecyclerView() {
        seriesAdapter = new SeriesAdapter();
        seriesRecyclerView.setLayoutManager(
                new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false)
        );
        seriesRecyclerView.setAdapter(seriesAdapter);

        int spacingInPixels = (int) (8 * getResources().getDisplayMetrics().density);
        seriesRecyclerView.addItemDecoration(
                new HorizontalSpacingItemDecoration(spacingInPixels)
        );

        seriesAdapter.setOnSeriesClickListener(serie -> {
            Bundle bundle = new Bundle();
            bundle.putString("sourceType", "SERIES");
            bundle.putString("sourceId", serie.getId());
            bundle.putString("sourceName", serie.getName());

            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_mainFragment_to_booksCollectionFragment, bundle);
        });
    }

    private void observeSeries() {
        mainViewModel.getSeries().observe(getViewLifecycleOwner(), series -> {
            if (series != null && !series.isEmpty()) {
                seriesAdapter.submitList(series);
                seriesHeader.setVisibility(View.VISIBLE);
                seriesRecyclerView.setVisibility(View.VISIBLE);
            } else {
                seriesAdapter.submitList(null);
                seriesHeader.setVisibility(View.GONE);
                seriesRecyclerView.setVisibility(View.GONE);
            }

            updateNotFoundState();
        });
    }

    private void setupBooksRecyclerView() {
        bookAdapter = new BookAdapter();
        bookAdapter.setStateRestorationPolicy(RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY);
        RecyclerView.LayoutManager layoutManager = booksRecyclerView.getLayoutManager();

        int spanCount = 3;
        if (layoutManager != null) {
            spanCount = ((GridLayoutManager) layoutManager).getSpanCount();
        }

        int spacingInPixels = dpToPx(12, requireContext());
        int edgeSpacingInPixels = 0;

        booksRecyclerView.setLayoutManager(new GridLayoutManager(getContext(), spanCount));

        if (booksRecyclerView.getItemDecorationCount() == 0) {
            booksRecyclerView.addItemDecoration(new GridSpacingItemDecoration(
                    spanCount,
                    spacingInPixels,
                    false,
                    edgeSpacingInPixels
            ));
        }

        booksRecyclerView.setAdapter(bookAdapter);

        bookAdapter.setOnBookClickListener(book -> {
            Bundle bundle = new Bundle();
            bundle.putString("bookId", book.getId());
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_mainFragment_to_bookInfoFragment, bundle);
        });

        booksRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                GridLayoutManager layoutManager = (GridLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && layoutManager.findLastCompletelyVisibleItemPosition() == bookAdapter.getItemCount() - 1) {
                    mainViewModel.loadMoreBooks();
                }
            }
        });
    }

    private void observeBooks() {
        mainViewModel.getBooks().observe(getViewLifecycleOwner(), books -> {
            if (books != null) {
                bookAdapter.submitList(books);
                boolean isSearchActive = search.getText() != null && search.getText().length() > 0;

                if (isSearchActive) {
                    if (books.isEmpty()) {
                        books_header.setVisibility(View.GONE);
                        booksRecyclerView.setVisibility(View.GONE);
                    } else {
                        books_header.setVisibility(View.VISIBLE);
                        booksRecyclerView.setVisibility(View.VISIBLE);
                    }
                } else {
                    books_header.setVisibility(View.GONE);
                    booksRecyclerView.setVisibility(View.VISIBLE);
                }

                updateNotFoundState();
            }
        });
    }

    private void updateNotFoundState() {
        boolean isSearchActive = search.getText().length() > 0;

        if (isSearchActive) {
            List<?> books = mainViewModel.getBooks().getValue();
            List<?> series = mainViewModel.getSeries().getValue();

            boolean isBooksEmpty = (books == null || books.isEmpty());
            boolean isSeriesEmpty = (series == null || series.isEmpty());

            if (isBooksEmpty && isSeriesEmpty) {
                not_found_view.setVisibility(View.VISIBLE);
            } else {
                not_found_view.setVisibility(View.GONE);
            }
        } else {
            not_found_view.setVisibility(View.GONE);
        }
    }

    private void showContent() {
        searchCard.setVisibility(View.VISIBLE);
        coordinator.setVisibility(View.VISIBLE);
        noInternetView.setVisibility(View.GONE);

        if (search.getText().length() == 0) {
            categoriesHeader.setVisibility(View.VISIBLE);
            shortCategoriesRecyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showErrorView() {
        searchCard.setVisibility(View.GONE);
        coordinator.setVisibility(View.GONE);
        noInternetView.setVisibility(View.VISIBLE);
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

    public void reloadPage() {
        RecyclerView booksRecyclerView = requireView().findViewById(R.id.booksRecyclerView);
        if (booksRecyclerView != null) {
            booksRecyclerView.scrollToPosition(0);
        }

        if (shortCategoriesRecyclerView != null) {
            shortCategoriesRecyclerView.scrollToPosition(0);
        }

        if (seriesRecyclerView != null) {
            seriesRecyclerView.scrollToPosition(0);
        }

        if (search != null) {
            search.setText("");
        }

        if (mainViewModel != null) {
            mainViewModel.resetBookList();
        }
    }
}