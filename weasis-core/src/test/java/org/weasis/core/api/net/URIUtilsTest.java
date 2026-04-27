/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

class URIUtilsTest {

  // ---------------------------------------------------------------------------
  // getURI – general behaviour
  // ---------------------------------------------------------------------------

  @Test
  void getURI_returnsURIForValidInput() throws URISyntaxException {
    URI uri = URIUtils.getURI("http://example.com/view?query=123");
    Assertions.assertEquals("http", uri.getScheme());
    Assertions.assertEquals("example.com", uri.getHost());

    Assertions.assertTrue(URIUtils.isHttpURI(uri));
    Assertions.assertNull(URIUtils.getAbsolutePath(uri));

    Assertions.assertThrows(URISyntaxException.class, () -> URIUtils.getURI("/path/to\0file.txt"));
  }

  @Test
  void getURI_throwsNullPointerException_forNullInput() {
    Assertions.assertThrows(NullPointerException.class, () -> URIUtils.getURI(null));
  }

  // ---------------------------------------------------------------------------
  // Protocol helpers
  // ---------------------------------------------------------------------------

  @Test
  void isHttpURI_returnsTrueForHttpsScheme() throws URISyntaxException {
    URI uri = new URI("https://example.com");
    Assertions.assertTrue(URIUtils.isHttpURI(uri));
    Assertions.assertTrue(URIUtils.isProtocol(uri, "https"));
    Assertions.assertFalse(URIUtils.isFileURI(uri));
  }

  @Test
  void ftpURI() throws URISyntaxException {
    URI uri = new URI("ftp://example.com");
    Assertions.assertFalse(URIUtils.isHttpURI(uri));
    Assertions.assertTrue(URIUtils.isProtocol(uri, "ftp"));
  }

  @Test
  void isProtocol_throwsIllegalArgumentException_forNullProtocol() throws URISyntaxException {
    URI uri = new URI("http://example.com");
    Assertions.assertThrows(IllegalArgumentException.class, () -> URIUtils.isProtocol(uri, null));
  }

  @Test
  void isProtocol_throwsIllegalArgumentException_forEmptyProtocol() throws URISyntaxException {
    URI uri = new URI("http://example.com");
    Assertions.assertThrows(IllegalArgumentException.class, () -> URIUtils.isProtocol(uri, ""));
  }

  @Test
  void isProtocol_throwsIllegalArgumentException_forNullURI() {
    Assertions.assertThrows(
        IllegalArgumentException.class, () -> URIUtils.isProtocol(null, "http"));
  }

  // ---------------------------------------------------------------------------
  // File URIs – cross-platform
  // ---------------------------------------------------------------------------

  @Test
  void fileURI_commonCases() throws URISyntaxException {
    URI uri = URIUtils.getURI("file:///path/to/file");
    Assertions.assertTrue(URIUtils.isFileURI(uri));
    Assertions.assertEquals("/path/to/file", URIUtils.getAbsolutePath(uri).toString());

    uri = URIUtils.getURI("file:/path/to/file");
    Assertions.assertTrue(URIUtils.isFileURI(uri));
    Assertions.assertEquals("/path/to/file", URIUtils.getAbsolutePath(uri).toString());

    uri = URIUtils.getURI("");
    Assertions.assertTrue(URIUtils.isFileURI(uri));
  }

  @Test
  void getAbsolutePath_returnsNull_forNonFileURI() throws URISyntaxException {
    URI uri = new URI("http://example.com/resource");
    Assertions.assertNull(URIUtils.getAbsolutePath(uri));
  }

  @Test
  void getAbsolutePath_throwsNullPointerException_forNullURI() {
    Assertions.assertThrows(NullPointerException.class, () -> URIUtils.getAbsolutePath(null));
  }

  @Test
  void toPath_throwsNullPointerException_forNullURI() {
    Assertions.assertThrows(NullPointerException.class, () -> URIUtils.toPath(null));
  }

  // ---------------------------------------------------------------------------
  // Platform-specific: Unix / macOS
  // ---------------------------------------------------------------------------

  @Nested
  @EnabledOnOs({OS.LINUX, OS.MAC})
  class UnixPathTests {

