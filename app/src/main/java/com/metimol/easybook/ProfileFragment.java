package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.APP_PREFERENCES;
import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

public class ProfileFragment extends Fragment {
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        SharedPreferences sharedPreferences = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        NavController navController = NavHostFragment.findNavController(this);

        MainViewModel mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        ConstraintLayout profile_container = view.findViewById(R.id.profile_container);
        ConstraintLayout nav_main = view.findViewById(R.id.nav_main);

        mainViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            profile_container.setPaddingRelative(
                    profile_container.getPaddingStart(),
                    height + dpToPx(20, context),
                    profile_container.getPaddingEnd(),
                    profile_container.getPaddingBottom()
            );
        });

        nav_main.setOnClickListener(v -> navController.navigate(R.id.action_profileFragment_to_mainFragment));
    }
}
