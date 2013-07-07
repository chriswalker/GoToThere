/**
 * 
 */
package com.taw.gotothere;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.widget.Toast;

/**
 * @author chris
 *
 */
public class NetworkReceiver extends BroadcastReceiver {
	
	/* (non-Javadoc)
	 * @see android.content.BroadcastReceiver#onReceive(android.content.Context, android.content.Intent)
	 */
	@Override
	public void onReceive(Context context, Intent intent) {
		ConnectivityManager conn =  (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
	    NetworkInfo networkInfo = conn.getActiveNetworkInfo();
	    if (networkInfo == null || !networkInfo.isAvailable()) {
	    	Toast.makeText(context, R.string.no_network_available, Toast.LENGTH_SHORT).show();
	    }
	}

}