    @Test
    void absoluteUnixPath() throws URISyntaxException {
      URI uri = URIUtils.getURI("/path/to/file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Assertions.assertEquals("/path/to/file.txt", URIUtils.getAbsolutePath(uri).toString());
    }

    @Test
    void relativeUnixPath() throws URISyntaxException {
      URI uri = URIUtils.getURI("path/to/file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Assertions.assertEquals("path/to/file.txt", uri.getPath());
      Assertions.assertEquals(
          Paths.get("path/to/file.txt").toAbsolutePath().toString(),
          URIUtils.getAbsolutePath(uri).toString());
    }

    @Test
    void fileSchemeUnixPath() throws URISyntaxException {
      URI uri = URIUtils.getURI("file:///path/to/file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));

      Path path = URIUtils.toPath(uri);
      Assertions.assertEquals("/path/to/file.txt", path.toString());

      File file = URIUtils.toFile(uri);
      Assertions.assertEquals("/path/to/file.txt", file.getPath());
    }

    @Test
    void pathWithSpaces() throws URISyntaxException {
      URI uri = URIUtils.getURI("file:///path/to/my%20file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Path path = URIUtils.toPath(uri);
      Assertions.assertEquals("/path/to/my file.txt", path.toString());
    }

    @Test
    void deeplyNestedUnixPath() throws URISyntaxException {
      URI uri = URIUtils.getURI("file:///a/b/c/d/e/f/g.dcm");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Assertions.assertEquals("/a/b/c/d/e/f/g.dcm", URIUtils.toPath(uri).toString());
    }
  }

  // ---------------------------------------------------------------------------
  // Platform-specific: Windows
  // ---------------------------------------------------------------------------

  @Nested
  @EnabledOnOs(OS.WINDOWS)
  class WindowsPathTests {

    @Test
    void windowsDriveLetterWithBackslashes() throws URISyntaxException {
      URI uri = URIUtils.getURI("C:\\path\\to\\file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Assertions.assertEquals("C:\\path\\to\\file.txt", URIUtils.getAbsolutePath(uri).toString());
    }

    @Test
    void windowsDriveLetterWithForwardSlashes() throws URISyntaxException {
      URI uri = URIUtils.getURI("C:/path/to/file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Assertions.assertEquals("C:\\path\\to\\file.txt", URIUtils.getAbsolutePath(uri).toString());
    }

    @Test
    void windowsFileURI() throws URISyntaxException {
      URI uri = URIUtils.getURI("file:///C:/path/to/file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Assertions.assertEquals("C:\\path\\to\\file.txt", URIUtils.getAbsolutePath(uri).toString());

      Path path = URIUtils.toPath(uri);
      Assertions.assertEquals("C:\\path\\to\\file.txt", path.toString());

      File file = URIUtils.toFile(uri);
      Assertions.assertEquals("C:\\path\\to\\file.txt", file.getPath());
    }

    @Test
    void windowsUNCPath() throws URISyntaxException {
      // UNC paths: file://server/share/path/to/file
      URI uri = new URI("file://server/share/path/to/file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Path path = URIUtils.toPath(uri);
      Assertions.assertEquals("\\\\server\\share\\path\\to\\file.txt", path.toString());
    }

    @Test
    void windowsWSLPath() throws URISyntaxException {
      // WSL path: file://wsl.localhost/Ubuntu/home/user/file.txt
      URI uri = new URI("file://wsl.localhost/Ubuntu/home/user/file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Path path = URIUtils.toPath(uri);
      Assertions.assertEquals("\\\\wsl.localhost\\Ubuntu\\home\\user\\file.txt", path.toString());
    }

    @Test
    void windowsPathWithSpaces() throws URISyntaxException {
      URI uri = URIUtils.getURI("file:///C:/path/to/my%20file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Path path = URIUtils.toPath(uri);
      Assertions.assertEquals("C:\\path\\to\\my file.txt", path.toString());
    }

    @Test
    void windowsRelativePath() throws URISyntaxException {
      URI uri = URIUtils.getURI("path\\to\\file.txt");
      Assertions.assertTrue(URIUtils.isFileURI(uri));
      Assertions.assertEquals(
          Paths.get("path\\to\\file.txt").toAbsolutePath().toString(),
          URIUtils.getAbsolutePath(uri).toString());
    }
  }
}
