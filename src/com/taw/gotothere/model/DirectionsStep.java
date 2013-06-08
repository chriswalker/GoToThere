package com.taw.gotothere.model;

import com.google.api.client.util.Key;

public class DirectionsStep { 	
 	@Key
 	public Polyline polyline;
 	
 	// Prefer not to do this....
 	public static class Polyline {
 		@Key
 		public String points;
 	}
}
