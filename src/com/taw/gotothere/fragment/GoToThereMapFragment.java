package com.taw.gotothere.fragment;

import java.util.ArrayList;
import java.util.List;

import android.app.ProgressDialog;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnInfoWindowClickListener;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.taw.gotothere.DirectionsTask;
import com.taw.gotothere.GoToThereActivity;
import com.taw.gotothere.GoToThereUtil;
import com.taw.gotothere.R;
import com.taw.gotothere.model.DirectionsLeg;
import com.taw.gotothere.model.DirectionsResult;
import com.taw.gotothere.model.DirectionsRoute.RouteBounds;
import com.taw.gotothere.model.DirectionsStep;

/**
 * Extension of the basic MapFragment, that is responsible for handling the
 * required marker and polyline placements. It also contains its own AsyncTask
 * for retrieving directions results.
 * 
 * @author chris
 */
public class GoToThereMapFragment extends MapFragment {

	// Maps objects
	
	/** Placed marker for the destination. */
	private Marker destinationMarker = null;
	/** Marker for the start point of the route. */
	private Marker originMarker = null;
	/** Current directions polyline. */
	private Polyline polyline;
	
	/** AsyncTask to retrieve directions. */
	private DirectionsTask directionsTask;
	
	/** Progress dialog for getting directions. */
	// TODO: move?
	private ProgressDialog progress;
	
	/** Click listener for the map. */
	private OnMapClickListener mapClickListener = new OnMapClickListener() {

		/**
		 * Display a marker where the user taps, geocode the map position into
		 * an address for the InfoWindow text, and show the InfoWindow.
		 */
		@Override
		public void onMapClick(LatLng latLng) {
			GoToThereActivity activity = (GoToThereActivity) getActivity();
			if (activity.networkConnected()) {
				if (!displayingRoute()) {
					placeDestinationMarker(latLng, getResources().getString(R.string.getting_address_text), null, true);
					String address = GoToThereUtil.geocode(getActivity(), latLng);
					if (address != null) {
						updateDestinationMarkerText(address, getResources().getString(R.string.tap_hint_snippet));
					}
				}
			} else {
				Toast.makeText(getActivity(), R.string.map_actions_disabled, Toast.LENGTH_SHORT).show();
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
			GoToThereActivity activity = (GoToThereActivity) getActivity();
			if (activity.servicesConnected() && activity.networkConnected()) {
				if (!displayingRoute() && marker.getSnippet() != null) {
					// No route, and this marker has a snippet (and so is the
					// destination marker)
					Location loc = activity.getLocationClient().getLastLocation();
					getDirections(new LatLng(loc.getLatitude(), loc.getLongitude()));
					marker.hideInfoWindow();
				}
			}			
		}
	};
		
// Overrides
	
	/* (non-Javadoc)
	 * @see com.google.android.gms.maps.MapFragment#onCreateView(android.view.LayoutInflater, android.view.ViewGroup, android.os.Bundle)
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, 
			ViewGroup container,
			Bundle savedInstanceState) {
		View mapView = super.onCreateView(inflater, container, savedInstanceState);

		GoogleMap map = getMap();
        map.setMyLocationEnabled(true);
        map.setOnMapClickListener(mapClickListener);
        map.setOnInfoWindowClickListener(infoWindowClickListener);
        
        setRetainInstance(true);		

        return mapView;
	}
		
// Public methods, usually called from the Activity
		
	/**
	 * Places an origin marker on the map.
	 * 
	 * @param latLng Location to place the marker
	 * @param title String to use as the marker title, or null
	 * @param snippet String to use as the marker snippet, or null
	 */
	public void placeOriginMarker(LatLng latLng, String title, String snippet) {
		if (originMarker != null) originMarker.remove();
		originMarker = placeMarker(latLng, title, snippet, BitmapDescriptorFactory.HUE_GREEN);
	}

	/**
	 * Places a destination marker on the map. We might want to display the InfoWindow
	 * of the marker after placement.
	 * 
	 * @param latLng Location to place the marker
	 * @param title String to use as the marker title, or null
	 * @param snippet String to use as the marker snippet, or null
	 */
	public void placeDestinationMarker(LatLng latLng, String title, String snippet, boolean showInfoWindow) {
		if (destinationMarker != null) destinationMarker.remove();
		destinationMarker = placeMarker(latLng, title, snippet, BitmapDescriptorFactory.HUE_RED);
		if (showInfoWindow) {
			destinationMarker.showInfoWindow();
		}
		
		getMap().moveCamera(CameraUpdateFactory.newLatLng(latLng));
	}

	/**
	 * Remove markers and polyline from the map.
	 */
	public void clearAll() {
		if (originMarker != null) { 
			originMarker.remove();
			originMarker = null;
			polyline.remove();
			polyline = null;
		}
		
		if (destinationMarker != null) {
			destinationMarker.remove();
			destinationMarker = null;
		}
	}
	
	/**
	 * Helper method to report if we are displaying a route.
	 * 
	 * @return true if we have a polyline, false otherwise
	 */
	public boolean displayingRoute() {
		return polyline != null ? true : false;
	}
	
// Private methods	
	
	/**
	 * Update the destination marker text with a new title and snippet, and
	 * default to showing the InfoWindow.
	 * @param title
	 * @param snippet
	 */
	private void updateDestinationMarkerText(String title, String snippet) {
		if (title != null) destinationMarker.setTitle(title);
		if (snippet != null) destinationMarker.setSnippet(snippet);

		destinationMarker.showInfoWindow();
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
		
		return getMap().addMarker(opts);
	}
	
    /**
     * Process DirectionsTask results by generating route display - 
     * a polyline of the route, plus a start marker on the map. Destination
     * marker has already been placed by the user, so we just add some
     * details to it.
     */
	public void showDirections(DirectionsResult directions) {
		progress.cancel();
		
		DirectionsLeg firstLeg = directions.routes.get(0).legs.get(0);
		
		// Add extra detail to the destination marker
		destinationMarker.setSnippet(firstLeg.distance.text);
		// Snap user-placed marker to end location defined in direction results
		destinationMarker.setPosition(firstLeg.endLocation.toLatLng());
		
		// Create the origin marker
		placeOriginMarker(firstLeg.startLocation.toLatLng(), firstLeg.startAddress, null);
			
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
		polyline = getMap().addPolyline(polyOpts);
		
		// Finally, pan the map to encompass the route lat/lng bounds
		RouteBounds routeBounds = directions.routes.get(0).bounds;
		LatLngBounds bounds = new LatLngBounds(routeBounds.sw.toLatLng(), routeBounds.ne.toLatLng());
    	getMap().moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 100));
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
	 * Start a thread to retrieve directions from the supplied start point to the
	 * selected end point.
	 */
	private void getDirections(LatLng start) {
		if (directionsTask == null || directionsTask.getStatus().equals(AsyncTask.Status.FINISHED)) {
			directionsTask = new DirectionsTask(this, start, destinationMarker.getPosition()); 
			directionsTask.execute();
			
			// Show progress dialog box
			progress = new ProgressDialog(getActivity());
			progress.setMessage(getResources().getString(R.string.progress_directions));
			progress.show();
		}				
	}
	
// Accessors, mainly used for saving instance state (maps markers not saved
// by default)
	
	/**
	 * @return the destinationMarker
	 */
	public Marker getDestinationMarker() {
		return destinationMarker;
	}

	/**
	 * @return the originMarker
	 */
	public Marker getOriginMarker() {
		return originMarker;
	}
}
