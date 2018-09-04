package com.codeminders.demo.service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.cloudresourcemanager.model.ListProjectsResponse;
import com.google.api.services.cloudresourcemanager.model.Project;

@Service
public class GoogleCloudService {
	
	@Autowired
	private UserService userService;
	
	private CloudResourceManager createCloudResourceManagerService() throws IOException, GeneralSecurityException {
		HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
		JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

		String accessToken = userService.getGoogleToken();
		
		GoogleCredential credential = new GoogleCredential().setAccessToken(accessToken);
		if (credential.createScopedRequired()) {
			credential = credential.createScoped(Arrays.asList("https://www.googleapis.com/auth/cloud-platform"));
		}

		return new CloudResourceManager.Builder(httpTransport, jsonFactory, credential)
				.setApplicationName("DICOM Viewer Google API Demo").build();
	}
	
	public java.util.List<String> getProjects() throws Exception {
		java.util.List<String> result = new ArrayList<>();
		CloudResourceManager cloudResourceManagerService = createCloudResourceManagerService();
		CloudResourceManager.Projects.List request = cloudResourceManagerService.projects().list();

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
