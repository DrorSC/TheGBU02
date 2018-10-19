package com.sametcoh.thegbu02;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
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
import com.squareup.picasso.Picasso;
import com.theartofdev.edmodo.cropper.CropImage;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.util.HashMap;

import de.hdodenhof.circleimageview.CircleImageView;

public class SetupActivity extends AppCompatActivity {

    private EditText _userName, _fullName, _countryName;
    private Button _saveInformationButton;
    private CircleImageView _profileImage;
    private ProgressDialog _loadingBar;

    private FirebaseAuth mAuth;
    private DatabaseReference _userRef;
    private StorageReference _userProfileImageRef;

    String _currentUserId;
    final static int Gallery_Pick = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        mAuth = FirebaseAuth.getInstance();
        _currentUserId = mAuth.getCurrentUser().getUid();
        _userRef = FirebaseDatabase.getInstance().getReference().child("Users").child(_currentUserId);
        _userProfileImageRef = FirebaseStorage.getInstance().getReference().child("Profile Images");


        _userName = (EditText) findViewById(R.id.setup_username);
        _fullName= (EditText) findViewById(R.id.setup_full_name);
        _countryName = (EditText) findViewById(R.id.setup_country);
        _saveInformationButton = (Button) findViewById(R.id.setup_information_button);
        _profileImage = (CircleImageView) findViewById(R.id.setup_profile_image);
        _loadingBar = new ProgressDialog(this);

        _saveInformationButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SaveAccountSetupInformation();
            }
        });

        // When user click on image he will be redirected to his phone gallery
        _profileImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent galleryIntent = new Intent();
                galleryIntent.setAction(Intent.ACTION_GET_CONTENT);
                galleryIntent.setType("image/*");
                startActivityForResult(galleryIntent, Gallery_Pick);
            }
        });

        // display image
        _userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                // check if user in data base
                if(dataSnapshot.exists()){
                    if(dataSnapshot.hasChild("profileimage")){
                        // Load cropped image into circle image view
                        String image = dataSnapshot.child("profileimage").getValue().toString();
                        Picasso.get().load(image).placeholder(R.drawable.profile).into(_profileImage);
                    } else {
                        Toast.makeText(SetupActivity.this, "Please select profile image first..", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // Validation
        if(requestCode == Gallery_Pick && resultCode == RESULT_OK && data != null){
            // Get the uri the user picked
            Uri imageUri = data.getData();
            // Direct user to image cropper
            CropImage.activity()
                    .setGuidelines(CropImageView.Guidelines.ON)
                    .setAspectRatio(1,1)
                    .start(this);
        }
        // User click on "CROP" button
        if(requestCode == CropImage.CROP_IMAGE_ACTIVITY_REQUEST_CODE){
            //
            CropImage.ActivityResult result = CropImage.getActivityResult(data);
            //
            if(resultCode == RESULT_OK){
                _loadingBar.setTitle("Profile Image");
                _loadingBar.setMessage("Please wait while we are updating your profile image..");
                _loadingBar.show();
                _loadingBar.setCanceledOnTouchOutside(true);
                // Get the cropped image uri
                Uri resultUri = result.getUri();
                StorageReference filePath = _userProfileImageRef.child(_currentUserId + ".jpg");
                // Add image file to the firebase storage
                filePath.putFile(resultUri).addOnCompleteListener(new OnCompleteListener<UploadTask.TaskSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<UploadTask.TaskSnapshot> task) {
                        if (task.isSuccessful()) {
                            Toast.makeText(SetupActivity.this, "Profile image stored successfully to Firebase storage",
                                    Toast.LENGTH_SHORT).show();
                            final String downloadUrl = task.getResult().getDownloadUrl().toString();
                            // Add profile image link to firebase database
                            _userRef.child("profileimage").setValue(downloadUrl)
                                    .addOnCompleteListener(new OnCompleteListener<Void>() {
                                        @Override
                                        public void onComplete(@NonNull Task<Void> task) {
                                            if(task.isSuccessful()){
                                                SendUserToSelfActivity();
                                                Toast.makeText(SetupActivity.this, "Profile images stored to Firebase Database successfully",
                                                        Toast.LENGTH_SHORT).show();
                                            } else {
                                                String message = task.getException().getMessage();
                                                Toast.makeText(SetupActivity.this, "Error occurred: " + message, Toast.LENGTH_SHORT).show();
                                            }
                                            _loadingBar.dismiss();
                                        }
                                    });
                        }
                    }
                });
            } else {
                Toast.makeText(this, "Error occurred: image cannot be cropped, try again", Toast.LENGTH_SHORT).show();
                _loadingBar.dismiss();
            }
        }
    }

    private void SaveAccountSetupInformation() {
        String userName = _userName.getText().toString();
        String fullName = _fullName.getText().toString();
        String country = _countryName.getText().toString();

        if(TextUtils.isEmpty(userName)){
            Toast.makeText(this, "Please write user name", Toast.LENGTH_SHORT).show();
        }
        if(TextUtils.isEmpty(fullName)){
            Toast.makeText(this, "Please write full name", Toast.LENGTH_SHORT).show();
        }
        if(TextUtils.isEmpty(country)){
            Toast.makeText(this, "Please write country", Toast.LENGTH_SHORT).show();
        }
        else {
            _loadingBar.setTitle("Saving information");
            _loadingBar.setMessage("Please wait while we are creating your new account..");
            _loadingBar.show();
            _loadingBar.setCanceledOnTouchOutside(true);

            HashMap userMap = new HashMap();
            userMap.put("username", userName);
            userMap.put("fullname", fullName);
            userMap.put("country", country);
            userMap.put("status", "Hey there, i am using TheGBU, Developed by BissliBBQ");
            userMap.put("gender", "none");
            userMap.put("dob", "none");
            userMap.put("relationshipstatus", "none");
            _userRef.updateChildren(userMap).addOnCompleteListener(new OnCompleteListener() {
                @Override
                public void onComplete(@NonNull Task task) {
                    if (task.isSuccessful()) {
                        SendUserToMainActivity();
                        Toast.makeText(SetupActivity.this, "Your account is created successfully",
                                Toast.LENGTH_LONG).show();
                    } else {
                        String message = task.getException().getMessage();
                        Toast.makeText(SetupActivity.this, "Error occurd: " + message,
                                Toast.LENGTH_SHORT).show();
                    }
                    _loadingBar.dismiss();
                }
            });
        }
    }

    private void SendUserToSelfActivity() {
        Intent selfIntent = new Intent(SetupActivity.this, SetupActivity.class);
        selfIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(selfIntent);
        finish();
    }

    private void SendUserToMainActivity() {
        Intent mainIntent = new Intent(SetupActivity.this, MainActivity.class);
        mainIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(mainIntent);
        finish();
    }
}