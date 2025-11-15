package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.APP_PREFERENCES;
import static com.metimol.easybook.MainActivity.dpToPx;
import static com.metimol.easybook.ProfileFragment.AVATAR_KEY;
import static com.metimol.easybook.ProfileFragment.USERNAME_KEY;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import de.hdodenhof.circleimageview.CircleImageView;

public class EditProfileFragment extends Fragment {

    private SharedPreferences sharedPreferences;
    private NavController navController;

    private ConstraintLayout editProfileContainer;
    private CircleImageView ivAvatar;
    private TextInputEditText etUsername;

    private Uri selectedAvatarUri;
    private ActivityResultLauncher<String> mGetContent;
    private boolean isNewAvatarSet = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mGetContent = registerForActivityResult(new ActivityResultContracts.GetContent(),
                new ActivityResultCallback<Uri>() {
                    @Override
                    public void onActivityResult(Uri uri) {
                        if (uri != null) {
                            selectedAvatarUri = uri;
                            isNewAvatarSet = true;
                            Glide.with(requireContext())
                                    .load(selectedAvatarUri)
                                    .placeholder(R.drawable.ic_default_avatar)
                                    .error(R.drawable.ic_default_avatar)
                                    .into(ivAvatar);
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_edit_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        MainViewModel mainViewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        FloatingActionButton fabEditAvatar = view.findViewById(R.id.fab_edit_avatar);
        ImageView ivBack = view.findViewById(R.id.iv_back);
        TextView tvSave = view.findViewById(R.id.tv_save);

        sharedPreferences = context.getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        navController = NavHostFragment.findNavController(this);
        editProfileContainer = view.findViewById(R.id.edit_profile_container);
        ivAvatar = view.findViewById(R.id.iv_avatar);
        etUsername = view.findViewById(R.id.et_username);

        mainViewModel.getStatusBarHeight().observe(getViewLifecycleOwner(), height -> {
            editProfileContainer.setPaddingRelative(
                    editProfileContainer.getPaddingStart(),
                    height + dpToPx(10, context),
                    editProfileContainer.getPaddingEnd(),
                    editProfileContainer.getPaddingBottom()
            );
        });

        loadUserData();

        ivBack.setOnClickListener(v -> navController.popBackStack());
        tvSave.setOnClickListener(v -> saveUserData());
        fabEditAvatar.setOnClickListener(v -> mGetContent.launch("image/*"));
    }

    private void loadUserData() {
        String username = sharedPreferences.getString(USERNAME_KEY, getString(R.string.default_username));
        String avatarUriString = sharedPreferences.getString(AVATAR_KEY, "");

        etUsername.setText(username);
        isNewAvatarSet = false;

        if (avatarUriString.isEmpty()) {
            selectedAvatarUri = null;
            ivAvatar.setImageResource(R.drawable.ic_default_avatar);
        } else {
            selectedAvatarUri = Uri.parse(avatarUriString);
            Glide.with(requireContext())
                    .load(selectedAvatarUri)
                    .placeholder(R.drawable.ic_default_avatar)
                    .error(R.drawable.ic_default_avatar)
                    .into(ivAvatar);
        }
    }

    private void saveUserData() {
        String newUsername = etUsername.getText().toString().trim();

        if (newUsername.isEmpty()) {
            newUsername = getString(R.string.default_username);
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(USERNAME_KEY, newUsername);

        if (isNewAvatarSet) {
            String newAvatarUriString;
            if (selectedAvatarUri != null) {
                newAvatarUriString = saveImageToInternalStorage(selectedAvatarUri);
            } else {
                newAvatarUriString = "";
            }

            if (newAvatarUriString != null) {
                editor.putString(AVATAR_KEY, newAvatarUriString);
            }
        }

        editor.apply();

        navController.popBackStack();
    }

    private String saveImageToInternalStorage(Uri uri) {
        try {
            InputStream inputStream = getContext().getContentResolver().openInputStream(uri);

            File internalFile = new File(getContext().getFilesDir(), "profile_pic.jpg");

            FileOutputStream outputStream = new FileOutputStream(internalFile);

            byte[] buffer = new byte[1024];
            int length;
            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }

            outputStream.close();
            inputStream.close();

            return internalFile.toURI().toString();

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}