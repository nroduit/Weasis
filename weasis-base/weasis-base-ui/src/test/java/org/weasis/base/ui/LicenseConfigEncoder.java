package org.weasis.base.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.OptionalInt;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 * Test class used to encode license configuration properties files. Remove the
 * <code>@Disabled</code> annotation, set the path for source and target file,
 * and run the test. It will use a key file stored in
 * <code>${user.home}/plugin-providers/config-key.properties</code>. After
 * generating the target file, copy its contents to a GitHub secrets. Also, copy
 * the contents of <code>config-key.properties</code> file to GitHub secrets. In
 * production environment, during the installer generation, these files should
 * be automatically generated copying information from GitHub secrets.
 */
@Disabled
class LicenseConfigEncoder {

  private static final String keyFilePath = "plugin-providers" + File.separator + "config-key.properties";
  private static final String keyFile = System.getProperty("user.home") + File.separator + keyFilePath;
  private static String key;

  /**
   * Change according with the needs. Just like
   * <code>mysourceurlsfile-source.properties</code>
   */
  private static final String sourceFile = System.getProperty("user.home") + File.separator + "plugin-providers"
      + File.separator + "";

  /**
   * Change according with the needs. Just like
   * <code>mysourceurlsfile.properties</code>
   */
  private static final String targetFile = System.getProperty("user.home") + File.separator + "plugin-providers"
      + File.separator + "";

  @BeforeAll
  static void beforeAll() {
    Properties p = new Properties();
    try (FileInputStream fis = new FileInputStream(new File(keyFile))) {
      p.load(fis);
      key = p.getProperty("key");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  void testEncode() {
    // read the source
    Properties p = new Properties();
    try (FileInputStream fis = new FileInputStream(new File(sourceFile))) {
      p.load(fis);
    } catch (Exception e) {
      e.printStackTrace();
      return;
    }

    // encode
    Set<Map.Entry<Object, Object>> entries = p.entrySet();
    for (Iterator<Map.Entry<Object, Object>> iterator = entries.iterator(); iterator.hasNext();) {
      Entry<Object, Object> entry = iterator.next();
      String value = (String) entry.getValue();
      String encodedValue = encode(key, value);
      p.put(entry.getKey(), encodedValue);
    }

    // write result
    try (FileOutputStream fos = new FileOutputStream(new File(targetFile))) {
      p.store(fos, "ENCODED LICENSE CONFIG FILE");
    } catch (Exception e) {
      e.printStackTrace();
    }

  }

  private String encode(String key, String value) {
    Random r = new Random(key.hashCode());
    long salt = r.nextLong();
    OptionalInt intSalt;
    try (IntStream inStream = Long.valueOf(salt).toString().chars()) {
      intSalt = inStream.reduce((left, right) -> {
        return left + right;
      });
    }
    StringBuilder switchedString = new StringBuilder();
    for (int i = 0; i < value.length(); ++i)
      switchedString.append((char) (value.charAt(i) + (1 * intSalt.getAsInt())));
    return switchedString.toString();
  }



}
