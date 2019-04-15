package comc.example.dagar.project;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;

public class CustomerMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, LocationListener,
        com.google.android.gms.location.LocationListener {

    //variables for googlemaps/setting and getting locations and googleapi client.
    private GoogleMap mMap;
    GoogleApiClient mGoogleAPIClient;
    Location mLastLocation;
    LocationRequest mLocationrequest;

    //variables handle buttons/map fragment and if ride needed
    private Button mlogout, mRequest, mSettings;
    private SupportMapFragment mapFragment;
    private boolean requestCall = false;

    private LatLng pickupLocation;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_customer_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        //handles logout button
        //when logout sign out user from database
        //return user to home screen/Main activity
        mlogout = (Button) findViewById(R.id.logout);
        mRequest = (Button) findViewById(R.id.request);
        mlogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(CustomerMapActivity.this,
                        MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });

        //call for a ride
        mRequest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //validate no request
                if (requestCall)
                {
                    requestCall = false;
                    geoQuery.removeAllListeners();
                    driverLocationRef.removeEventListener(driverLocationListener);
                }
                //validate yes request
                else
                    {
                        //create a new request in the database with current latitude/longitude
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("request");
                    GeoFire geoFire = new GeoFire(ref);

                    //constantly update the location
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(),
                            mLastLocation.getLongitude()));
                    pickupLocation = new LatLng(mLastLocation.getLatitude(),
                            mLastLocation.getLongitude());

                    //onscreen markers
                    mMap.addMarker(new MarkerOptions().position(pickupLocation).title("HERE!!"));
                    mRequest.setText("Grabbing Ride");
                    getDriveLocation();
                }
            }
        });
    }

    //variables for finding a driver
    private  int radius = 1;
    private Boolean found = false;
    private String dFoundID;
    GeoQuery geoQuery;


    private void getDriveLocation(){

        //ping database for avaiable driver
        DatabaseReference driverLocation = FirebaseDatabase.
                getInstance().getReference().child("driversAval");

        //geofire instance
        GeoFire geoFire = new GeoFire(driverLocation);

        //query gets the center of the radius of the request
         geoQuery = geoFire.queryAtLocation(new GeoLocation(pickupLocation.latitude,
                 pickupLocation.latitude), radius);
         //not necessary but a good measure to remove listener when method recalls itself
        geoQuery.removeAllListeners();
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {

            //used to check if a driver has been found
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!found) {
                    found = true;

                    //only get first driver found
                    dFoundID = key;

                    //get reference to driver
                    DatabaseReference driverRef = FirebaseDatabase.getInstance().
                            getReference().child("users").child("driver")
                            .child(dFoundID);

                    //get reference to current user
                    //update map to but rider inside the child
                    String curuserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap map = new HashMap();
                    map.put("curRiderId", curuserId);
                    driverRef.updateChildren(map);
                    getDriverLocation();

                    //update button status
                    mRequest.setText("Looking for Ride...");
                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {

                //expand radius until driver is found
                if (!found){
                    radius++;
                    //keep calling method until all drivers found
                    getDriveLocation();
                }

            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private Marker mDriverMarker;
    private DatabaseReference driverLocationRef;
    private ValueEventListener driverLocationListener;


    //method gets reference to driver location
    private void getDriverLocation(){
         driverLocationRef = FirebaseDatabase.getInstance().getReference().
                 child("driverWorking").child(dFoundID)
                .child("l");

       driverLocationListener = driverLocationRef.addValueEventListener(new ValueEventListener() {

           //used to constantly update map of new latitude and longitude
           //of user
           //needed or app will crash
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()){

                    //put data in list
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationlong = 0;
                    mRequest.setText("Driver Found");

                    //validate latitude
                    if (map.get(0) !=null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }

                    //validate longitude
                    if (map.get(1) !=null){
                        locationlong = Double.parseDouble(map.get(1).toString());
                    }

                    //map marker will reset every time a new location exists
                    LatLng driverLatLong = new LatLng(locationLat,locationlong);

                    //needed or app will crash
                    if (mDriverMarker !=null){
                        mDriverMarker.remove();
                    }

                    //set new latitude and longitude
                    Location lo1 = new Location(" ");
                    lo1.setLatitude(pickupLocation.latitude);
                    lo1.setLongitude(pickupLocation.longitude);
                    Location lo2 = new Location(" ");
                    lo2.setLatitude(driverLatLong.latitude);
                    lo2.setLongitude(driverLatLong.longitude);

                    float distance = lo1.distanceTo(lo2);

                    //expand distance to find driver
                    if (distance <100) {
                        mRequest.setText("Im HERE!!");
                    }else {
                        mRequest.setText("Driver Found  " + String.valueOf(distance));
                    }
                    mDriverMarker = mMap.addMarker(new MarkerOptions().position(driverLatLong)
                    .title("You Driver "));
                }

            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //permission validation necessary for all phones
        //may not be enough on newer models
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        //call function
        buildGoogleApiClient();

        //enable location
        mMap.setMyLocationEnabled(true);
    }

    //builds google api client while adding callbacks and checking for connection failures
    protected synchronized void buildGoogleApiClient(){
        mGoogleAPIClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).addApi(LocationServices.API)
                .build();

        mGoogleAPIClient.connect();
    }
    @Override
    public void onLocationChanged(Location location) {

        //validate api client
        if (getApplicationContext() !=null) {
            mLastLocation = location;

            //get latitude/longitude location
            LatLng latlog = new LatLng(location.getLatitude(), location.getLongitude());

            //has map move with the latlog of the phone
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latlog));

            //zooms map to specified distance
            mMap.animateCamera(CameraUpdateFactory.zoomTo(9));


        }

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        //create a mew location request
        mLocationrequest = new LocationRequest();

        //set the interval of the request to run every second
        mLocationrequest.setInterval(1000);
        mLocationrequest.setFastestInterval(1000);

        //set highest priority for the maps location request
        mLocationrequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        //used to check if all permissions are available
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission
                        (this, Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CustomerMapActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATIONrEQUEST );
        }

        //Triggers refresh of location
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleAPIClient,
                mLocationrequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
    final int LOCATIONrEQUEST = 1;

    //used to validate the location by requesting phones user to accept
    //only relevant on newer phone models
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults){
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode){
            case LOCATIONrEQUEST:{
                if (grantResults.length>0 && grantResults[0] == PackageManager.PERMISSION_GRANTED){
                    mapFragment.getMapAsync(this);
                }
                else {
                    Toast.makeText(getApplicationContext(), "Provide Permission",
                            Toast.LENGTH_LONG).show();
                }
                break;
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

    }
}
