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
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.directions.route.AbstractRouting;
import com.directions.route.Route;
import com.directions.route.RouteException;
import com.directions.route.Routing;
import com.directions.route.RoutingListener;
import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DriverMapActivity extends FragmentActivity implements OnMapReadyCallback,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
        LocationListener, com.google.android.gms.location.LocationListener, RoutingListener {

    //variables for googlemaps/setting and getting locations and googleapi client.
    private GoogleMap mMap;
    GoogleApiClient mGoogleAPIClient;
    Location mLastLocation;
    LocationRequest mLocationrequest;

    //variables handle buttons/map fragment and matching rider to driver
    private Button mlogout;
    private SupportMapFragment mapFragment;
    private String custID = " ";
    private  boolean isLogginOut = false;

    //variables for drawing path from driver to rider
    private List<Polyline> polylines;
    private static final int[] COLORS = new int[]{R.color.primary_dark_material_light};


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver_map);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.

        //makes line between rider and driver
        polylines = new ArrayList<>();
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        //set permissions to use location
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(DriverMapActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},LOCATIONrEQUEST );
        }else {
            mapFragment.getMapAsync(this);
        }

        //handles logout button
        //when logout sign out user from database
        //return user to home screen/Main activity
        mlogout = (Button) findViewById(R.id.logout);
        mlogout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                isLogginOut = true;

                disconnectDriver();
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(DriverMapActivity.this,
                        MainActivity.class);
                startActivity(intent);
                finish();
                return;
            }
        });
        getAssignedRider();
    }

    //match the driver to an assigned rider who is also logged in
    private void getAssignedRider(){
        String driverId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference assignRiderRef = FirebaseDatabase.getInstance().
                getReference().child("users")
                .child("driver").child(driverId).child("curRiderId");
        assignRiderRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()){
                        custID = dataSnapshot.getValue().toString();
                        getAssignedRiderPickupLocation();

                }
            }

            //request cancelled
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });
    }

    //gets the riders location
    private void getAssignedRiderPickupLocation(){
        DatabaseReference assignCusPickupLocation = FirebaseDatabase.getInstance().
                getReference().child("request")
                .child(custID).child("l");
        assignCusPickupLocation.addValueEventListener(new ValueEventListener() {

            //used to constantly update map of new latitude and longitude
            //of user
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationlong = 0;

                    //validate latitude
                    if (map.get(0) !=null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }

                    //validate longitude
                    if (map.get(1) !=null){
                        locationlong = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng pickupLatlong = new LatLng(locationLat,locationlong);
                     mMap.addMarker(new MarkerOptions().position(pickupLatlong)
                            .title("Pickup Location"));
                     getRouteToMarker(pickupLatlong);
                }
            }

            //request cancelled
            //or error with database
            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });


    }

    //get the route marker from user to the rider to pick up
    private void getRouteToMarker(LatLng pickupLatlong){
        Routing routing = new Routing.Builder()
                .travelMode(AbstractRouting.TravelMode.DRIVING)
                .withListener(this)
                .alternativeRoutes(false)
                .waypoints(new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude()),
                        pickupLatlong)
                .build();
        routing.execute();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        //permission validation necessary for all phones
        //may not be enough on newer models
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

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
            mMap.animateCamera(CameraUpdateFactory.zoomTo(8));

            //get id of current user
            String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

            //point to reference of all available drivers or working drivers in the database
            DatabaseReference refAvalable = FirebaseDatabase.getInstance().getReference("driversAval");
            DatabaseReference refWorking = FirebaseDatabase.getInstance().getReference("driverWorking");

            //new geofire reference for avaiable drivers/working drivers
            GeoFire geoFireAvalable = new GeoFire(refAvalable);
            GeoFire geoFireWorking = new GeoFire(refWorking);

            //case for custID
            switch (custID){

                //only used when driver working
                case "":
                            //switch from working to available
                    geoFireWorking.removeLocation(userId);
                    //get new location
                    geoFireAvalable.setLocation(userId, new GeoLocation(location.getLatitude(),
                            location.getLongitude()));
                break;


                default:

                    //switch from available to working
                    geoFireAvalable.removeLocation(userId);
                    //get new location
                    geoFireWorking.setLocation(userId, new GeoLocation(location.getLatitude(),
                            location.getLongitude()));
                    break;
            }








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
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
    return;
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


    //when driver log out remove that instance from the database to disconnect them
    private void disconnectDriver(){
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleAPIClient, this);
        String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("driversAval");

        GeoFire geoFire = new GeoFire(ref);
        geoFire.removeLocation(userId);
    }

    final int LOCATIONrEQUEST = 1;

    //used to validate the location by requesting phones user to accept
    //only relevant on newer phone models
        @Override
        public void onRequestPermissionsResult(int requestCode,
                                               @NonNull String[] permissions,
                                               @NonNull int[] grantResults){
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            switch (requestCode){
                case LOCATIONrEQUEST:{
                    if (grantResults.length>0 &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED){
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

    //disconnect from database if logout
    @Override
    protected void onStop() {
        super.onStop();
        if (!isLogginOut){
           disconnectDriver();
        }

    }

    //validate if failed to get route
    @Override
    public void onRoutingFailure(RouteException e) {
        if(e != null) {
            Toast.makeText(this, "Error: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }else {
            Toast.makeText(this, "Something went wrong, Try again",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRoutingStart() {

    }

    //if route is successful display the fastest/shortest path to the rider
    @Override
    public void onRoutingSuccess(ArrayList<Route> route, int shortestRouteIndex) {
        if(polylines.size()>0) {
            for (Polyline poly : polylines) {
                poly.remove();
            }
        }
        polylines = new ArrayList<>();
        //add route(s) to the map.
        for (int i = 0; i <route.size(); i++) {

            //In case of more than 5 alternative routes
            int colorIndex = i % COLORS.length;

            //set options for line such as:
            //color, width and type
            PolylineOptions polyOptions = new PolylineOptions();
            polyOptions.color(getResources().getColor(COLORS[colorIndex]));
            polyOptions.width(10 + i * 3);
            polyOptions.addAll(route.get(i).getPoints());
            Polyline polyline = mMap.addPolyline(polyOptions);
            polylines.add(polyline);

            //display distance to other user
            Toast.makeText(getApplicationContext(),"Route "+
                    (i+1) +": distance - "+ route.get(i).getDistanceValue()+
                    ": duration - "+ route.get(i).getDurationValue(),Toast.LENGTH_SHORT).show();
        }

    }

    @Override
    public void onRoutingCancelled() {

    }
}
