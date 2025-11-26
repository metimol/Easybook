package com.metimol.easybook.firebase;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.metimol.easybook.R;
import com.metimol.easybook.database.AudiobookDao;
import com.metimol.easybook.database.Book;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FirebaseRepository {
    private final FirebaseAuth auth;
    private final DatabaseReference database;
    private final AudiobookDao audiobookDao;
    private final ExecutorService executorService;
    private final MutableLiveData<FirebaseUser> currentUser = new MutableLiveData<>();

    public FirebaseRepository(AudiobookDao dao) {
        this.auth = FirebaseAuth.getInstance();
        this.database = FirebaseDatabase.getInstance().getReference();
        this.audiobookDao = dao;
        this.executorService = Executors.newSingleThreadExecutor();

        currentUser.postValue(auth.getCurrentUser());
    }

    public LiveData<FirebaseUser> getCurrentUser() {
        return currentUser;
    }

    public GoogleSignInClient getGoogleSignInClient(Context context) {
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        return GoogleSignIn.getClient(context, gso);
    }

    public void firebaseAuthWithGoogle(String idToken, Runnable onSuccess, Runnable onFailure) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        currentUser.postValue(auth.getCurrentUser());
                        syncLocalDataToCloud();
                        onSuccess.run();
                    } else {
                        onFailure.run();
                    }
                });
    }

    public void syncLocalDataToCloud() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        executorService.execute(() -> {
            List<Book> localBooks = audiobookDao.getAllBooksProgress();

            DatabaseReference userBooksRef = database.child("users").child(user.getUid()).child("books");

            userBooksRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    Map<String, Object> updates = new HashMap<>();

                    for (Book localBook : localBooks) {
                        if (!snapshot.hasChild(localBook.id)) {
                            Map<String, Object> bookData = getStringObjectMap(localBook);

                            updates.put(localBook.id, bookData);
                        }
                    }

                    if (!updates.isEmpty()) {
                        userBooksRef.updateChildren(updates);
                    }
                }

                @NonNull
                private static Map<String, Object> getStringObjectMap(Book localBook) {
                    Map<String, Object> bookData = new HashMap<>();
                    bookData.put("id", localBook.id);
                    bookData.put("isFavorite", localBook.isFavorite);
                    bookData.put("isFinished", localBook.isFinished);
                    bookData.put("currentChapterId", localBook.currentChapterId);
                    bookData.put("currentTimestamp", localBook.currentTimestamp);
                    bookData.put("progressPercentage", localBook.progressPercentage);
                    bookData.put("lastListened", localBook.lastListened);
                    return bookData;
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e("FirebaseRepo", "Sync error: " + error.getMessage());
                }
            });
        });
    }

    public void logout() {
        auth.signOut();
        currentUser.postValue(null);
    }
}