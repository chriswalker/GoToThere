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
import android.content.IntentFilter;
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
import android.view.MenuItem;
import android.widget.SearchView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks;
import com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener;
import com.google.android.gms.common.GooglePlayServicesUtil;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
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
	private MapsHelper mapsHelper = null;
	
	// Instance state keys
	
	/** Key for origin latitude. */
	private static final String ORIGIN_LAT = "origin_lat";
	/** Key for origin longitude. */
	private static final String ORIGIN_LNG = "origin_lng";
	/** Key for origin address (text). */
	private static final String ORIGIN_TITLE = "origin_text";
	/** Key for destination latitude. */
	private static final String DEST_LAT = "dest_lat";
	/** Key for destination latitude. */
	private static final String DEST_LNG = "dest_lng";
	/** Key for destination address (text). */
	private static final String DEST_TITLE = "dest_text";
	/** Key for destination snippet. */
	private static final String DEST_SNIPPET = "dest_snippet";
	

	// Misc
	
	/** Request code for return by Services. */
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	
	/** Location client, obtained from Play Services. */
	private LocationClient locationClient;
	
	/** AsyncTask to retrieve directions. */
	private DirectionsTask directionsTask;
	
	/** Progress dialog for getting directions. */
	private ProgressDialog progress;

	/** Shared preference, indicating whether user has accepted the 'terms'. */
	private static final String ACCEPTED_TOC = "ACCEPTED_TOC";
	
	/** Broadcast receiver for receiving network events. */
	private NetworkReceiver receiver;
	
	/** Click listener for the map. */
	private OnMapClickListener mapClickListener = new OnMapClickListener() {

		/**
		 * Display a marker where the user taps, geocode the map position into
		 * an address for the InfoWindow text, and show the InfoWindow.
		 */
		@Override
		public void onMapClick(LatLng latLng) {
			if (networkConnected()) {
				if (!mapsHelper.displayingRoute()) {
					mapsHelper.placeDestinationMarker(latLng, getResources().getString(R.string.getting_address_text), null, true);
					String address = geocode(latLng);
					if (address != null) {
						mapsHelper.updateDestinationMarkerText(address, getResources().getString(R.string.tap_hint_snippet));
					}
				}
			} else {
				// Hmmm
				displayActionsDisabledToast();
			}
		}
	};
	
	/** Click listener for info window clicks. */
	private OnInfoWindowClickListener infoWindowClickListener = new OnInfoWindowClickListener() {

		/* (non-Javadoc)
		 * @see com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener#onInfoWindowClick(com.google.android.gms.maps.model.Marker)
		 */
		@Override
		public void onInfoWindowClick(Marker marker) {
			if (servicesConnected() && networkConnected()) {
				// The destination marker was clicked
				getDirections(marker.getPosition());
				marker.hideInfoWindow();
			}			
		}
	};
	
	/*
	 * General activity overrides
	 */

	/*
	 * Only intent we're interested in is ACTION_SEARCH, but we defer
   	 * handling it until we're connected to the location client - see onConnected()
   	 * 
	 * (non-Javadoc)
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

		if (!getPreferences(MODE_PRIVATE).getBoolean(ACCEPTED_TOC, false)) {
			showFirstRunDialog();
		}
        
    	mapsHelper = new MapsHelper(this);
        mapsHelper.getMap().setOnMapClickListener(mapClickListener);
        mapsHelper.getMap().setOnInfoWindowClickListener(infoWindowClickListener);
        
    	locationClient = new LocationClient(this, this, this);
    	
        if (networkConnected()) {
        	registerNetworkReceiver();
        }
    }

    /* (non-Javadoc)
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		if (receiver != null) {
			unregisterReceiver(receiver);
		}
		super.onDestroy();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onStart()
	 */
	@Override
	protected void onStart() {
		super.onStart();
		if (networkConnected()) {
			locationClient.connect();
		}
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onStop()
	 */
	@Override
	protected void onStop() {
		if (networkConnected()) {
			locationClient.disconnect();
		}
		super.onStop();
	}

	
	
	/* We may be resuming because the user has backed into the activity from
	 * the settings, if they had a network issue and clicked on the Settings
	 * button in the "No Network" dialog; we check if the NetworkReceiver is
	 * set up, and if not register it.
	 * 
	 * (non-Javadoc)
	 * @see android.app.Activity#onResume()
	 */
	@Override
	protected void onResume() {
		// register receivers in here if null
		if (receiver == null) registerNetworkReceiver();
		
		super.onResume();
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onCreateOptionsMenu(android.view.Menu)
	 */
	@Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.go_to_there, menu);
        
    	// Set up search widget
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        
        return true;
    }


	/* (non-Javadoc)
	 * @see android.app.Activity#onOptionsItemSelected(android.view.MenuItem)
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_clear:
			cancelNavigation();
		}
		// Others to follow
		return false;
	}

	/* Handle results from other activities - currently just response
     * from Google Play Services, potentially called if the user has to
     * try and rectify a problem with the location services.
	 *
	 * (non-Javadoc)
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
		
		double lat, lng;
		String title, snippet = null;
		
		if (savedInstanceState.containsKey(DEST_LAT)) {
			lat = savedInstanceState.getDouble(DEST_LAT);
			lng = savedInstanceState.getDouble(DEST_LNG);
			title = savedInstanceState.getString(DEST_TITLE);
			snippet = savedInstanceState.getString(DEST_SNIPPET);
			mapsHelper.placeDestinationMarker(new LatLng(lat, lng), title, snippet, false);			
		}
		
		if (savedInstanceState.containsKey(ORIGIN_LAT)) {
			lat = savedInstanceState.getDouble(ORIGIN_LAT);
			lng = savedInstanceState.getDouble(ORIGIN_LNG);
			title = savedInstanceState.getString(ORIGIN_TITLE);
			mapsHelper.placeOriginMarker(new LatLng(lat, lng), title, snippet);

			// If we have an origin, we by definition also have a destination, so we 
			// previously plotted directions.
			// TODO: Will cache directions in bundle to save doing this again
			getDirections(mapsHelper.getOriginMarker().getPosition(), 
					mapsHelper.getDestinationMarker().getPosition());
		}		
	}

	/* (non-Javadoc)
	 * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (mapsHelper != null) {
			Marker marker = mapsHelper.getOriginMarker();
			if (marker != null) {
				outState.putDouble(ORIGIN_LAT, marker.getPosition().latitude);
				outState.putDouble(ORIGIN_LNG, marker.getPosition().longitude);
				outState.putString(ORIGIN_TITLE, marker.getTitle());
			}
			marker = mapsHelper.getDestinationMarker();
			if (marker != null) {
				outState.putDouble(DEST_LAT, marker.getPosition().latitude);
				outState.putDouble(DEST_LNG, marker.getPosition().longitude);
				outState.putString(DEST_TITLE, marker.getTitle());
				outState.putString(DEST_SNIPPET, marker.getSnippet());			
			}
		}
		super.onSaveInstanceState(outState);
	}
    
    /*
     * Google Play Services implementations
     */

	/* (non-Javadoc)
	 * @see com.google.android.gms.common.GooglePlayServicesClient.OnConnectionFailedListener#onConnectionFailed(com.google.android.gms.common.ConnectionResult)
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

	/* Once connected to the location service, get last known location
	 * and pan the map camera to it.
	 *
	 * (non-Javadoc)
	 * @see com.google.android.gms.common.GooglePlayServicesClient.ConnectionCallbacks#onConnected(android.os.Bundle)
	 */
	@Override
	public void onConnected(Bundle bundle) { 
    	Location loc = locationClient.getLastLocation();
    	LatLng centre = new LatLng(loc.getLatitude(), loc.getLongitude());
    	mapsHelper.getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(centre, 16));
    	
    	// If the activity was started via a search, update suggestions
    	// and reverse-geocode the address
    	Intent intent = getIntent();
        if (intent.getAction().equals(Intent.ACTION_SEARCH)) {
        	String query = intent.getStringExtra(SearchManager.QUERY);

    		SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this, 
					GoToThereSuggestionProvider.AUTHORITY, GoToThereSuggestionProvider.MODE);
            suggestions.saveRecentQuery(query, null);
            
            Toast.makeText(this, R.string.reverse_geocoding_query, Toast.LENGTH_SHORT).show();
            LatLng position = reverseGeocode(query);
            if (position != null) {
            	mapsHelper.placeDestinationMarker(position, query, getResources().getString(R.string.tap_hint_snippet), true);
            } else {
				Toast.makeText(this, R.string.error_not_found_text, Toast.LENGTH_SHORT).show();
            }
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
	private void cancelNavigation() {
		mapsHelper.clearAll();		
		// Remove instance state?
	}

	/**
	 * Reverse-geocode the location the user typed into the search box.
	 */
	// TODO
	private LatLng reverseGeocode(String address) {
		Geocoder geo = new Geocoder(this, Locale.getDefault());
		LatLng position = null;
		try {
			List<Address> addresses = geo.getFromLocationName(address, 1);
			if (addresses.size() > 0) {
				position = new LatLng(addresses.get(0).getLatitude(),
						addresses.get(0).getLongitude());
			}
		} catch (IOException ioe) {
			Log.e(TAG, "Could not geocode '" + address + "'", ioe);
			Toast.makeText(this, R.string.error_general_text, Toast.LENGTH_SHORT).show();
		}
		
		return position;
	}
	
	/**
	 * Geocode the location provided by the user's map-tap.
	 * @param latLng
	 * @return
	 */
	// TODO
	private String geocode(LatLng latLng) {
		Geocoder geo = new Geocoder(this, Locale.getDefault());
		String address = null;
		try {
			List<Address> addresses = geo.getFromLocation(latLng.latitude, latLng.longitude, 1);
			if (addresses.size() > 0) {
				Address addr = addresses.get(0);
				address = addr.getAddressLine(0);
				address += ", " + addr.getLocality();
			} else {
				Toast.makeText(this, R.string.error_not_found_text, Toast.LENGTH_SHORT).show();
			}
			
		} catch (IOException ioe) {
			Log.e(TAG, "Could not geocode '" + latLng + "'", ioe);
			Toast.makeText(this, R.string.error_general_text, Toast.LENGTH_SHORT).show();			
		}
		
		return address;
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
	 * Display the Terms and Conditions alert dialog.
	 */
	private void showFirstRunDialog() {
	    Dialog dialog = GoToThereUtil.getFirstRunDialog(this);
	    if (dialog != null) {
            GoToThereDialogFragment dialogFragment = new GoToThereDialogFragment();
            dialogFragment.setDialog(dialog);
            dialogFragment.show(getFragmentManager(), "First Run");	    	
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
    
    /**
     * Display a toast to the user indicated map interactions have been disabled
     * due to no network connectivity. We have to do it here as there is no
     * access to a context from within the OnMapClickListener, alas.
     */
    private void displayActionsDisabledToast() {
		Toast.makeText(this, R.string.map_actions_disabled, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Register the NetworkReceiver to get updates about network
     * availability
     */
    private void registerNetworkReceiver() {
    	IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new NetworkReceiver(locationClient);
        registerReceiver(receiver, filter);
    }
}
