package com.taw.gotothere;

import java.io.IOException;

import android.os.AsyncTask;

import com.google.android.gms.maps.model.LatLng;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.taw.gotothere.fragment.GoToThereMapFragment;
import com.taw.gotothere.model.DirectionsResult;

public class DirectionsTask extends AsyncTask<Void, Integer, DirectionsResult> {
	
	/** Reference to parent fragment. */
	private GoToThereMapFragment fragment;
	
	/** Start point of route. */
	private LatLng origin;
	/** End point of route. */
	private LatLng destination;


	public DirectionsTask(GoToThereMapFragment fragment, LatLng origin, LatLng destination) {
		super();
		
		this.fragment = fragment;
		
		this.origin = origin;
		this.destination = destination;		
	}
	
	@Override
	protected DirectionsResult doInBackground(Void... params) {
     	HttpTransport transport = new NetHttpTransport();
     	final JsonFactory jsonFactory = new JacksonFactory();
     	
     	HttpRequestFactory reqFactory = transport.createRequestFactory(new HttpRequestInitializer() {
			@Override
			public void initialize(HttpRequest req) throws IOException {
				req.setParser(new JsonObjectParser(jsonFactory));
			}
     	});
     	
     	// URL
     	DirectionsUrl url = DirectionsUrl.getDirectionsByLatLng(origin, destination);
     	
     	DirectionsResult result = null;
     	try {
         	HttpRequest req = reqFactory.buildGetRequest(url);
     		result = req.execute().parseAs(DirectionsResult.class);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
     	
     	return result;
	}

	@Override
	protected void onPostExecute(DirectionsResult route) {
		// Tell the fragment to display the route
		fragment.showDirections(route);
	}

	/**
	 * Direction URL into Google Directions service 
	 */
	private static class DirectionsUrl extends GenericUrl {

		private static final String URL = "http://maps.googleapis.com/maps/api/directions/json";

		public DirectionsUrl(String encodedUrl) {
			super(encodedUrl);
			put("sensor", "true");
			put("mode", "walking");
		}

		public static DirectionsUrl getDirectionsByLatLng(LatLng origin, LatLng destination) {
			DirectionsUrl url = new DirectionsUrl(URL);
			url.put("origin", origin.latitude + "," + origin.longitude);
			url.put("destination", destination.latitude + "," + destination.longitude);
			
			return url;
		}
	}
}
