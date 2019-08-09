package org.weasis.launcher;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;

public class CustomFileHandler extends FileHandler {
    static {
        new File(System.getProperty("user.home", "") + File.separator + ".weasis" + File.separator + "log").mkdirs();
    }
    
    public CustomFileHandler() throws IOException {
        super("%h/.weasis/log/boot-%u.log");
    }
}