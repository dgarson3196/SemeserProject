package comc.example.dagar.project;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class CustomerActivity extends AppCompatActivity {

    //fields for buttons and textfields
    private EditText mEmail,mPassword;
    private Button mLogin, mRegister;

    //variable for firebase authentication/firebase authentication listener
    private FirebaseAuth auth;
    private FirebaseAuth.AuthStateListener firebaseauthListener;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);

        //get instance of firebase authentication
        //ping database for user
        auth = FirebaseAuth.getInstance();
        firebaseauthListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                //if user exists move to Rider map
                if(user !=null){
                    Intent intent = new Intent(CustomerActivity.this,
                            CustomerMapActivity.class);
                    startActivity(intent);
                    finish();
                    return;
                }
            }
        };

        //set variables to layout variables
        mEmail = (EditText) findViewById(R.id.email);
        mPassword = (EditText) findViewById(R.id.password);
        mLogin = (Button) findViewById(R.id.login);
        mRegister = (Button) findViewById(R.id.registration);

        //create a new user with valid email and password verification
        mRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = mEmail.getText().toString();
                final String password = mPassword.getText().toString();
                auth.createUserWithEmailAndPassword(email,password).addOnCompleteListener
                        (CustomerActivity.this,
                        new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                //test for registration error if exist toast error message
                                //to user
                                if(!task.isSuccessful())
                                {
                                    Toast.makeText(CustomerActivity.this,
                                            "Registration ERROR", Toast.LENGTH_SHORT).show();
                                }
                                //if no error create user and send new credentials to database
                                else
                                    {
                                    String userID = auth.getCurrentUser().getUid();
                                    DatabaseReference currentUserdb = FirebaseDatabase.getInstance().
                                            getReference().child("users").child("rider").child(userID);
                                    currentUserdb.setValue(true);
                                }
                            }
                        });
            }
        });

        //login with valid email and password verification
        mLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String email = mEmail.getText().toString();
                final String password = mPassword.getText().toString();
                auth.signInWithEmailAndPassword(email,password).addOnCompleteListener
                        (CustomerActivity.this,
                        new OnCompleteListener<AuthResult>() {
                            @Override
                            public void onComplete(@NonNull Task<AuthResult> task) {
                                //test for login error
                                if(!task.isSuccessful()){
                                    Toast.makeText(CustomerActivity.this,
                                            "Login ERROR", Toast.LENGTH_SHORT).show();
                                }
                            }
                        });

            }
        });


    }

    //when activity classed start authentication listener
    @Override
    protected void onStart(){
        super.onStart();
        auth.addAuthStateListener(firebaseauthListener);
    }

    //when move to next class stop authentication listener
    @Override
    protected void onStop(){
        super.onStop();
        auth.removeAuthStateListener(firebaseauthListener);
    }
}