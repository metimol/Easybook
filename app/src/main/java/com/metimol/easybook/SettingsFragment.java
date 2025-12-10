package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.APP_PREFERENCES;
import static com.metimol.easybook.MainActivity.dpToPx;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.SwitchCompat;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

public class SettingsFragment extends Fragment {

    public static final String THEME_KEY = "theme_pref";
    public static final int THEME_LIGHT = 0;
    public static final int THEME_DARK = 1;
    public static final int THEME_AUTO = 2;

    public static final String SPEED_KEY = "playback_speed_pref";
    public static final String DOWNLOAD_TO_APP_FOLDER_KEY = "download_to_app_folder";
    public static final String FOLDER_NAMING_KEY = "folder_naming_pref";
    public static final int FOLDER_NAMING_TITLE = 0;
    public static final int FOLDER_NAMING_AUTHOR_TITLE = 1;
    public static final int FOLDER_NAMING_AUTHOR_TITLE_READER = 2;

    private SharedPreferences sharedPreferences;
    private RadioGroup themeRadioGroup;
    private SwitchCompat downloadLocationSwitch;
    private LinearLayout folderNamingContainer;
    private Spinner folderNamingSpinner;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        sharedPreferences = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);

        MainViewModel mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        ConstraintLayout settingsContainer = view.findViewById(R.id.settings_container);
        ImageView ivBack = view.findViewById(R.id.iv_back);
        themeRadioGroup = view.findViewById(R.id.theme_radio_group);
        downloadLocationSwitch = view.findViewById(R.id.switch_download_location);
        folderNamingContainer = view.findViewById(R.id.folder_naming_container);
        folderNamingSpinner = view.findViewById(R.id.folder_naming_spinner);

        setupFolderNamingSpinner();

        ivBack.setOnClickListener(v -> NavHostFragment.findNavController(this).popBackStack());

        mainViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            settingsContainer.setPaddingRelative(
                    settingsContainer.getPaddingStart(),
                    height + dpToPx(20, context),
                    settingsContainer.getPaddingEnd(),
                    settingsContainer.getPaddingBottom());
        });

        loadSettings();

        themeRadioGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int themeMode = THEME_AUTO;
            if (checkedId == R.id.theme_light) {
                themeMode = THEME_LIGHT;
            } else if (checkedId == R.id.theme_dark) {
                themeMode = THEME_DARK;
            }

            saveAndApplyTheme(themeMode);
        });

        downloadLocationSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean useAppFolder = !isChecked;
            sharedPreferences.edit().putBoolean(DOWNLOAD_TO_APP_FOLDER_KEY, useAppFolder).apply();
            folderNamingContainer.setVisibility(useAppFolder ? View.GONE : View.VISIBLE);
        });
    }

    private void setupFolderNamingSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(requireContext(),
                R.array.folder_naming_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        folderNamingSpinner.setAdapter(adapter);

        folderNamingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                sharedPreferences.edit().putInt(FOLDER_NAMING_KEY, position).apply();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
    }

    private void loadSettings() {
        int currentTheme = sharedPreferences.getInt(THEME_KEY, THEME_AUTO);
        if (currentTheme == THEME_LIGHT) {
            themeRadioGroup.check(R.id.theme_light);
        } else if (currentTheme == THEME_DARK) {
            themeRadioGroup.check(R.id.theme_dark);
        } else {
            themeRadioGroup.check(R.id.theme_auto);
        }

        boolean useAppFolder = sharedPreferences.getBoolean(DOWNLOAD_TO_APP_FOLDER_KEY, true);
        downloadLocationSwitch.setChecked(!useAppFolder);
        folderNamingContainer.setVisibility(useAppFolder ? View.GONE : View.VISIBLE);

        int folderNamingMode = sharedPreferences.getInt(FOLDER_NAMING_KEY, FOLDER_NAMING_TITLE);
        folderNamingSpinner.setSelection(folderNamingMode);
    }

    private void saveAndApplyTheme(int themeMode) {
        sharedPreferences.edit().putInt(THEME_KEY, themeMode).apply();

        if (themeMode == THEME_LIGHT) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        } else if (themeMode == THEME_DARK) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}