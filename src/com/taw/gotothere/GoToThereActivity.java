package com.taw.gotothere;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.SearchRecentSuggestions;
import android.util.Log;
import android.view.Menu;
import android.widget.SearchView;
import android.widget.Toast;

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
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.taw.gotothere.model.DirectionsLeg;
import com.taw.gotothere.model.DirectionsResult;
import com.taw.gotothere.model.DirectionsRoute.RouteBounds;
import com.taw.gotothere.model.DirectionsStep;
import com.taw.gotothere.provider.GoToThereSuggestionProvider;

public class GoToThereActivity extends Activity implements
	ConnectionCallbacks,
	OnConnectionFailedListener {

	/** Logging. */
	private static final String TAG = "GoToThereActivity";
	
	// Maps objects
	
	/** Google Map object. */
	private GoogleMap map;
	/** Placed marker for the destination. */
	private Marker destinationMarker;
	/** Marker for the start point of the route. */
	private Marker originMarker;
	/** Current directions polyline. */
	private Polyline polyline;
	
	// Instance state keys
	
	/** Key for origin latitude. */
	private static final String ORIGIN_LAT = "origin_lat";
	/** Key for origin longitude. */
	private static final String ORIGIN_LNG = "origin_lng";
	/** Key for destination latitude. */
	private static final String DEST_LAT = "dest_lat";
	/** Key for destination latitude. */
	private static final String DEST_LNG = "dest_lng";

	// Misc
	
	/** Request code for return by Services. */
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	
	/** Location client, obtained from Play Services. */
	private LocationClient locationClient;
	
	/** AsyncTask to retrieve directions. */
	private DirectionsTask directionsTask;
	
	/** Progress dialog for getting directions. */
	private ProgressDialog progress;
	
	/** Click listener for the map. */
	private OnMapClickListener mapClickListener = new OnMapClickListener() {

		/**
		 * Initially just display a marker where the user taps.
		 */
		@Override
		public void onMapClick(LatLng latLng) {
			if (destinationMarker != null) destinationMarker.remove();
			destinationMarker = placeMarker(latLng, null, null, BitmapDescriptorFactory.HUE_RED);
			
			if (servicesConnected()) {
				getDirections(latLng);
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
    	
    	// Only intent we're interested in is ACTION_SEARCH, but we defer
    	// handling it until we're connected to the location client -
    	// see onConnected()
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
        
    	// Set up search widget
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        
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
            case CONNECTION_FAILURE_RESOLUTION_REQUEST:
            	// TODO
                switch (resultCode) {
                    case Activity.RESULT_OK:
                    /*
                     * Try the request again
                     */
                    break;
                }
        }
     }
    
	/* (non-Javadoc)
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		
		// By definition, if we have an origin, we will also have a
		// destination
		if (savedInstanceState.containsKey(ORIGIN_LAT)) {
			double lat = savedInstanceState.getDouble(ORIGIN_LAT);
			double lng = savedInstanceState.getDouble(ORIGIN_LNG);			
			originMarker = placeMarker(new LatLng(lat, lng), null, null, BitmapDescriptorFactory.HUE_GREEN);
			
			lat = savedInstanceState.getDouble(DEST_LAT);
			lng = savedInstanceState.getDouble(DEST_LNG);
			destinationMarker = placeMarker(new LatLng(lat, lng), null, null, BitmapDescriptorFactory.HUE_RED);
			
			getDirections(originMarker.getPosition(), destinationMarker.getPosition());
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// Any other marker data (titles, snippets) will be recreated
		// on restore via the getDirections call; we just need lats/lngs
		if (originMarker != null) {
			outState.putDouble(ORIGIN_LAT, originMarker.getPosition().latitude);
			outState.putDouble(ORIGIN_LNG, originMarker.getPosition().longitude);
		}
		if (destinationMarker != null) {
			outState.putDouble(DEST_LAT, destinationMarker.getPosition().latitude);
			outState.putDouble(DEST_LNG, destinationMarker.getPosition().longitude);			
		}
		super.onSaveInstanceState(outState);
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
    	
    	// If the activity was started via a search, update suggestions
    	// And reverse-geocode the address
    	Intent intent = getIntent();
        if (intent.getAction().equals(Intent.ACTION_SEARCH)) {
        	String query = intent.getStringExtra(SearchManager.QUERY);

    		SearchRecentSuggestions suggestions = 
    			new SearchRecentSuggestions(this, 
    					GoToThereSuggestionProvider.AUTHORITY, GoToThereSuggestionProvider.MODE);
            suggestions.saveRecentQuery(query, null);
            
            reverseGeocodeQuery(query);	            
        }
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
     * @param end End point the user has selected
     */
	private void getDirections(LatLng end) {
		Location loc = locationClient.getLastLocation();
		getDirections(new LatLng(loc.getLatitude(), loc.getLongitude()), end);
	}

	/**
	 * Start a thread to retrieve directions from the supplied start point to the
	 * selected end point.
	 * @param start LatLng representing the start point
	 * @param end LatLng representing the end point
	 */
	private void getDirections(LatLng start, LatLng end) {
		if (directionsTask == null || directionsTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
			directionsTask = new DirectionsTask(this, start, end); 
			directionsTask.execute();
			
			// Show progress dialog box
			progress = new ProgressDialog(this);
			progress.setMessage(getResources().getString(R.string.progress_directions));
			progress.show();
		}				
	}
	
    /**
     * Process DirectionsTask results by generating route display - 
     * a polyline of the route, plus a start marker on the map. Destination
     * marker has already been placed by the user, so we just add some
     * details to it.
     */
	// Will be private once using broadcast to return AsyncTask results
	public void showDirections(DirectionsResult directions) {
		progress.cancel();
		
		DirectionsLeg firstLeg = directions.routes.get(0).legs.get(0);
		
		// Add extra detail to the destination marker
		destinationMarker.setTitle(firstLeg.endAddress);
		destinationMarker.setSnippet(firstLeg.distance.text);
		// Snap user-placed marker to end location defined in direction
		// results - may remove.
		destinationMarker.setPosition(firstLeg.endLocation.toLatLng());
		
		// Create the origin marker
		if (originMarker != null) originMarker.remove();
		originMarker = placeMarker(firstLeg.startLocation.toLatLng(), firstLeg.startAddress, null, BitmapDescriptorFactory.HUE_GREEN);
			
		// Loop through results and display polyline
		PolylineOptions polyOpts = new PolylineOptions().
				width(10).
				color(Color.GREEN);
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

		// Finally, pan the map to encompass the route lat/lng bounds
		RouteBounds routeBounds = directions.routes.get(0).bounds;
		LatLngBounds bounds = new LatLngBounds(routeBounds.sw.toLatLng(), routeBounds.ne.toLatLng());
    	map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
	}
	
	/**
	 * Add a marker to the map at the supplied position.
	 * 
	 * @param position
	 * @param title
	 * @param snippet
	 * @return Marker instance generated by the map
	 */
	private Marker placeMarker(LatLng position, String title, String snippet, float colour) {
		MarkerOptions opts = new MarkerOptions().
				position(position).
				title(title).
				snippet(snippet).
				icon(BitmapDescriptorFactory.defaultMarker(colour));
		
		return map.addMarker(opts);
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
	private List<LatLng> decodePolyline(String polyline) {

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

	/**
	 * Reverse-geocode the location the user typed into the search box, and centre the map
	 * on it.
	 */
	private void reverseGeocodeQuery(String address) {
		Geocoder geo = new Geocoder(this, Locale.getDefault());
		try {
			List<Address> addresses = geo.getFromLocationName(address, 10);			// Hmmmm, 1?
			if (addresses.size() > 0) {
				LatLng position = new LatLng(addresses.get(0).getLatitude(),
						addresses.get(0).getLongitude());
				destinationMarker = placeMarker(position, null, null, BitmapDescriptorFactory.HUE_RED);
				if (servicesConnected()) {
					getDirections(position);
				}
			} else {
				Toast.makeText(this, R.string.error_not_found_text, Toast.LENGTH_SHORT).show();
			}
		} catch (IOException ioe) {
			Log.e(TAG, "Could not geocode '" + address + "'", ioe);
			Toast.makeText(this, R.string.error_general_text, Toast.LENGTH_SHORT).show();
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
