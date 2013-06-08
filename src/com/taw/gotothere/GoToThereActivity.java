package com.taw.gotothere;

import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.taw.gotothere.model.DirectionsLeg;
import com.taw.gotothere.model.DirectionsResult;
import com.taw.gotothere.model.DirectionsStep;

public class GoToThereActivity extends Activity implements
	ConnectionCallbacks,
	OnConnectionFailedListener {

	/** Logging. */
	private static final String TAG = "GoToThereActivity";
	
	/** Google Map object. */
	private GoogleMap map;
	/** Placed marker for the destination. */
	private Marker destinationMarker;
	/** Marker for the start point of the route. */
	private Marker originMarker;
	/** Current directions polyline. */
	private Polyline polyline;
	
	/** Request code for return by Services. */
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	
	/** Location client, obtained from Play Services. */
	private LocationClient locationClient;
	
	/** AsyncTask to retrieve directions. */
	private DirectionsTask directionsTask;
	
	/** Click listener for the map. */
	private OnMapClickListener mapClickListener = new OnMapClickListener() {

		/**
		 * Initially just display a marker where the user taps.
		 */
		@Override
		public void onMapClick(LatLng latLng) {
			if (destinationMarker != null) destinationMarker.remove();
			MarkerOptions opts = new MarkerOptions().
					position(latLng).
					icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));
			destinationMarker = map.addMarker(opts);
			
			if (servicesConnected()) {
				Location loc = locationClient.getLastLocation();
				getDirections(new LatLng(loc.getLatitude(), loc.getLongitude()), latLng);
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

	/**
	 * Once connected to the location service, get last known location
	 * and pan the map camera to it.
	 */
	@Override
	public void onConnected(Bundle bundle) { 
    	Location loc = locationClient.getLastLocation();
    	LatLng centre = new LatLng(loc.getLatitude(), loc.getLongitude());
    	map.moveCamera(CameraUpdateFactory.newLatLngZoom(centre, 16));
	}

	@Override
	public void onDisconnected() { }
    
    
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

    /**
	 * Start a thread to retrieve the directions from the user's current location
	 * to their selected point.
	 */
	private void getDirections(LatLng start, LatLng end) {
		if (directionsTask == null || directionsTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
			directionsTask = new DirectionsTask(this, start, end); 
			directionsTask.execute();
		}		
	}

    /**
     * Process DirectionsTask results by generating route display - 
     * a polyline of the route, plus a start marker on the map. 
     */
	public void showDirections(DirectionsResult directions) {		
		DirectionsLeg firstLeg = directions.routes.get(0).legs.get(0);
		
		// Add extra detail to the destination marker
		destinationMarker.setTitle(firstLeg.endAddress);
		destinationMarker.setSnippet(firstLeg.distance.text);
		
		// Create the origin marker
//		MarkerOptions opts = new MarkerOptions().
//				position(new LatLng(directions.)).
//				title("Navigate to this point").
//				snippet("1.2km from current location").
//				icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN));
//		originMarker = map.addMarker(opts);
		
		// Loop through results and display polyline
		PolylineOptions polyOpts = new PolylineOptions().
				width(10).
				color(Color.CYAN);
		for (DirectionsLeg leg : directions.routes.get(0).legs) {
			for (DirectionsStep step : leg.steps) {
				List<LatLng> points = decodePolyline(step.polyline.points);
				for (LatLng l : points) {
					polyOpts.add(l);
				}
				
			}
		}
		if (polyline != null) polyline.remove();
		polyline = map.addPolyline(polyOpts);
	}
	
	/**
	 * The Directions API returns a start & end point for the leg, but
	 * this would just yield a straight line on the map, which only approximately
	 * follows roads, so we use the polyline associated with each step instead. 
	 * This is encoded as per Google's Polyline encoding algorithm. The decoder 
	 * below is taken (with much relief) from:
	 * 
	 * http://jeffreysambells.com/posts/2010/05/27/decoding-polylines-from-google-maps-direction-api-with-java/
	 * 
	 * @return points a List of LatLngs for the polyline of this step
	 */
	public List<LatLng> decodePolyline(String polyline) {

	    List<LatLng> points = new ArrayList<LatLng>();
	    int index = 0, len = polyline.length();
	    int lat = 0, lng = 0;

	    while (index < len) {
	        int b, shift = 0, result = 0;
	        do {
	            b = polyline.charAt(index++) - 63;
	            result |= (b & 0x1f) << shift;
	            shift += 5;
	        } while (b >= 0x20);
	        int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
	        lat += dlat;

	        shift = 0;
	        result = 0;
	        do {
	            b = polyline.charAt(index++) - 63;
	            result |= (b & 0x1f) << shift;
	            shift += 5;
	        } while (b >= 0x20);
	        int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
	        lng += dlng;

	        LatLng latLng = new LatLng(lat / 1E5, lng / 1E5);
	        points.add(latLng);
	    }

	    return points;
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
