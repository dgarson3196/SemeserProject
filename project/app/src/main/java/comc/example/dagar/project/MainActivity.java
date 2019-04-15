package comc.example.dagar.project;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    //driver/rider buttons
    private Button mdriver;
    private Button mcustomer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //set variables to layout button
        mdriver = (Button) findViewById(R.id.driver);
        mcustomer = (Button) findViewById(R.id.customer);

        //set onclick listener to start driver login
        mdriver.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this,
                        DriverActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        //set onclick listener to start customers login
        mcustomer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this,
                        CustomerActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
    }
}