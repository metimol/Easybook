package com.metimol.easybook;

import static com.metimol.easybook.MainActivity.APP_PREFERENCES;
import static com.metimol.easybook.ProfileFragment.AVATAR_KEY;
import static com.metimol.easybook.ProfileFragment.USERNAME_KEY;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseUser;
import com.metimol.easybook.database.AppDatabase;
import com.metimol.easybook.firebase.FirebaseRepository;

public class LoginFragment extends Fragment {

    public static final String IS_GUEST_KEY = "is_guest";

    private FirebaseRepository firebaseRepository;
    private ActivityResultLauncher<Intent> signInLauncher;
    private ProgressBar progressBar;
    private NavController navController;
    private CardView btnGoogle;
    private CardView btnGithub;
    private TextView btnAnonymous;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        firebaseRepository = new FirebaseRepository(AppDatabase.getDatabase(requireContext()).audiobookDao());

        signInLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(result.getData());
                    handleSignInResult(task);
                }
        );
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_login, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        navController = NavHostFragment.findNavController(this);
        btnGoogle = view.findViewById(R.id.btn_google);
        btnGithub = view.findViewById(R.id.btn_github);
        btnAnonymous = view.findViewById(R.id.btn_anonymous);
        progressBar = view.findViewById(R.id.login_progress);

        btnGoogle.setOnClickListener(v -> signInWithGoogle());
        btnGithub.setOnClickListener(v -> signInWithGitHub());
        btnAnonymous.setOnClickListener(v -> signInAnonymously());
    }

    private void signInWithGoogle() {
        progressBar.setVisibility(View.VISIBLE);
        btnGoogle.setVisibility(View.GONE);
        btnGithub.setVisibility(View.GONE);
        btnAnonymous.setVisibility(View.GONE);

        Intent signInIntent = firebaseRepository.getGoogleSignInClient(requireContext()).getSignInIntent();
        signInLauncher.launch(signInIntent);
    }

    private void signInWithGitHub() {
        progressBar.setVisibility(View.VISIBLE);
        btnGoogle.setVisibility(View.GONE);
        btnGithub.setVisibility(View.GONE);
        btnAnonymous.setVisibility(View.GONE);

        firebaseRepository.signInWithGitHub(requireActivity(),
                this::updateUIAndNavigate,
                e -> {
                    progressBar.setVisibility(View.GONE);
                    btnGoogle.setVisibility(View.VISIBLE);
                    btnGithub.setVisibility(View.VISIBLE);
                    btnAnonymous.setVisibility(View.VISIBLE);

                    if (e instanceof FirebaseAuthUserCollisionException) {
                        Toast.makeText(requireContext(), getString(R.string.email_exist), Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.error_auth_firebase), Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void signInAnonymously() {
        SharedPreferences prefs = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean(IS_GUEST_KEY, true);
        editor.putString(USERNAME_KEY, getString(R.string.default_username));
        editor.putString(AVATAR_KEY, "");
        editor.apply();

        navController.navigate(R.id.action_loginFragment_to_mainFragment);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            firebaseRepository.firebaseAuthWithGoogle(account.getIdToken(),
                    this::updateUIAndNavigate,
                    e -> {
                        progressBar.setVisibility(View.GONE);
                        btnGoogle.setVisibility(View.VISIBLE);
                        btnGithub.setVisibility(View.VISIBLE);
                        btnAnonymous.setVisibility(View.VISIBLE);

                        Toast.makeText(requireContext(), getString(R.string.error_auth_firebase), Toast.LENGTH_SHORT).show();
                    }
            );
        } catch (ApiException e) {
            progressBar.setVisibility(View.GONE);
            btnGoogle.setVisibility(View.VISIBLE);
            btnGithub.setVisibility(View.VISIBLE);
            btnAnonymous.setVisibility(View.VISIBLE);

            Toast.makeText(requireContext(), getString(R.string.error_auth_google) + " " + e.getStatusCode(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUIAndNavigate() {
        FirebaseUser user = firebaseRepository.getCurrentUser().getValue();
        if (user != null) {
            SharedPreferences prefs = requireContext().getSharedPreferences(APP_PREFERENCES, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString(USERNAME_KEY, getString(R.string.default_username));
            editor.putString(AVATAR_KEY, "");
            editor.putBoolean(IS_GUEST_KEY, false);
            editor.apply();

            progressBar.setVisibility(View.GONE);
            btnGoogle.setVisibility(View.VISIBLE);
            btnGithub.setVisibility(View.VISIBLE);
            btnAnonymous.setVisibility(View.VISIBLE);
            navController.navigate(R.id.action_loginFragment_to_mainFragment);
        }
    }
}