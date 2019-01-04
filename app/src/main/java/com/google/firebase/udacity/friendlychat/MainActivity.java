/**
 * Copyright Google Inc. All Rights Reserved.
 *
 * <p>Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 * <p>http://www.apache.org/licenses/LICENSE-2.0
 *
 * <p>Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.udacity.friendlychat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

  private static final String TAG = "MainActivity";
  private static final int RC_SIGN_IN = 1;
  private static final int RC_PHOTO_PICKER = 2;
  public static final String ANONYMOUS = "anonymous";
  public static final int DEFAULT_MSG_LENGTH_LIMIT = 1000;
  private static final String FRIENDLY_MSG_LENGTH_KEY = "friendly_msg_length";

  private ListView mMessageListView;
  private MessageAdapter mMessageAdapter;
  private ProgressBar mProgressBar;
  private ImageButton mPhotoPickerButton;
  private EditText mMessageEditText;
  private Button mSendButton;

  private String mUsername;
  private FirebaseDatabase mFriFirebaseDatabase;
  private DatabaseReference mDatabaseReference;
  private ChildEventListener mChildEventListener;
  private FirebaseAuth mFirebaseAuth;
  private FirebaseAuth.AuthStateListener mAuthStateListener;
  private FirebaseStorage mFirebaseStorage;
  private StorageReference mStorageReference;
  private FirebaseRemoteConfig mFirebaseRemoteConfig;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    mUsername = ANONYMOUS;

    // Initialize Firebase components
    mFriFirebaseDatabase = FirebaseDatabase.getInstance();
    mFirebaseAuth = FirebaseAuth.getInstance();
    mFirebaseStorage = FirebaseStorage.getInstance();
    mFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();

    mDatabaseReference = mFriFirebaseDatabase.getReference("messages");
    mStorageReference = mFirebaseStorage.getReference("chat_photos");

    // Initialize references to views
    mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
    mMessageListView = (ListView) findViewById(R.id.messageListView);
    mPhotoPickerButton = (ImageButton) findViewById(R.id.photoPickerButton);
    mMessageEditText = (EditText) findViewById(R.id.messageEditText);
    mSendButton = (Button) findViewById(R.id.sendButton);

    // Initialize message ListView and its adapter
    List<FriendlyMessage> friendlyMessages = new ArrayList<>();
    mMessageAdapter = new MessageAdapter(this, R.layout.item_message, friendlyMessages);
    mMessageListView.setAdapter(mMessageAdapter);

    // Initialize progress bar
    mProgressBar.setVisibility(ProgressBar.INVISIBLE);

    // ImagePickerButton shows an image picker to upload a image for a message
    mPhotoPickerButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            // TODO: Fire an intent to show an image picker
          }
        });

    // Enable Send button when there's text to send
    mMessageEditText.addTextChangedListener(
        new TextWatcher() {
          @Override
          public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {}

          @Override
          public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            if (charSequence.toString().trim().length() > 0) {
              mSendButton.setEnabled(true);
            } else {
              mSendButton.setEnabled(false);
            }
          }

          @Override
          public void afterTextChanged(Editable editable) {}
        });
    mMessageEditText.setFilters(
        new InputFilter[] {new InputFilter.LengthFilter(DEFAULT_MSG_LENGTH_LIMIT)});

    // Send button sends a message and clears the EditText
    mSendButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {

            FriendlyMessage friendlyMessage =
                new FriendlyMessage(mMessageEditText.getText().toString(), mUsername, null);
            mDatabaseReference.push().setValue(friendlyMessage);

            // Clear input box
            mMessageEditText.setText("");
          }
        });

    mAuthStateListener =
        new FirebaseAuth.AuthStateListener() {
          @Override
          public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
              OnSignedInInit(user.getDisplayName());
            } else {
              OnSignedOut();
              startActivityForResult(
                  AuthUI.getInstance()
                      .createSignInIntentBuilder()
                      .setAvailableProviders(
                          Arrays.asList(
                              new AuthUI.IdpConfig.GoogleBuilder().build(),
                              new AuthUI.IdpConfig.EmailBuilder().build(),
                              new AuthUI.IdpConfig.PhoneBuilder().build(),
                              new AuthUI.IdpConfig.AnonymousBuilder().build()))
                      .build(),
                  RC_SIGN_IN);
            }
          }
        };

    mPhotoPickerButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/jpeg");
            intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            startActivityForResult(
                Intent.createChooser(intent, "Complete action using"), RC_PHOTO_PICKER);
          }
        });

    FirebaseRemoteConfigSettings config =
        new FirebaseRemoteConfigSettings.Builder()
            .setDeveloperModeEnabled(BuildConfig.DEBUG)
            .build();
    mFirebaseRemoteConfig.setConfigSettings(config);

    Map<String, Object> defaultConfig = new HashMap<>();
    defaultConfig.put(FRIENDLY_MSG_LENGTH_KEY, DEFAULT_MSG_LENGTH_LIMIT);
    mFirebaseRemoteConfig.setDefaults(defaultConfig);
    fetchConfig();
  }

  private void fetchConfig() {
    long cacheExpiration = 0;
    if (mFirebaseRemoteConfig.getInfo().getConfigSettings().isDeveloperModeEnabled()) {
      cacheExpiration = 0;
    }

    mFirebaseRemoteConfig
        .fetch(cacheExpiration)
        .addOnSuccessListener(
            new OnSuccessListener<Void>() {
              @Override
              public void onSuccess(Void aVoid) {
                mFirebaseRemoteConfig.activateFetched();
                applyRetrievedLengthLimit();
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                Log.w(TAG, "Error fetching the config.");
                applyRetrievedLengthLimit();
              }
            });
  }

  private void applyRetrievedLengthLimit() {
    Long length = mFirebaseRemoteConfig.getLong(FRIENDLY_MSG_LENGTH_KEY);
    mMessageEditText.setFilters(
        new InputFilter[] {new InputFilter.LengthFilter(length.intValue())});
    Log.d(TAG, FRIENDLY_MSG_LENGTH_KEY + " = " + length);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == RC_SIGN_IN) {
      if (resultCode == RESULT_OK) {
        Toast.makeText(this, "Sing in!!!", Toast.LENGTH_LONG).show();
      } else if (resultCode == RESULT_CANCELED) {
        Toast.makeText(this, "Sing in cancelled.", Toast.LENGTH_LONG).show();
        finish();
      }
    } else if (requestCode == RC_PHOTO_PICKER && resultCode == RESULT_OK) {
      Uri selectedImageUri = data.getData();
      final StorageReference photoRef =
          mStorageReference.child(selectedImageUri.getLastPathSegment());
      UploadTask uploadTask = photoRef.putFile(selectedImageUri);
      uploadTask
          .continueWithTask(
              new Continuation<UploadTask.TaskSnapshot, Task<Uri>>() {
                @Override
                public Task<Uri> then(@NonNull Task<UploadTask.TaskSnapshot> task)
                    throws Exception {
                  if (!task.isSuccessful()) {
                    throw task.getException();
                  }
                  // Continue with the task to get the download URL
                  return photoRef.getDownloadUrl();
                }
              })
          .addOnCompleteListener(
              new OnCompleteListener<Uri>() {
                @Override
                public void onComplete(@NonNull Task<Uri> task) {
                  if (task.isSuccessful()) {
                    Uri downloadUri = task.getResult();
                    FriendlyMessage friendlyMessage =
                        new FriendlyMessage(null, mUsername, downloadUri.toString());
                    mDatabaseReference.push().setValue(friendlyMessage);
                  } else {
                    Toast.makeText(MainActivity.this, "Unable to upload.", Toast.LENGTH_LONG)
                        .show();
                  }
                }
              });
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    if (mAuthStateListener != null) {
      mFirebaseAuth.removeAuthStateListener(mAuthStateListener);
    }
    detachDatabaseReadListener();
    mMessageAdapter.clear();
  }

  @Override
  protected void onResume() {
    super.onResume();
    mFirebaseAuth.addAuthStateListener(mAuthStateListener);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.sign_out_menu:
        AuthUI.getInstance().signOut(this);
        return true;

      default:
        return super.onOptionsItemSelected(item);
    }
  }

  private void OnSignedInInit(String userName) {
    mUsername = userName;
    attachDatabaseReadListener();
  }

  private void attachDatabaseReadListener() {
    if (mChildEventListener == null) {
      mChildEventListener =
          new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {
              FriendlyMessage friendlyMessage = dataSnapshot.getValue(FriendlyMessage.class);
              mMessageAdapter.add(friendlyMessage);
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {}

            @Override
            public void onChildRemoved(@NonNull DataSnapshot dataSnapshot) {}

            @Override
            public void onChildMoved(@NonNull DataSnapshot dataSnapshot, @Nullable String s) {}

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {}
          };
      mDatabaseReference.addChildEventListener(mChildEventListener);
    }
  }

  private void OnSignedOut() {
    mUsername = ANONYMOUS;
    mMessageAdapter.clear();
    detachDatabaseReadListener();
  }

  private void detachDatabaseReadListener() {
    if (mChildEventListener != null) {
      mDatabaseReference.removeEventListener(mChildEventListener);
      mChildEventListener = null;
    }
  }
}
