/**
 * 
 */
package com.taw.gotothere;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;

/**
 * General utility methods.
 * 
 * @author chris
 */
public class GoToThereUtil {

	/** Logging tag. */
	private static final String TAG = GoToThereUtil.class.getName();
	
	// TODO - create constants file
	/** Shared preference, indicating whether user has accepted the 'terms'. */
	private static final String ACCEPTED_TOC = "ACCEPTED_TOC";
	
	/**
	 * Return a First Run dialog for embedding within a fragment.
	 * @param activity
	 * @return Dialog constructed dialog
	 */
	public static Dialog getFirstRunDialog(final Activity activity) {
		final SharedPreferences prefs = activity.getPreferences(Activity.MODE_PRIVATE);
		
		return new AlertDialog.Builder(activity)
			.setTitle(activity.getResources().getString(R.string.first_run_dialog_title))
			.setMessage(activity.getResources().getString(R.string.first_run_dialog_text))
			.setPositiveButton(activity.getResources().getString(R.string.accept_button_label), 
				new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   prefs.edit().putBoolean(ACCEPTED_TOC, true).commit();
		           }
		       })
		    .setNegativeButton(activity.getResources().getString(R.string.dont_accept_button_label), 
		    	new DialogInterface.OnClickListener() {
		    		public void onClick(DialogInterface dialog, int id) {
		    			activity.finish();
		    		}
		       })
	       .create();
	}
	
	/**
	 * Reverse-geocode the location the user typed into the search box.
	 */
	// TODO
	public static LatLng reverseGeocode(Context context, String address) {
		Geocoder geo = new Geocoder(context, Locale.getDefault());
		LatLng position = null;
		try {
			List<Address> addresses = geo.getFromLocationName(address, 1);
			if (addresses.size() > 0) {
				position = new LatLng(addresses.get(0).getLatitude(),
						addresses.get(0).getLongitude());
			}
		} catch (IOException ioe) {
			Log.e(TAG, "Could not geocode '" + address + "'", ioe);
			//Toast.makeText(context, R.string.error_general_text, Toast.LENGTH_SHORT).show();
			// Caller deals with a null position
		}
		
		return position;
	}
	
	/**
	 * Geocode the location provided by the user's map-tap.
	 * @param latLng
	 * @return
	 */
	// TODO
	public static String geocode(Context context, LatLng latLng) {
		Geocoder geo = new Geocoder(context, Locale.getDefault());
		String address = null;
		try {
			List<Address> addresses = geo.getFromLocation(latLng.latitude, latLng.longitude, 1);
			if (addresses.size() > 0) {
				Address addr = addresses.get(0);
				address = addr.getAddressLine(0);
				address += ", " + addr.getLocality();
			} else {
				Toast.makeText(context, R.string.error_not_found_text, Toast.LENGTH_SHORT).show();
			}
			
		} catch (IOException ioe) {
			Log.e(TAG, "Could not geocode '" + latLng + "'", ioe);
			Toast.makeText(context, R.string.error_general_text, Toast.LENGTH_SHORT).show();			
		}
		
		return address;
	}
}
