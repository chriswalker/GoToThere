/**
 * 
 */
package com.taw.gotothere;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Settings;

/**
 * @author chris
 *
 */
public class GoToThereUtil {

	// TODO - create constants file
	/** Shared preference, indicating whether user has accepted the 'terms'. */
	private static final String ACCEPTED_TOC = "ACCEPTED_TOC";
	
	/**
	 * Return a Network Unavailable dialog for embedding within a fragment.
	 * @param activity
	 * @return Dialog constructed dialog
	 */
	public static Dialog getNoNetworkDialog(final Activity activity) {
		return new AlertDialog.Builder(activity)
			.setTitle(activity.getResources().getString(R.string.no_network_dialog_title))
			.setMessage(activity.getResources().getString(R.string.no_network_dialog_text))
			.setNegativeButton(activity.getResources().getString(R.string.settings_button_text), 
				new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   activity.startActivity(new Intent(Settings.ACTION_SETTINGS));
		           }
		       })
		    .setPositiveButton(activity.getResources().getString(R.string.ok_button_text), 
				new DialogInterface.OnClickListener() {
		           public void onClick(DialogInterface dialog, int id) {
		        	   activity.finish();
		           }
		       })
			.create();
	}

	/**
	 * Return a First Run dialog for embedding within a fragment.
	 * @param activity
	 * @return Dialog constructed dialog
	 */
	public static Dialog getFirstRunDialog(final Activity activity) {
		final SharedPreferences prefs = activity.getPreferences(Activity.MODE_PRIVATE);
		
		return new AlertDialog.Builder(activity)
			.setTitle(activity.getResources().getString(R.string.first_run_dialog_title))
			.setMessage(activity.getResources().getString(R.string.first_run_text))
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
}
