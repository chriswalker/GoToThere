package com.taw.gotothere.model;

import java.util.List;

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
 	
 	public static class Distance {
 		@Key
 		public String text;
 		
 		@Key
 		public int value;
 	}
}
