package com.taw.gotothere.model;

import java.util.List;

import com.google.android.gms.maps.model.LatLng;
import com.google.api.client.util.Key;

public class DirectionsLeg {
 	@Key
 	public List<DirectionsStep> steps;
 	
 	@Key
 	public Distance distance;
 	
 	@Key("start_address")
 	public String startAddress;
 	
 	@Key("end_address")
 	public String endAddress;
 	
 	@Key("start_location")
 	public Location startLocation;
 	
 	@Key("end_location")
 	public Location endLocation;
 	
 	public static class Distance {
 		@Key
 		public String text;
 		
 		@Key
 		public int value;
 	}
 	
 	public static class Location {
 		@Key
 		public float lat;
 		
 		@Key
 		public float lng;
 		
 		public LatLng toLatLng() {
 			return new LatLng(lat, lng);
 		}
 	}
 	
}
