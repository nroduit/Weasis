package com.codeminders.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@EnableOAuth2Sso // Without this, basic authentication is invoked
@ComponentScan("com.codeminders.demo")
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
