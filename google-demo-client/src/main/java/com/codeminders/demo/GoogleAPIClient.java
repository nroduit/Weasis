package com.codeminders.demo;

import com.codeminders.demo.model.Dataset;
import com.codeminders.demo.model.DicomStore;
import com.codeminders.demo.model.Location;
import com.codeminders.demo.model.ProjectDescriptor;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.*;
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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
			"https://www.googleapis.com/auth/cloud-healthcare",
			"https://www.googleapis.com/auth/cloud-platform",
			"https://www.googleapis.com/auth/userinfo.profile", 
			"https://www.googleapis.com/auth/userinfo.email");

	private static Oauth2 oauth2;
	private static GoogleClientSecrets clientSecrets;

	/** Instance of Google Cloud Resource Manager */
	private static CloudResourceManager cloudResourceManager;

	private boolean isSignedIn = false;
	private String accessToken;
	
	protected GoogleAPIClient() {
	}
	
	private static Credential authorize() throws Exception {
		// load client secrets
		try (InputStream in = getSecret()) {
			clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
		}
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

	private static InputStream getSecret() throws IOException {
		String file = System.getProperty("google.client.secret");
		if (file != null) {
			return new FileInputStream(file);
		}

		String portableDir = System.getProperty("weasis.portable.dir");
		if (portableDir != null) {
			return new FileInputStream(portableDir + File.separator + "client_secrets.json");
		}

		String url = System.getProperty("weasis.codebase.url");
		return new URL(url + "/client_secrets.json").openStream();
	}

	public String getAccessToken() {
	    if (accessToken == null) {
	        signIn();
        }
        return accessToken;
    }

	public void signIn() {
		if (!isSignedIn) {
			int tryCount = 0;
			Exception error;
			do {
				try {
					tryCount++;
					httpTransport = GoogleNetHttpTransport.newTrustedTransport();
					dataStoreFactory = new FileDataStoreFactory(DATA_STORE_DIR);
					// authorization
					Credential credential = authorize();
					// set up global Oauth2 instance
					oauth2 = new Oauth2.Builder(httpTransport, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME)
							.build();
					
					cloudResourceManager = new CloudResourceManager.Builder(httpTransport, JSON_FACTORY, credential)
							.build();
					accessToken = credential.getAccessToken();
					GoogleAuthStub.setAuthToken(accessToken);
					// run commands
					tokenInfo(accessToken);
					userInfo();
					error = null;
					isSignedIn = true;
				} catch(Exception e) {
					error = e;
					e.printStackTrace();
					System.out.println("Retry authorization:");
				}
				} while (!isSignedIn && tryCount < 4);
			if (error != null) {
				throw new IllegalStateException(error);
			}
		}
	}

	public void clearSignIn() {
		deleteDir(DATA_STORE_DIR);
	}

	private void deleteDir(File file) {
		if (!file.exists()) {
			return;
		}
		if (file.isDirectory()) {
			for (File child: file.listFiles()) {
				deleteDir(child);
			}
		}
		file.delete();
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
	
	public List<ProjectDescriptor> fetchProjects() throws Exception {
		signIn();
		List<ProjectDescriptor> result = new ArrayList<ProjectDescriptor>();
		CloudResourceManager.Projects.List request = cloudResourceManager.projects().list();
	    ListProjectsResponse response;
	    do {
	      response = request.execute();
	      if (response.getProjects() == null) {
	        continue;
	      }
	      for (Project project : response.getProjects()) {
	        result.add(new ProjectDescriptor(project.getName(), project.getProjectId()));
	      }
	      request.setPageToken(response.getNextPageToken());
	    } while (response.getNextPageToken() != null);
	    return result;
	}
	
	private String parseName(String name) {
		return name.substring(name.lastIndexOf("/") + 1);
	}
	
	private HttpResponse googleRequest(String url) throws Exception {
		signIn();
		HttpRequest request = httpTransport.createRequestFactory().buildGetRequest(new GenericUrl(url));
	    HttpHeaders headers = new HttpHeaders();
	    headers.set("Authorization", Arrays.asList(new String[] {"Bearer " + accessToken}));
	    request.setHeaders(headers);
		return request.execute();
	}
	
	public List<Location> fetchLocations(ProjectDescriptor project) throws Exception {
		signIn();
		String url = "https://healthcare.googleapis.com/v1alpha/projects/" + project.getId() + "/locations";
		String data = googleRequest(url).parseAsString();
		JsonParser parser = new JsonParser();
		JsonElement jsonTree = parser.parse(data);
	    JsonArray jsonObject = jsonTree.getAsJsonObject().get("locations").getAsJsonArray();
        return StreamSupport.stream(jsonObject.spliterator(), false)
            .map(JsonElement::getAsJsonObject)
            .map(obj -> new Location(project,
                    obj.get("name").getAsString(),
                    obj.get("locationId").getAsString()))
            .collect(Collectors.toList());
	}
	
	public List<Dataset> fetchDatasets(Location location) throws Exception {
		signIn();
		String url = "https://healthcare.googleapis.com/v1alpha/projects/"+location.getParent().getId()+"/locations/"+location.getId()+"/datasets";
		String data = googleRequest(url).parseAsString();
		JsonParser parser = new JsonParser();
		JsonElement jsonTree = parser.parse(data);
	    JsonArray jsonObject = jsonTree.getAsJsonObject().get("datasets").getAsJsonArray();
	    return StreamSupport.stream(jsonObject.spliterator(), false)
		    .map(obj -> obj.getAsJsonObject().get("name").getAsString())
		    .map(this::parseName)
            .map(name -> new Dataset(location, name))
		    .collect(Collectors.toList());
	}

	public List<DicomStore> fetchDicomstores(Dataset dataset) throws Exception {
		signIn();
		String url = "https://healthcare.googleapis.com/v1alpha"
                        + "/projects/" + dataset.getProject().getId()
                        + "/locations/" + dataset.getParent().getId()
                        + "/datasets/" + dataset.getName() + "/dicomStores";
		String data = googleRequest(url).parseAsString();
		JsonParser parser = new JsonParser();
		JsonElement jsonTree = parser.parse(data);
	    JsonArray jsonObject = jsonTree.getAsJsonObject().get("dicomStores").getAsJsonArray();

	    return StreamSupport.stream(jsonObject.spliterator(), false)
		    .map(obj -> obj.getAsJsonObject().get("name").getAsString())
		    .map(this::parseName)
            .map(name -> new DicomStore(dataset, name))
		    .collect(Collectors.toList());
	}

	public static String getImageUrl(DicomStore store, String studyId) {
        return "https://healthcare.googleapis.com/v1alpha"
                + "/projects/" + store.getProject().getId()
                + "/locations/" + store.getLocation().getId()
                + "/datasets/" + store.getParent().getName()
                + "/dicomStores/" + store.getName()
                + "/dicomWeb/studies/" + studyId;
    }
	
}
