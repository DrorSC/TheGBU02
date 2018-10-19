package com.sametcoh.thegbu02;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;

public class PostActivity extends AppCompatActivity {

    private Toolbar _mToolbar;

    // Choosing picture for the new post
    private ImageButton _selectPostImage;
    private EditText _postDescription;
    private Button _updatePostButton;
    private static final int Gallery_Pick = 1;
    private Uri _imageUri;

    // saving image to storage
    private String _description;
    private StorageReference _postsImagesReference;
    private String _saveCurrentDate, _saveCurrentTime, _postRandomName;

    // saving post information
    private String _downloadUrl, _currentUserId;
    private DatabaseReference _usersRef, _postRef;
    private FirebaseAuth _mAuth;
    private ProgressDialog _loadingBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post);

        _mAuth = FirebaseAuth.getInstance();
        _currentUserId = _mAuth.getUid();
        _postsImagesReference = FirebaseStorage.getInstance().getReference();
        _usersRef = FirebaseDatabase.getInstance().getReference().child("Users");
        _postRef = FirebaseDatabase.getInstance().getReference().child("Posts");

        _mToolbar = (Toolbar) findViewById(R.id.update_post_page_toolbar);
        setSupportActionBar(_mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        getSupportActionBar().setTitle("updatepost");

        _selectPostImage = (ImageButton) findViewById(R.id.select_post_image);
        _postDescription = (EditText) findViewById(R.id.post_description);
        _updatePostButton = (Button) findViewById(R.id.update_post_button);
        _loadingBar = new ProgressDialog(this);


        // Let user choose picture from phone gallery after clicking the post image
        _selectPostImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                OpenGallery();
            }
        });
        
        
        // After user click on Update Post do this
        _updatePostButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ValidatePostInfo();
                
            }
        });
    }

    private void ValidatePostInfo() {
        _description = _postDescription.getText().toString();

        // check if image is selected and description is added
        if(_imageUri == null){
            Toast.makeText(this, "Please select post image", Toast.LENGTH_SHORT).show();
        }
        else if(TextUtils.isEmpty(_description)){
            Toast.makeText(this, "Please add description", Toast.LENGTH_SHORT).show();
        } else {

            _loadingBar.setTitle("Add New Post");
            _loadingBar.setMessage("Please wait while we are updating your new post..");
            _loadingBar.show();
            _loadingBar.setCanceledOnTouchOutside(true);
            //
            StoreImageToFirebaseStorage();
        }
    }

    private void StoreImageToFirebaseStorage() {
        // To create a unique name for the image we use current time&date
        Calendar calForDate = Calendar.getInstance();
        SimpleDateFormat currentDate = new SimpleDateFormat("dd-MMMM-yyyy");
        _saveCurrentDate = currentDate.format(calForDate.getTime());
        Calendar calForTime = Calendar.getInstance();
        SimpleDateFormat currentTime = new SimpleDateFormat("HH:mm");
        _saveCurrentTime = currentTime.format(calForDate.getTime());

        _postRandomName = _saveCurrentDate + _saveCurrentTime;

        // get the unique file path
        StorageReference filePath = _postsImagesReference.child("Post Images").child(_imageUri.getLastPathSegment()
                + _postRandomName + ".jpg");
        // add file to the storage
        filePath.putFile(_imageUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                if(task.isSuccessful()) {
                    _downloadUrl = task.getResult().getDownloadUrl().toString();

                    Toast.makeText(PostActivity.this, "Image uploaded successfully", Toast.LENGTH_SHORT).show();

                    SavingPostInformationToDatabase();
                }else {
                    String message = task.getException().getMessage();
                    Toast.makeText(PostActivity.this, "Error occured: " + message, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void SavingPostInformationToDatabase() {
        _usersRef.child(_currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    String userFullName = dataSnapshot.child("fullname").getValue().toString();
                    String userProfileImage = dataSnapshot.child("profileimage").getValue().toString();

                    HashMap postsMap = new HashMap();
                    postsMap.put("uid", _currentUserId);
                    postsMap.put("date", _saveCurrentDate);
                    postsMap.put("time", _saveCurrentTime);
                    postsMap.put("description", _description);
                    postsMap.put("postimage", _downloadUrl);
                    postsMap.put("profileimage", userProfileImage);
                    postsMap.put("fullname", userFullName);

                    _postRef.child(_currentUserId + _postRandomName).updateChildren(postsMap)
                            .addOnCompleteListener(new OnCompleteListener() {
                                @Override
                                public void onComplete(@NonNull Task task) {
                                    if(task.isSuccessful()){
                                        Toast.makeText(PostActivity.this, "New post is updated successfully",
                                                Toast.LENGTH_SHORT).show();
                                        SendUserToMainActivity();
                                    }else{
                                        Toast.makeText(PostActivity.this, "Error occurds while updating your post",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    _loadingBar.dismiss();
                                }
                            });
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void OpenGallery() {
        Intent galleryIntent = new Intent();
        galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
        galleryIntent.setType("image/*");
        startActivityForResult(galleryIntent, Gallery_Pick);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // after user picked an image from the gallery, set that image into the new post
        if(requestCode == Gallery_Pick && resultCode == RESULT_OK && data != null){
            _imageUri = data.getData();
            _selectPostImage.setImageURI(_imageUri);
        }

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == android.R.id.home){ // if user clicks on "back" button - send him to main activity
            SendUserToMainActivity();
        }
        return super.onOptionsItemSelected(item);
    }

    private void SendUserToMainActivity() {
        Intent mainIntent = new Intent(PostActivity.this, MainActivity.class);
        //mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(mainIntent);
        //finish();
    }
}
