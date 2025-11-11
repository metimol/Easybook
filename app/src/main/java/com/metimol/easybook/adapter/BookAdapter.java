package com.metimol.easybook.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.metimol.easybook.R;
import com.metimol.easybook.api.models.Book;

import java.util.Objects;

public class BookAdapter extends ListAdapter<Book, BookAdapter.BookViewHolder> {

    public BookAdapter() {
        super(DIFF_CALLBACK);
    }

    public interface OnBookClickListener {
        void onBookClick(Book book);
    }

    private OnBookClickListener clickListener;

    public void setOnBookClickListener(OnBookClickListener listener) {
        this.clickListener = listener;
    }

    private static final DiffUtil.ItemCallback<Book> DIFF_CALLBACK = new DiffUtil.ItemCallback<Book>() {
        @Override
        public boolean areItemsTheSame(@NonNull Book oldItem, @NonNull Book newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Book oldItem, @NonNull Book newItem) {
            return Objects.equals(oldItem.getName(), newItem.getName());
        }
    };

    @NonNull
    @Override
    public BookViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_book, parent, false);
        return new BookViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BookViewHolder holder, int position) {
        Book book = getItem(position);
        holder.bind(book);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onBookClick(book);
            }
        });
    }

    static class BookViewHolder extends RecyclerView.ViewHolder {
        private final ImageView bookCover;
        private final TextView bookTitle;
        private final TextView bookAuthor;
        private final TextView bookReader;
        private final CircularProgressDrawable progressDrawable;

        public BookViewHolder(@NonNull View itemView) {
            super(itemView);
            bookCover = itemView.findViewById(R.id.bookCover);
            bookTitle = itemView.findViewById(R.id.bookTitle);
            bookAuthor = itemView.findViewById(R.id.bookAuthor);
            bookReader = itemView.findViewById(R.id.bookReader);

            progressDrawable = new CircularProgressDrawable(itemView.getContext());
            progressDrawable.setStrokeWidth(5f);
            progressDrawable.setCenterRadius(30f);
            progressDrawable.start();
        }

        public void bind(Book book) {
            bookTitle.setText(book.getName());

            if (book.getAuthors() != null && !book.getAuthors().isEmpty()) {
                String Author = book.getAuthors().get(0).getName() + " " + book.getAuthors().get(0).getSurname();
                bookAuthor.setText(Author);
            } else {
                bookAuthor.setText(itemView.getContext().getString(R.string.unknown));
            }

            if (book.getReaders() != null && !book.getReaders().isEmpty()) {
                String Reader = book.getReaders().get(0).getName() + " " + book.getReaders().get(0).getSurname();
                bookReader.setText(Reader);
            } else {
                bookReader.setText(itemView.getContext().getString(R.string.unknown));
            }

            Glide.with(itemView.getContext())
                    .load(book.getDefaultPoster())
                    .placeholder(progressDrawable)
                    .error(R.drawable.ic_placeholder_book)
                    .fallback(R.drawable.ic_placeholder_book)
                    .into(bookCover);
        }
    }
}