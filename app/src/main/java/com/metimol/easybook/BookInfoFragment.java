package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.metimol.easybook.api.models.Author;
import com.metimol.easybook.api.models.Book;

public class BookInfoFragment extends Fragment {
    String bookID;
    private MainViewModel viewModel;
    private ImageView bookCover;
    private TextView bookTitle, bookAuthor, bookReader;
    private ProgressBar progressBar;
    private CircularProgressDrawable progressDrawable;

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
        progressDrawable = new CircularProgressDrawable(requireContext());

        Context context = requireContext();

        progressDrawable.setStrokeWidth(5f);
        progressDrawable.setCenterRadius(30f);
        progressDrawable.start();

        ivBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

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

        Glide.with(requireContext())
                .load(book.getDefaultPoster())
                .placeholder(progressDrawable)
                .error(R.drawable.ic_placeholder_book)
                .fallback(R.drawable.ic_placeholder_book)
                .into(bookCover);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        viewModel.clearBookDetails();
    }
}
