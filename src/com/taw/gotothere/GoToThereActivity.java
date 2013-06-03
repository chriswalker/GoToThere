package com.taw.gotothere;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

public class GoToThereActivity extends Activity implements
	ConnectionCallbacks,
	OnConnectionFailedListener {

	/** Logging. */
	private static final String TAG = "GoToThereActivity";
	
	/** Google Map object. */
	private GoogleMap map;
	/** Placed marker. */
	private Marker marker;
	/** Request code for return by Services. */
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	
	/** Location client, obtained from Play Services. */
	private LocationClient locationClient;
	
	/** Click listener for the map. */
	private OnMapClickListener mapClickListener = new OnMapClickListener() {

		/**
		 * Initially just display a marker where the user taps.
		 */
		@Override
		public void onMapClick(LatLng latLng) {
			if (marker != null) marker.remove();
			MarkerOptions opts = new MarkerOptions().
					position(latLng).
					title("Navigate to this point").
					snippet("1.2km from current location").
					icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
			marker = map.addMarker(opts);
			
			if (servicesConnected()) {
				Location loc = locationClient.getLastLocation();
				Log.d(TAG, "Last known location was: " + loc.getLatitude() + "/" + loc.getLongitude());
				//getDirections(new LatLng(loc.getLatitude(), loc.getLongitude()), latLng);
			}
		}
	};
		
	/*
	 * General activity overrides
	 */
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        map.setMyLocationEnabled(true);
        
        map.setOnMapClickListener(mapClickListener);
        
    	locationClient = new LocationClient(this, this, this);
    }

    @Override
	protected void onStart() {
		super.onStart();
		locationClient.connect();
	}

	@Override
	protected void onStop() {
		locationClient.disconnect();
		super.onStop();
	}

	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.go_to_there, menu);
        return true;
    }

    /**
     * Handle results from other activities - currently just response
     * from Google Play Services, potentially called if the user has to
     * try and rectify a problem with the location services.
     */
    @Override
    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case CONNECTION_FAILURE_RESOLUTION_REQUEST :
            	// TODO
                switch (resultCode) {
                    case Activity.RESULT_OK :
                    /*
                     * Try the request again
                     */
                    break;
                }
        }
     }
    
    /*
     * Google Play Services implementations
     */
    
	@Override
	public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(
                        this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            showErrorDialog(connectionResult.getErrorCode());
        }
    }


	@Override
	public void onConnected(Bundle bundle) {
		// TODO - use R.string
		Toast.makeText(this, "Connected", Toast.LENGTH_SHORT).show();
	}


	@Override
	public void onDisconnected() {
		// TODO - use R.string
        Toast.makeText(this, "Disconnected. Please re-connect.", Toast.LENGTH_SHORT).show();		
	}
    
    
// Private methods
   
	/**
	 * Test if Google Play Services is available on the user's device,
	 * and take action if not, or if the version is lower than we require
	 * @return true if available
	 */
    private boolean servicesConnected() {
        int resultCode = GooglePlayServicesUtil.isGooglePlayServicesAvailable(this);
        if (ConnectionResult.SUCCESS == resultCode) {
            Log.d(TAG, "Google Play services is available.");
            return true;
        } else {
        	Log.d(TAG, "Google Play Services unavailable.");
        	showErrorDialog(resultCode);        	
        }
        
        return false; // hmmm
    }
    
    /**
     * Construct and display the Play Services error dialog, to give the
     * user a chance to rectify the problem.
     * @param errorCode The error code reported back from GooglePlayServicesUil
     */
    private void showErrorDialog(int errorCode) {
        Dialog errorDialog = GooglePlayServicesUtil.getErrorDialog(
                errorCode,
                this,
                CONNECTION_FAILURE_RESOLUTION_REQUEST);

        if (errorDialog != null) {
            ErrorDialogFragment errorFragment = new ErrorDialogFragment();
            errorFragment.setDialog(errorDialog);
            errorFragment.show(getFragmentManager(), "Location Updates");
        }

    }

	/*
	 * Error Dialog for Play Services issues.
	 */
    
    public static class ErrorDialogFragment extends DialogFragment {
        // Global field to contain the error dialog
        private Dialog dialog;
        // Default constructor. Sets the dialog field to null
        public ErrorDialogFragment() {
            super();
            dialog = null;
        }
        // Set the dialog to display
        public void setDialog(Dialog dialog) {
            this.dialog = dialog;
        }
        // Return a Dialog to the DialogFragment.
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return dialog;
        }
    }

}
