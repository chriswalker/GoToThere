package com.taw.gotothere.model;

import java.util.List;

import com.google.api.client.util.Key;

public class DirectionsRoute {
 	@Key
 	public List<DirectionsLeg> legs;
 	
// 	@Key
// 	public String copyright;
 	
 	@Key
 	public RouteBounds bounds;
 	
 	public static class RouteBounds {
 		@Key("northeast")
 		public DirectionsLatLng ne;
 		
 		@Key("southwest")
 		public DirectionsLatLng sw;
 	}
}
