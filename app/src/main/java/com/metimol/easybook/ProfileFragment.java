package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.APP_PREFERENCES;
import static com.metimol.easybook.LoginFragment.IS_GUEST_KEY;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
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
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

public class ProfileFragment extends Fragment {
    public static final String USERNAME_KEY = "username";
    public static final String AVATAR_KEY = "avatar";
    private SharedPreferences sharedPreferences;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        sharedPreferences = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        NavController navController = NavHostFragment.findNavController(this);

        MainViewModel mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        ConstraintLayout profile_container = view.findViewById(R.id.profile_container);
        ConstraintLayout nav_main = view.findViewById(R.id.nav_main);
        ConstraintLayout nav_player = view.findViewById(R.id.nav_player);
        TextView editInfo = view.findViewById(R.id.edit_info);
        ConstraintLayout share = view.findViewById(R.id.share);
        ConstraintLayout rateUs = view.findViewById(R.id.rateUs);
        ConstraintLayout settings = view.findViewById(R.id.settings);
        ConstraintLayout logout = view.findViewById(R.id.logout);

        ConstraintLayout bookmarks = view.findViewById(R.id.bookmarks);
        ConstraintLayout listening = view.findViewById(R.id.listen);
        ConstraintLayout listened = view.findViewById(R.id.listened);

        boolean isGuest = sharedPreferences.getBoolean(IS_GUEST_KEY, false);

        mainViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            profile_container.setPaddingRelative(
                    profile_container.getPaddingStart(),
                    height,
                    profile_container.getPaddingEnd(),
                    profile_container.getPaddingBottom()
            );
        });

        nav_main.setOnClickListener(v -> navController.navigate(R.id.action_profileFragment_to_mainFragment));
        nav_player.setOnClickListener(v -> {
            PlayerBottomSheetFragment playerFragment = new PlayerBottomSheetFragment();
            playerFragment.show(getParentFragmentManager(), "PlayerBottomSheet");
        });
        editInfo.setOnClickListener(v -> navController.navigate(R.id.action_profileFragment_to_EditProfileFragment));
        share.setOnClickListener(v -> shareInfo(context, getString(R.string.share_text)));
        rateUs.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/metimol/Easybook"))));

        settings.setOnClickListener(v -> {
            navController.navigate(R.id.action_profileFragment_to_SettingsFragment);
        });

        bookmarks.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("sourceType", "FAVORITES");
            navController.navigate(R.id.action_profileFragment_to_booksCollectionFragment, bundle);
        });

        listening.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("sourceType", "LISTENING");
            navController.navigate(R.id.action_profileFragment_to_booksCollectionFragment, bundle);
        });

        listened.setOnClickListener(v -> {
            Bundle bundle = new Bundle();
            bundle.putString("sourceType", "LISTENED");
            navController.navigate(R.id.action_profileFragment_to_booksCollectionFragment, bundle);
        });

        logout.setOnClickListener(v -> {
            mainViewModel.logout();

            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.remove(USERNAME_KEY);
            editor.remove(AVATAR_KEY);
            editor.remove(IS_GUEST_KEY);
            editor.apply();

            navController.navigate(R.id.action_profileFragment_to_loginFragment, null,
                    new androidx.navigation.NavOptions.Builder()
                            .setPopUpTo(R.id.nav_graph, true)
                            .build());
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        ImageView avatar = requireView().findViewById(R.id.userAvatar);
        TextView username = requireView().findViewById(R.id.userName);

        if (sharedPreferences.getString(AVATAR_KEY, "").isEmpty()) {
            avatar.setImageResource(R.drawable.ic_default_avatar);
        } else {
            avatar.setImageURI(Uri.parse(sharedPreferences.getString(AVATAR_KEY, "")));
        }

        username.setText(sharedPreferences.getString(USERNAME_KEY, ""));
    }

    private void shareInfo(Context context, String textToShare) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, textToShare);
        sendIntent.setType("text/plain");

        Intent shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_title));
        context.startActivity(shareIntent);
    }
}