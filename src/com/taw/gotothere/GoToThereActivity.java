package com.taw.gotothere;

import android.app.Activity;
import android.os.Bundle;
import android.view.Menu;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;

public class GoToThereActivity extends Activity {

	/** Google Map object. */
	private GoogleMap map;
	
	/** Click listener for the map. */
	private OnMapClickListener mapClickListener = new OnMapClickListener() {

		/**
		 * Initially just display a marker where the user taps.
		 */
		@Override
		public void onMapClick(LatLng latLng) {
			// Draw circle object
			CircleOptions opts = new CircleOptions().
					center(latLng).
					radius(20);
			Circle circle = map.addCircle(opts);
		}
		
	};
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        
        map = ((MapFragment) getFragmentManager().findFragmentById(R.id.map)).getMap();
        map.setMyLocationEnabled(true);
        
        map.setOnMapClickListener(mapClickListener);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.go_to_there, menu);
        return true;
    }
    
}
