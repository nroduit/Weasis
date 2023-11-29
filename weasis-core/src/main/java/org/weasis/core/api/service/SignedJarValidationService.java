package org.weasis.core.api.service;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ServiceScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = SignedJarValidationService.class, scope = ServiceScope.SINGLETON)
public class SignedJarValidationService {

  private static final Logger LOGGER = LoggerFactory.getLogger(SignedJarValidationService.class);

  private Object targetCertificate;

  public boolean validateSignedBootJar(File signedBootJar, String targetCertificateCompanyName) throws Exception {
    LOGGER.trace("Validating signed jar...");
    boolean result = false;

    // Find jar certificates
    byte[] certificateSigned = getCertificatesStore(signedBootJar);
    if (certificateSigned == null || certificateSigned.length == 0) {
      throw new SecurityException("Jar is not signed!");
    }
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    Collection<?> internalCerts = null;
    try (ByteArrayInputStream inStream = new ByteArrayInputStream(certificateSigned)) {
      internalCerts = cf.generateCertificates(inStream);
      LOGGER.trace("internalCerts: {}", internalCerts.size());
    }
    targetCertificate = null;
    for (Object object : internalCerts) {
      X509Certificate certificate = (X509Certificate) object;
      String name = certificate.getSubjectX500Principal().getName();
      LOGGER.trace(name);
      if (name.toLowerCase().contains(targetCertificateCompanyName.toLowerCase())) {
        targetCertificate = certificate;
      }
    }
    assert internalCerts != null;
    assert targetCertificate != null;

    // Verify jar file
    try (JarFile jf = new JarFile(signedBootJar, true)) {

      // Tests jarfile and manifest
      assert jf != null;
      Manifest m = jf.getManifest();
      assert m != null;

      // Tests each jar entry
      Enumeration<JarEntry> entries = jf.entries();
      while (entries.hasMoreElements()) {
        JarEntry je = entries.nextElement();
        LOGGER.trace("---> " + je.getName());

        // Skip directories.
        if (je.isDirectory())
          continue;

        // verify certificates
        try (InputStream is = jf.getInputStream(je)) {
          int n;
          byte[] buffer = new byte[8192];
          while ((n = is.read(buffer, 0, buffer.length)) != -1) {
            // Don't care
          }
        } catch (IOException e) {
          LOGGER.error(e.getMessage(), e);
          return result;
        }
        Certificate[] certs = je.getCertificates();
        if ((certs == null) || (certs.length == 0)) {
          if (!je.getName().startsWith("META-INF"))
            throw new SecurityException("The provider " + "has unsigned " + "class files.");
        }
        LOGGER.trace("certs: " + (certs != null ? certs.length : 0));

        // verify if entry is signed with the right certificate
        result = false;
        if (certs != null && certs.length > 0) {
          for (int i = 0; i < certs.length; i++) {
            X509Certificate certificate = (X509Certificate) certs[i];
            LOGGER.trace(certificate.getSubjectX500Principal().getName());
          }
          int startIndex = 0;
          X509Certificate[] certChain;
          while ((certChain = getAChain(certs, startIndex)) != null) {
            LOGGER.trace(certChain[0].getSubjectX500Principal().getName());
            if (certChain[0].equals(targetCertificate)) {
              // Stop since one trusted signer is found.
              result = true;
              break;
            }
            // Proceed to the next chain.
            startIndex += certChain.length;
          }
          if (!result) {
            break;
          }
        }
      }
      LOGGER.trace("Boot jar validation: {}", result);
      return result;
    }
  }

  private byte[] getCertificatesStore(File jarToTest) {
    byte[] result = null;
    try (JarFile jf = new JarFile(jarToTest, true)) {
      Enumeration<JarEntry> entries = jf.entries();
      while (entries.hasMoreElements()) {
        JarEntry je = entries.nextElement();
        if (je.getName().equals("META-INF/CERT.RSA")) {
          try (InputStream is = jf.getInputStream(je)) {
            result = is.readAllBytes();
          }
        }
      }
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
    return result;
  }

  private static X509Certificate[] getAChain(Certificate[] certs, int startIndex) {
    if (startIndex > certs.length - 1)
      return null;

    int i;
    // Keep going until the next certificate is not the
    // issuer of this certificate.
    for (i = startIndex; i < certs.length - 1; i++) {
      if (!((X509Certificate) certs[i + 1]).getSubjectX500Principal().getName()
          .equals(((X509Certificate) certs[i]).getIssuerX500Principal().getName())) {
        break;
      }
    }

    // Construct and return the found certificate chain.
    int certChainSize = (i - startIndex) + 1;
    X509Certificate[] ret = new X509Certificate[certChainSize];
    for (int j = 0; j < certChainSize; j++) {
      ret[j] = (X509Certificate) certs[startIndex + j];
    }
    return ret;
  }

}
