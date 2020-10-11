package com.example.uoitnavigation;

import android.Manifest;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.speech.RecognizerIntent;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.language.v1.AnalyzeEntitiesRequest;
import com.google.cloud.language.v1.AnalyzeEntitiesResponse;
import com.google.cloud.language.v1.Document;
import com.google.cloud.language.v1.Entity;
import com.google.cloud.language.v1.LanguageServiceClient;
import com.google.cloud.language.v1.LanguageServiceSettings;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;


class Coordinates {
    public double lat;
    public double lon;

    public Coordinates(double lat, double lon) {
        this.lat = lat;
        this.lon = lon;
    }

    public double getLat() {
        return lat;
    }

    public double getLon() {
        return lon;
    }

    public String toString() {
        return this.lat + ", " + this.lon;
    }
}

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final int REQ_CODE_SPEECH_INPUT = 100;
    Location myLocation;
    FusedLocationProviderClient mFusedLocationProviderClient;
    EditText roomNum;
    String room;
    private GoogleMap mMap;

    //HashMap with Rooms and Coordinates
    private HashMap<String, LatLng> UAMapCords;
    private String[] UARooms = {"UA1140","UA1120","UA1240","UA1220","UA1001","UA1030","UA1350","UA1350-B","UA1420","UA1440","UA1460","UA1540","UA1520","UA1660","UA1640","UA1620",};

    private ArrayList<String> reqNodes;
    private TextView mVoiceInputTv;
    private Button mSpeakBtn;
    private String message;
    private LanguageServiceClient mLanguageClient;
    //private LanguageServiceSettings settings;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        reqNodes = new ArrayList<>(); //Initialize the Arraylist for the voice recog
        UAMapCords = new HashMap<>(); //Initialize the HashMap for room coords

        getRoomMarkers();

        // Obtain the SupportMapFragment for the map to be ready to used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        initMap(); //initialize Map

        mSpeakBtn = findViewById(R.id.button);
        mSpeakBtn.setOnClickListener(v -> startVoiceInput());

    }

    public void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map); //get the map fragment
        mapFragment.getMapAsync(this);
    }//end initMap()


     // MUST HAVE GOOGLE PLAY SERVICE INSTALLED ON THE PHONE
     // Execute the map activities
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        defaultLocation(43.9444, -78.896748, 18f);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.INTERNET}, 1234);
            return;
        }
        mMap.setMyLocationEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);

        addRoomMarkers();
    }

    //Go To UOIT's location
    private void defaultLocation(Double lat, Double lng, float zoom) {
        LatLng latLng = new LatLng(lat, lng);
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, zoom);
        mMap.moveCamera(cameraUpdate);
    }

    // Get current Device location
    public void getDeviceLocation() {

        //Initialize our Location Provider: that's google map
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(MapsActivity.this, new String[]{Manifest.permission.INTERNET,
                    Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION}, 456);
            return;
        }
        mFusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        //GPS Location can be null if GPS is turned of
                        if (location != null) {
                            myLocation = location;
                            Log.d("Current Locatio: ", "" + myLocation.getLatitude() + ", " + myLocation.getLongitude());
                            onLocationChange(location);
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(MapsActivity.this, "GPS doesn't seem to be ON", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void onLocationChange(Location location) {
        LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());
        CameraUpdate cameraUpdate = CameraUpdateFactory.newLatLngZoom(latLng, 18f);
        mMap.animateCamera(cameraUpdate);
    }


    public void addRoomMarkers() {
        for (Map.Entry mapElement : UAMapCords.entrySet()) {
            String key = (String) mapElement.getKey();
            mMap.addMarker(new MarkerOptions().position((LatLng) mapElement.getValue()).title(key));

        }
    }


    //Add initial room markers
    public void getRoomMarkers() {

        for (int i = 0; i < UARooms.length; i++) {
            //System.out.println(UARooms);
            Coordinates room = getRoomCoords(UARooms[i]);
            UAMapCords.put(UARooms[i], new LatLng(room.getLat(), room.getLon()));
            //System.out.println("Room " + UARooms[i] + " Coords: " + room.toString());
        }
    }

    // make rest api calls to dataset to get coords for room
    public Coordinates getRoomCoords(String room) {
        // This is the fix......
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        HttpsURLConnection con = null;
        String responseData = null;
        try {
            //URL url = new URL("https://api.mapbox.com/datasets/v1/karan8patel/cjtm7sw9h01tb63p71shk2x1s/features/"+ room + "/?access_token=pk.eyJ1Ijoia2FyYW44cGF0ZWwiLCJhIjoiY2p0bTZudno1MGUydTQ0bzI4YmoxNDhiaiJ9.Fnb0WkR_evUb0BJyHxjKWw");

            URL url = new URL("https://api.mapbox.com/datasets/v1/karan8patel/" + getString(R.string.dataset_key) + "/features/" + room + "/?access_token=" + getString(R.string.access_tocken_mapbox));
            con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36");

            con.connect();
            if (con.getResponseCode() == 200) {
                BufferedReader bf = new BufferedReader(new InputStreamReader(con.getInputStream()));
                responseData = bf.readLine();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
        //Log.d("MapsActivity", responseData);

        JsonParser jp = new JsonParser();
        JsonObject jo = (JsonObject) jp.parse(responseData);
        JsonObject joCords = (JsonObject) jp.parse(jo.get("geometry").toString());
        //System.out.println(joCords.get("coordinates").toString());
        JsonArray ja = joCords.get("coordinates").getAsJsonArray();

        Coordinates cords = new Coordinates(ja.get(1).getAsDouble(), ja.get(0).getAsDouble());

        return cords;
    }


    // Make API calls to backend server to get location service
    public void findFromRoom(String room1, String room2) {
        LatLng roomlatLng1 = null;
        LatLng roomlatLng2 = null;

        // This is the fix......
        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        // requesting our path finder server for path
        HttpsURLConnection con = null;
        String responseData = null;
        try {
            URL url = new URL("https://ua-pathfinder.herokuapp.com/api/pathfind?room1=" + room1 + "&room2=" + room2);
            con = (HttpsURLConnection) url.openConnection();
            con.setRequestMethod("GET");
            con.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.86 Safari/537.36");

            con.connect();
            if (con.getResponseCode() == 200) {
                BufferedReader bf = new BufferedReader(new InputStreamReader(con.getInputStream()));
                responseData = bf.readLine();
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (con != null) {
                con.disconnect();
            }
        }
        Toast.makeText(this, responseData, Toast.LENGTH_SHORT).show();

        //parsing the data
        JsonParser jp = new JsonParser();
        JsonObject jo = (JsonObject) jp.parse(responseData);
        JsonArray ja = jo.get("path").getAsJsonArray();
        ArrayList<Coordinates> path = new ArrayList<>();
        for (int i = 0; i < ja.size(); i++) {
            path.add(new Coordinates(ja.get(i).getAsJsonArray().get(1).getAsDouble(), ja.get(i).getAsJsonArray().get(0).getAsDouble()));
        }
        System.out.println(path);


        //Make polyline from path
        PolylineOptions polylineOptions = new PolylineOptions();

        for (int i = 0; i < path.size(); i++) {
            polylineOptions.add(new LatLng(path.get(i).getLat(), path.get(i).getLon()));
        }

        mMap.addPolyline(polylineOptions);

    }

    //Start the voice input
    private void startVoiceInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hello, How can I help you?");
        try {
            startActivityForResult(intent, REQ_CODE_SPEECH_INPUT);
        } catch (ActivityNotFoundException a) {
            Toast.makeText(this, String.valueOf(a.getMessage()), Toast.LENGTH_LONG);
        }
    }

    //process the voice
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case REQ_CODE_SPEECH_INPUT: {
                if (resultCode == RESULT_OK && null != data) {
                    ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    message = result.get(0);
                    message = message.toUpperCase();
                    try {
                        mLanguageClient = LanguageServiceClient.create(
                                LanguageServiceSettings.newBuilder()
                                        .setCredentialsProvider(() ->
                                                GoogleCredentials.fromStream(MapsActivity.this
                                                        .getResources()
                                                        .openRawResource(R.raw.credential)))
                                        .build());
                    } catch (IOException e) {
                        throw new IllegalStateException("Unable to create a language client", e);
                    }

                    AnalyzeEntitiesRequest request = AnalyzeEntitiesRequest.newBuilder()
                            .setDocument(Document.newBuilder()
                                    .setContent(message)
                                    .setType(Document.Type.PLAIN_TEXT)
                                    .build())
                            .build();

                    AnalyzeEntitiesResponse ar = mLanguageClient.analyzeEntities(request);
                    Log.d("MapsActivity", ar.toString());
                    for (Entity ent : ar.getEntitiesList()) {
                        if (ent.getSalience() <= 0.85){
                            if (ent.getName().length() == 6) {
                                reqNodes.add(ent.getName());
                            }
                        }
                    }
                    Log.d("MapsActivity", reqNodes.toString());
                    //findFromRoom("ua1120", "ua1350");

                    if (reqNodes.size() == 1) {
                        // findFromRoom from current Location to the destination
                        String destination = reqNodes.get(0);
                        //find
                    } else if (reqNodes.size() == 2) {
                        String sNode = reqNodes.get(0);
                        String eNode = reqNodes.get(1);
                        Toast.makeText(this, sNode + " " + eNode, Toast.LENGTH_SHORT).show();
                        findFromRoom(sNode, eNode);
                    }
                    //Toast.makeText(this, reqNodes.toString(), Toast.LENGTH_SHORT).show();

                    //reqNodes.clear();

                }
                break;
            }
        }
    }

    //Async taske for proccessing the voice
    private class AnalyzeTask extends AsyncTask<String, Void, AnalyzeEntitiesResponse> {

        @Override
        protected AnalyzeEntitiesResponse doInBackground(String... args) {
            AnalyzeEntitiesRequest request = AnalyzeEntitiesRequest.newBuilder()
                    .setDocument(Document.newBuilder()
                            .setContent(args[0])
                            .setType(Document.Type.PLAIN_TEXT)
                            .build())
                    .build();
            return mLanguageClient.analyzeEntities(request);
        }

        @Override
        protected void onPostExecute(AnalyzeEntitiesResponse analyzeEntitiesResponse) {
            // update UI with results 
            mVoiceInputTv.setText(analyzeEntitiesResponse.toString());
        }
    }
}
