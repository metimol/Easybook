package com.metimol.easybook.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.metimol.easybook.R;
import com.metimol.easybook.api.models.Author;

import java.util.Objects;

public class AuthorAdapter extends ListAdapter<Author, AuthorAdapter.AuthorViewHolder> {

    public interface OnAuthorClickListener {
        void onAuthorClick(Author author);
    }
    private OnAuthorClickListener clickListener;

    public void setOnAuthorClickListener(OnAuthorClickListener listener) {
        this.clickListener = listener;
    }

    public AuthorAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<Author> DIFF_CALLBACK = new DiffUtil.ItemCallback<Author>() {
        @Override
        public boolean areItemsTheSame(@NonNull Author oldItem, @NonNull Author newItem) {
            return oldItem.getId().equals(newItem.getId());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Author oldItem, @NonNull Author newItem) {
            return Objects.equals(oldItem.getName(), newItem.getName()) &&
                    Objects.equals(oldItem.getSurname(), newItem.getSurname());
        }
    };

    @NonNull
    @Override
    public AuthorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_serie, parent, false);
        return new AuthorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AuthorViewHolder holder, int position) {
        Author author = getItem(position);
        holder.bind(author);
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onAuthorClick(author);
            }
        });
    }

    static class AuthorViewHolder extends RecyclerView.ViewHolder {
        private final TextView authorName;
        private final TextView authorDetails;

        public AuthorViewHolder(@NonNull View itemView) {
            super(itemView);
            authorName = itemView.findViewById(R.id.serieName);
            authorDetails = itemView.findViewById(R.id.serieBooksCount);
        }

        public void bind(Author author) {
            String fullName = author.getName() + " " + author.getSurname();
            authorName.setText(fullName);
            authorDetails.setVisibility(View.GONE);
        }
    }
}