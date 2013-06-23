package com.taw.gotothere;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.taw.gotothere.fragment.GoToThereDialogFragment;
import com.taw.gotothere.model.DirectionsResult;
import com.taw.gotothere.provider.GoToThereSuggestionProvider;

public class GoToThereActivity extends Activity implements
	ConnectionCallbacks,
	OnConnectionFailedListener {

	/** Logging. */
	private static final String TAG = "GoToThereActivity";
	
	/** Helper for maps interactions. */
	private MapsHelper mapsHelper;
	
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
			mapsHelper.placeDestinationMarker(latLng, null, null);
			
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

        if (networkConnected()) {
        	mapsHelper = new MapsHelper(this);
	        mapsHelper.getMap().setOnMapClickListener(mapClickListener);
	        
	    	locationClient = new LocationClient(this, this, this);
	    	
	    	// Only intent we're interested in is ACTION_SEARCH, but we defer
	    	// handling it until we're connected to the location client -
	    	// see onConnected()
        } else {
        	showNoNetworkDialog();
        }
    }

    @Override
	protected void onStart() {
		super.onStart();
		if (networkConnected()) {
			locationClient.connect();
		}
	}

	@Override
	protected void onStop() {
		if (networkConnected()) {
			locationClient.disconnect();
		}
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
			mapsHelper.placeOriginMarker(new LatLng(lat, lng), null, null);
			
			lat = savedInstanceState.getDouble(DEST_LAT);
			lng = savedInstanceState.getDouble(DEST_LNG);
			mapsHelper.placeDestinationMarker(new LatLng(lat, lng), null, null);
			
			getDirections(mapsHelper.getOriginMarker().getPosition(), 
					mapsHelper.getDestinationMarker().getPosition());
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// Any other marker data (titles, snippets) will be recreated
		// on restore via the getDirections call; we just need lats/lngs
		Marker marker = mapsHelper.getOriginMarker();
		if (marker != null) {
			outState.putDouble(ORIGIN_LAT, marker.getPosition().latitude);
			outState.putDouble(ORIGIN_LNG, marker.getPosition().longitude);
		}
		marker = mapsHelper.getDestinationMarker();
		if (marker != null) {
			outState.putDouble(DEST_LAT, marker.getPosition().latitude);
			outState.putDouble(DEST_LNG, marker.getPosition().longitude);			
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
    	mapsHelper.getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(centre, 16));
    	
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
	public void showDirections(DirectionsResult directions) {
		mapsHelper.showDirections(directions);
		progress.cancel();
	}
	
	/**
	 * Cancel the navigation display - remove markers and polyline
	 * form the map, and remove any instance state pertaining to
	 * this route.
	 */
//	private void cancelNavigation() {
//		// Remove markers
//		
//		// Remove polyline
//		
//		// Remove instance state?
//		
//		actionMode.finish();
//	}

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
				mapsHelper.placeDestinationMarker(position, null, null);
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
	
	/**
	 * Determines whether the device has internet connectivity; if not
	 * we won't be able to get map data or directions.
	 * @return
	 */
	private boolean networkConnected() {
	    ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
        	return false;
        }
        
        return true;
	}
	
	/**
	 * Display the No Network Access alert dialog.
	 */
	private void showNoNetworkDialog() {
	    Dialog dialog = GoToThereUtil.getNoNetworkDialog(this);
	    if (dialog != null) {
            GoToThereDialogFragment dialogFragment = new GoToThereDialogFragment();
            dialogFragment.setDialog(dialog);
            dialogFragment.show(getFragmentManager(), "No Network");	    	
	    }
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
            GoToThereDialogFragment dialogFragment = new GoToThereDialogFragment();
            dialogFragment.setDialog(errorDialog);
            dialogFragment.show(getFragmentManager(), "Location Updates");
        }
    }
}
