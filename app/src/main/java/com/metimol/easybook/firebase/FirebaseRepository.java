package com.metimol.easybook.firebase;

import android.app.Activity;
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
import com.google.firebase.auth.OAuthProvider;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import com.metimol.easybook.BuildConfig;
import com.metimol.easybook.R;
import com.metimol.easybook.database.AudiobookDao;
import com.metimol.easybook.database.Book;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FirebaseRepository {
    private final FirebaseAuth auth;
    private final DatabaseReference database;
    private final AudiobookDao audiobookDao;
    private final ExecutorService executorService;
    private final MutableLiveData<FirebaseUser> currentUser = new MutableLiveData<>();

    private ValueEventListener cloudListener;
    private static final String TAG = "FirebaseRepository";

    public FirebaseRepository(AudiobookDao dao) {
        this.auth = FirebaseAuth.getInstance();
        String databaseUrl = BuildConfig.FIREBASE_DB_URL;
        this.database = FirebaseDatabase.getInstance(databaseUrl).getReference();

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

    public void firebaseAuthWithGoogle(String idToken, Runnable onSuccess, Consumer<Exception> onFailure) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        auth.signInWithCredential(credential)
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "firebaseAuthWithGoogle: success");
                        currentUser.setValue(auth.getCurrentUser());
                        syncLocalDataToCloud();
                        startSync();
                        onSuccess.run();
                    } else {
                        Log.e(TAG, "firebaseAuthWithGoogle: failure", task.getException());
                        onFailure.accept(task.getException());
                    }
                });
    }

    public void signInWithGitHub(Activity activity, Runnable onSuccess, Consumer<Exception> onFailure) {
        OAuthProvider.Builder provider = OAuthProvider.newBuilder("github.com");
        auth.startActivityForSignInWithProvider(activity, provider.build())
                .addOnSuccessListener(authResult -> {
                    Log.d(TAG, "signInWithGitHub: success");
                    currentUser.setValue(auth.getCurrentUser());
                    syncLocalDataToCloud();
                    startSync();
                    onSuccess.run();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "signInWithGitHub: failure", e);
                    onFailure.accept(e);
                });
    }

    public void startSync() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;

        DatabaseReference userBooksRef = database.child("users").child(user.getUid()).child("books");

        if (cloudListener == null) {
            cloudListener = new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    executorService.execute(() -> {
                        for (DataSnapshot bookSnapshot : snapshot.getChildren()) {
                            try {
                                String id = bookSnapshot.child("id").getValue(String.class);
                                if (id == null) continue;

                                Book cloudBook = new Book();
                                cloudBook.id = id;
                                cloudBook.isFavorite = Boolean.TRUE.equals(bookSnapshot.child("isFavorite").getValue(Boolean.class));
                                cloudBook.isFinished = Boolean.TRUE.equals(bookSnapshot.child("isFinished").getValue(Boolean.class));
                                cloudBook.currentChapterId = bookSnapshot.child("currentChapterId").getValue(String.class);
                                Long ts = bookSnapshot.child("currentTimestamp").getValue(Long.class);
                                cloudBook.currentTimestamp = ts != null ? ts : 0;
                                Long lastListened = bookSnapshot.child("lastListened").getValue(Long.class);
                                cloudBook.lastListened = lastListened != null ? lastListened : 0;
                                Integer progress = bookSnapshot.child("progressPercentage").getValue(Integer.class);
                                cloudBook.progressPercentage = progress != null ? progress : 0;

                                Book localBook = audiobookDao.getBookById(id);

                                if (localBook == null || cloudBook.lastListened > localBook.lastListened) {
                                    audiobookDao.insertBook(cloudBook);
                                }

                            } catch (Exception e) {
                                Log.e(TAG, "Error parsing cloud data", e);
                            }
                        }
                    });
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    Log.e(TAG, "startSync cancelled: " + error.getMessage());
                }
            };
            userBooksRef.addValueEventListener(cloudListener);
        }
    }

    public void stopSync() {
        if (cloudListener != null && auth.getCurrentUser() != null) {
            database.child("users").child(auth.getCurrentUser().getUid()).child("books")
                    .removeEventListener(cloudListener);
            cloudListener = null;
        }
    }

    public void updateBookInCloud(Book book) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Log.w(TAG, "updateBookInCloud: user is null");
            return;
        }

        Map<String, Object> bookData = new HashMap<>();
        bookData.put("id", book.id);
        bookData.put("isFavorite", book.isFavorite);
        bookData.put("isFinished", book.isFinished);
        bookData.put("currentChapterId", book.currentChapterId);
        bookData.put("currentTimestamp", book.currentTimestamp);
        bookData.put("progressPercentage", book.progressPercentage);
        bookData.put("lastListened", book.lastListened);

        database.child("users").child(user.getUid()).child("books").child(book.id)
                .updateChildren(bookData)
                .addOnFailureListener(e -> Log.e(TAG, "updateBookInCloud failed", e));
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
                        userBooksRef.updateChildren(updates)
                                .addOnFailureListener(e -> Log.e(TAG, "syncLocalDataToCloud failed to update", e));
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
                    Log.e(TAG, "syncLocalDataToCloud cancelled: " + error.getMessage());
                }
            });
        });
    }

    public void logout() {
        stopSync();
        auth.signOut();
        currentUser.postValue(null);
    }
}