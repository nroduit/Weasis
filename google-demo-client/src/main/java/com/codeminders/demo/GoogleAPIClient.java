package com.codeminders.demo;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.ListProjectsResponse;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.oauth2.Oauth2;
import com.google.api.services.oauth2.model.Tokeninfo;
import com.google.api.services.oauth2.model.Userinfoplus;

public class GoogleAPIClient {
	/**
	 * Be sure to specify the name of your application. If the application name is
	 * {@code null} or blank, the application will log a warning. Suggested format
	 * is "MyCompany-ProductName/1.0".
	 */
	private static final String APPLICATION_NAME = "Codeminders-GoogleHealthViewerDemo/1.0";

	/** Directory to store user credentials. */
	private static final java.io.File DATA_STORE_DIR = new java.io.File(System.getProperty("user.home"),".store/google_viewer_auth");

	/**
	 * Global instance of the {@link DataStoreFactory}. The best practice is to make
	 * it a single globally shared instance across your application.
	 */
	private static FileDataStoreFactory dataStoreFactory;

	/** Global instance of the HTTP transport. */
	private static HttpTransport httpTransport;

	/** Global instance of the JSON factory. */
	private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
	
	/** OAuth 2.0 scopes. */
	private static final List<String> SCOPES = Arrays.asList(
			"https://www.googleapis.com/auth/userinfo.profile", 
			"https://www.googleapis.com/auth/userinfo.email",
			"https://www.googleapis.com/auth/cloud-platform");

	private static Oauth2 oauth2;
	private static GoogleClientSecrets clientSecrets;

	/** Instance of Google Cloud Resource Manager */
	private static CloudResourceManager cloudResourceManager;

	private boolean isSignedIn = false;
	
	protected GoogleAPIClient() {
	}
	
	private static Credential authorize() throws Exception {
		// load client secrets
		clientSecrets = GoogleClientSecrets.load(JSON_FACTORY,
				new InputStreamReader(GoogleAPIClient.class.getResourceAsStream("/client_secrets.json")));
		if (clientSecrets.getDetails().getClientId().startsWith("Enter")
				|| clientSecrets.getDetails().getClientSecret().startsWith("Enter ")) {
			System.out.println("Enter Client ID and Secret from https://code.google.com/apis/console/ "
					+ "into src/main/resources/client_secrets.json");
			System.exit(1);
		}
		// set up authorization code flow
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(httpTransport, JSON_FACTORY,
				clientSecrets, SCOPES).setDataStoreFactory(dataStoreFactory).build();
		// authorize
		return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
	}

	public void signIn() throws Exception{
		if (!isSignedIn) {
			httpTransport = GoogleNetHttpTransport.newTrustedTransport();
			dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
			// authorization
			Credential credential = authorize();
			// set up global Oauth2 instance
			oauth2 = new Oauth2.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME)
					.build();
			
			cloudResourceManager = new CloudResourceManager.Builder(httpTransport, JSON_FACTORY, credential)
					.build();
			
			// run commands
			tokenInfo(credential.getAccessToken());
			userInfo();
			isSignedIn = true;
		}
	}

	private static void tokenInfo(String accessToken) throws IOException {
		System.out.println("Validating token");
		Tokeninfo tokeninfo = oauth2.tokeninfo().setAccessToken(accessToken).execute();
		System.out.println(tokeninfo.toString());
		if (!tokeninfo.getAudience().equals(clientSecrets.getDetails().getClientId())) {
			System.err.println("ERROR: audience does not match our client ID!");
		}
	}

	private static void userInfo() throws IOException {
		System.out.println("Obtaining User Profile Information");
		Userinfoplus userinfo = oauth2.userinfo().get().execute();
		System.out.println(userinfo.toString());
	}
	
	public List<String> fetchProjects() throws Exception {
		signIn();
		List<String> result = new ArrayList<String>();
		CloudResourceManager.Projects.List request = cloudResourceManager.projects().list();
	    ListProjectsResponse response;
	    do {
	      response = request.execute();
	      if (response.getProjects() == null) {
	        continue;
	      }
	      for (Project project : response.getProjects()) {
	        result.add(project.getName());
	      }
	      request.setPageToken(response.getNextPageToken());
	    } while (response.getNextPageToken() != null);
	    return result;
	}
	
}
