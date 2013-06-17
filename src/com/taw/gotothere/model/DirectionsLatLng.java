package com.taw.gotothere.model;

import com.google.android.gms.maps.model.LatLng;
import com.google.api.client.util.Key;

public class DirectionsLatLng {
	@Key
	public float lat;
	
	@Key
	public float lng;
	
	public LatLng toLatLng() {
		return new LatLng(lat, lng);
	}
}
