package com.codeminders.demo.controller;

import java.security.Principal;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.codeminders.demo.service.GoogleCloudService;
import com.codeminders.demo.service.UserService;

@RestController
@RequestMapping("/")
public class UserRestController {

	@Autowired
	private UserService userService;
	
	@Autowired
	private GoogleCloudService cloudService;
	
    @GetMapping(value = "user")
    public Principal user(Principal user) {
        return user;
    }
    
    @GetMapping(value = "token")
    public String token() {
    	return userService.getGoogleToken();
    }
    
    @GetMapping(value = "project")
    public List<String> folders() throws Exception {
    	return cloudService.getProjects();
    }
    
}
