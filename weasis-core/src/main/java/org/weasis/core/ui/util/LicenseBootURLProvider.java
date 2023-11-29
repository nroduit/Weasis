package org.weasis.core.ui.util;

import java.io.File;
import java.net.URI;
import java.util.List;

public interface LicenseBootURLProvider {

  public List<URI> getBootURLs();

  public boolean validateSignedBootJar(File signedBootJar);
}
