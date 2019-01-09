package com.codeminders.demo.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.provider.OAuth2Authentication;
import org.springframework.security.oauth2.provider.authentication.OAuth2AuthenticationDetails;
import org.springframework.stereotype.Service;

@Service
public class UserService {

	public String getGoogleToken() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    	String token = "";
		if (auth instanceof OAuth2Authentication) {
    		token = ((OAuth2AuthenticationDetails)((OAuth2Authentication)auth).getDetails()).getTokenValue();
    	}
		return token;
	}
	
}
