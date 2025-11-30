package com.metimol.easybook;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.metimol.easybook.adapter.EpisodeAdapter;
import com.metimol.easybook.adapter.PlayerViewPagerAdapter;
import com.metimol.easybook.service.PlaybackService;

public class PlayerBottomSheetFragment extends BottomSheetDialogFragment {

    private MainViewModel viewModel;
    private PlaybackService playbackService;

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private PlayerViewPagerAdapter pagerAdapter;

    private View emptyPlayerView;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.CustomBottomSheetDialogTheme);
        viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_player_bottom_sheet, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewPager = view.findViewById(R.id.player_view_pager);
        tabLayout = view.findViewById(R.id.tab_indicator);
        emptyPlayerView = view.findViewById(R.id.empty_player_view);
        View dragHandle = view.findViewById(R.id.drag_handle);

        setupViewPager();
        setupTabLayout();

        viewModel.getPlaybackService().observe(getViewLifecycleOwner(), service -> {
            if (service == null) {
                showEmptyView(true);
                return;
            }
            this.playbackService = service;
            pagerAdapter.setPlaybackService(service);

            playbackService.currentBook.observe(getViewLifecycleOwner(), book -> {
                showEmptyView(book == null);
            });
        });
    }

    private void showEmptyView(boolean show) {
        if (show) {
            emptyPlayerView.setVisibility(View.VISIBLE);
            viewPager.setVisibility(View.GONE);
            tabLayout.setVisibility(View.GONE);
        } else {
            emptyPlayerView.setVisibility(View.GONE);
            viewPager.setVisibility(View.VISIBLE);
            tabLayout.setVisibility(View.VISIBLE);
        }
    }

    private void setupViewPager() {
        EpisodeAdapter episodeAdapter = new EpisodeAdapter();

        pagerAdapter = new PlayerViewPagerAdapter(this, episodeAdapter, () -> {
            if (viewPager != null) {
                viewPager.setCurrentItem(0, true);
            }
        });
        pagerAdapter.setMainViewModel(viewModel);
        viewPager.setAdapter(pagerAdapter);
    }

    private void setupTabLayout() {
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {}).attach();

        for (int i = 0; i < tabLayout.getTabCount(); i++) {
            View tab = ((ViewGroup) tabLayout.getChildAt(0)).getChildAt(i);
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) tab.getLayoutParams();
            p.setMargins(dpToPx(4, requireContext()), 0, dpToPx(4, requireContext()), 0);
            tab.setBackgroundResource(R.drawable.tab_selector);
            tab.requestLayout();
        }
    }


    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog dialog = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);

        dialog.setOnShowListener(dialogInterface -> {
            BottomSheetDialog d = (BottomSheetDialog) dialogInterface;
            FrameLayout bottomSheet = d.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null) {
                bottomSheet.setBackgroundResource(android.R.color.transparent);
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
                BottomSheetBehavior.from(bottomSheet).setSkipCollapsed(true);
            }
        });
        return dialog;
    }

    public static int dpToPx(float dp, Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}