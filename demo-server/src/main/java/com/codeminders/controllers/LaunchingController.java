package com.codeminders.controllers;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URL;
import java.util.UUID;

@Controller
public class LaunchingController {

    @GetMapping("/")
    public String index(HttpServletRequest request, Model model) {
        model.addAttribute("server", getHostAndPort(request));
        model.addAttribute("salt", UUID.randomUUID().toString());
        return "main";
    }

    @GetMapping(value = "/weasis", produces = "application/x-java-jnlp-file")
    public String jnlp(HttpServletRequest request, Model model) {
        model.addAttribute("ihs", "128m");
        model.addAttribute("mhs", "768m");
        model.addAttribute("jnlp_jar_version", "false");
        model.addAttribute("cdb", "http://" + getHostAndPort(request) + "/weasis");

        return "weasis";
    }

    private String getHostAndPort(HttpServletRequest request) {
        try {
            URL url = new URL(request.getRequestURL().toString());
            return url.getHost() + ":" + url.getPort();
        } catch (IOException ex) {
            throw new IllegalStateException("Can't resolve host", ex);
        }
    }


}
