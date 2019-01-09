package com.codeminders.demo;

public class GoogleAPIClientFactory {

	private static GoogleAPIClientFactory instance;

	private GoogleAPIClient googleAPIClient;

	/**
	 * TODO: Probably it should be refactored, so this class become OSGi component.
	 * GetInstance method should be removed after that.
	 */
	public static GoogleAPIClientFactory getInstance() {
		if (instance == null) {
			instance = new GoogleAPIClientFactory();
		}
		return instance;
	}

	public GoogleAPIClient createGoogleClient() {
		if (googleAPIClient == null) {
			googleAPIClient = new GoogleAPIClient();
		}
		return googleAPIClient;
	}

}
