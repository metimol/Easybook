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
import com.metimol.easybook.api.models.BookFile;

import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

public class EpisodeAdapter extends ListAdapter<BookFile, EpisodeAdapter.EpisodeViewHolder> {

    public EpisodeAdapter() {
        super(DIFF_CALLBACK);
    }

    private static final DiffUtil.ItemCallback<BookFile> DIFF_CALLBACK = new DiffUtil.ItemCallback<BookFile>() {
        @Override
        public boolean areItemsTheSame(@NonNull BookFile oldItem, @NonNull BookFile newItem) {
            return oldItem.getId() == newItem.getId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull BookFile oldItem, @NonNull BookFile newItem) {
            return Objects.equals(oldItem.getTitle(), newItem.getTitle()) &&
                    oldItem.getDuration() == newItem.getDuration();
        }
    };

    @NonNull
    @Override
    public EpisodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_episode, parent, false);
        return new EpisodeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EpisodeViewHolder holder, int position) {
        BookFile episode = getItem(position);
        holder.bind(episode);
    }

    static class EpisodeViewHolder extends RecyclerView.ViewHolder {
        private final TextView episodeIndex;
        private final TextView episodeTitle;
        private final TextView episodeDuration;

        public EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
            episodeIndex = itemView.findViewById(R.id.episodeIndex);
            episodeTitle = itemView.findViewById(R.id.episodeTitle);
            episodeDuration = itemView.findViewById(R.id.episodeDuration);
        }

        public void bind(BookFile episode) {
            episodeIndex.setText(String.format(Locale.getDefault(), "%d.", episode.getIndex() + 1));
            episodeTitle.setText(episode.getTitle());
            episodeDuration.setText(formatDuration(episode.getDuration()));
        }

        private String formatDuration(int totalSeconds) {
            if (totalSeconds <= 0) {
                return "00:00";
            }
            long hours = TimeUnit.SECONDS.toHours(totalSeconds);
            long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
            long seconds = totalSeconds % 60;

            if (hours > 0) {
                return String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds);
            } else {
                return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
            }
        }
    }
}