package com.taw.gotothere;

import java.io.IOException;

import android.app.Activity;
import android.os.AsyncTask;
import android.util.Log;

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
import com.taw.gotothere.model.DirectionsResult;

public class DirectionsTask extends AsyncTask<Void, Integer, DirectionsResult> {
	/** Reference to calling activity. */
	private GoToThereActivity activity;
	
	/** Start point of route. */
	private LatLng origin;
	/** End point of route. */
	private LatLng destination;


	public DirectionsTask(GoToThereActivity activity, LatLng origin, LatLng destination) {
		super();

		this.activity = activity;
		
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
		Log.d("DirectionsTask", "some msg to breakpoint on");
		activity.showDirections(route);
	}

	/**
	 * Direction URL into Google Directions service 
	 */
	private static class DirectionsUrl extends GenericUrl {

		private static final String URL = "http://maps.googleapis.com/maps/api/directions/json";

		public DirectionsUrl(String encodedUrl) {
			super(encodedUrl);
		}

		public static DirectionsUrl getDirectionsByLatLng(LatLng origin,
				LatLng destination) {
			// Better way of doing this...
			String params = "sensor=true&mode=walking";
			params += "&origin=" + origin.latitude + "," + origin.longitude;
			params += "&destination=" + destination.latitude + ","
					+ destination.longitude;
			return new DirectionsUrl(URL + "?" + params);
		}
	}
}
