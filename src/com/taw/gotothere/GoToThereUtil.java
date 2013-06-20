/**
 * 
 */
package com.taw.gotothere;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.provider.Settings;

/**
 * @author chris
 *
 */
public class GoToThereUtil {

	/**
	 * Return a Network Unavailable dialog for embedding within
	 * a fragment.
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
}
