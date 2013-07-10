package com.taw.gotothere;

import android.app.Activity;
import android.app.Dialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
import com.google.android.gms.maps.model.LatLng;
import com.taw.gotothere.fragment.GoToThereDialogFragment;
import com.taw.gotothere.fragment.GoToThereMapFragment;
import com.taw.gotothere.provider.GoToThereSuggestionProvider;

public class GoToThereActivity extends Activity implements
	ConnectionCallbacks,
	OnConnectionFailedListener {

	/** Logging. */
	private static final String TAG = "GoToThereActivity";
	
	/** MapFragment containing the map view. */
	private GoToThereMapFragment mapFragment;

	/** Request code for return by Services. */
	private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
	
	/** Location client, obtained from Play Services. */
	private LocationClient locationClient;

	/** Shared preference, indicating whether user has accepted the 'terms'. */
	private static final String ACCEPTED_TOC = "ACCEPTED_TOC";
	
	/** Broadcast receiver for receiving network events. */
	private NetworkReceiver receiver;	
		
// General activity overrides

	/* Only intent we're interested in is ACTION_SEARCH, but we defer
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
        
		mapFragment = (GoToThereMapFragment) getFragmentManager().findFragmentById(R.id.map);
        
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
    	mapFragment.getMap().moveCamera(CameraUpdateFactory.newLatLngZoom(centre, 16));
    	
    	// If the activity was started via a search, update suggestions
    	// and reverse-geocode the address
    	Intent intent = getIntent();
        if (intent.getAction().equals(Intent.ACTION_SEARCH)) {
        	String query = intent.getStringExtra(SearchManager.QUERY);

    		SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this, 
					GoToThereSuggestionProvider.AUTHORITY, GoToThereSuggestionProvider.MODE);
            suggestions.saveRecentQuery(query, null);
            
            Toast.makeText(this, R.string.reverse_geocoding_query, Toast.LENGTH_SHORT).show();
            LatLng position = GoToThereUtil.reverseGeocode(this, query);
            if (position != null) {
            	mapFragment.placeDestinationMarker(position, query, getResources().getString(R.string.tap_hint_snippet), true);
            } else {
				Toast.makeText(this, R.string.error_not_found_text, Toast.LENGTH_SHORT).show();
            }
        }
    }

	@Override
	public void onDisconnected() { }
      
	/**
	 * Determines whether the device has internet connectivity; if not
	 * we won't be able to get map data or directions.
	 * @return
	 */
	public boolean networkConnected() {
	    ConnectivityManager connMgr = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected()) {
        	return false;
        }
        
        return true;
	}

	/**
	 * Test if Google Play Services is available on the user's device,
	 * and take action if not, or if the version is lower than we require
	 * @return true if available
	 */
    public boolean servicesConnected() {
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
	 * @return the locationClient
	 */
	public LocationClient getLocationClient() {
		return locationClient;
	}   
    
// Private methods
	
	/**
	 * Cancel the navigation display - remove markers and polyline
	 * form the map, and remove any instance state pertaining to
	 * this route.
	 */
	private void cancelNavigation() {
		mapFragment.clearAll();		
		// Remove instance state?
	}

//	/**
//	 * Reverse-geocode the location the user typed into the search box.
//	 */
//	// TODO
//	private LatLng reverseGeocode(String address) {
//		Geocoder geo = new Geocoder(this, Locale.getDefault());
//		LatLng position = null;
//		try {
//			List<Address> addresses = geo.getFromLocationName(address, 1);
//			if (addresses.size() > 0) {
//				position = new LatLng(addresses.get(0).getLatitude(),
//						addresses.get(0).getLongitude());
//			}
//		} catch (IOException ioe) {
//			Log.e(TAG, "Could not geocode '" + address + "'", ioe);
//			Toast.makeText(this, R.string.error_general_text, Toast.LENGTH_SHORT).show();
//		}
//		
//		return position;
//	}
	
//	/**
//	 * Geocode the location provided by the user's map-tap.
//	 * @param latLng
//	 * @return
//	 */
//	// TODO
//	private String geocode(LatLng latLng) {
//		Geocoder geo = new Geocoder(this, Locale.getDefault());
//		String address = null;
//		try {
//			List<Address> addresses = geo.getFromLocation(latLng.latitude, latLng.longitude, 1);
//			if (addresses.size() > 0) {
//				Address addr = addresses.get(0);
//				address = addr.getAddressLine(0);
//				address += ", " + addr.getLocality();
//			} else {
//				Toast.makeText(this, R.string.error_not_found_text, Toast.LENGTH_SHORT).show();
//			}
//			
//		} catch (IOException ioe) {
//			Log.e(TAG, "Could not geocode '" + latLng + "'", ioe);
//			Toast.makeText(this, R.string.error_general_text, Toast.LENGTH_SHORT).show();			
//		}
//		
//		return address;
//	}
	
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
     * Register the NetworkReceiver to get updates about network
     * availability
     */
    private void registerNetworkReceiver() {
    	IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new NetworkReceiver(locationClient);
        registerReceiver(receiver, filter);
    }
}
