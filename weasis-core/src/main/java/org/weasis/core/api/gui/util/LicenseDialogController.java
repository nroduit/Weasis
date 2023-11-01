/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.gui.util;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.HttpResponse.ResponseInfo;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.stream.Stream;

import javax.swing.JOptionPane;
import javax.swing.text.Document;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.service.LicensedPluginsService;
import org.weasis.core.util.StringUtil;

import com.formdev.flatlaf.util.StringUtils;

/**
 * Default behavior for a license controller.
 */
public class LicenseDialogController implements LicenseController {

  public enum STATUS {
    START_PROCESSING,
    END_PROCESSING
  }

  private static final Logger LOGGER = LoggerFactory.getLogger(LicenseDialogController.class);

  private static final int SUCCESS_HTTP_STATUS_CODE = 200;

  static final String CANCEL_COMMAND = "cancel"; // NON-NLS
  static final String OK_COMMAND = "ok"; // NON-NLS
  static final String TEST_COMMAND = "test";
  private final File licenceFile;

  private final AbstractTabLicense licencesItem;
  private final Document codeDocument;
  private final Document serverDocument;
  private final Consumer<STATUS> statusSetter;

  private LicensedPluginsService service;

  private Bundle bundle;

  private boolean tested;

  public LicenseDialogController(
      Document codeDocument, Document serverDocument, AbstractTabLicense licencesItem, Consumer<STATUS> statusSetter) {
    this.licencesItem = licencesItem;
    this.codeDocument = codeDocument;
    this.serverDocument = serverDocument;
    this.statusSetter = statusSetter;
    this.licenceFile =
        new File(
            AppProperties.WEASIS_PATH
                + File.separator
                + licencesItem.getLicenseName()
                + ".license"); // NON-NLS
    readLicenseContents();
  }

  private void readLicenseContents() {
    if (licenceFile.exists()) {
      try {
        String licenseContents = Files.readString(licenceFile.toPath());
        codeDocument.insertString(0, licenseContents, null);
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
      }
    }
  }

  /**
   * Confirms changes at the user's local installation/PC, by executing the following
   * steps:
   * <ol>
   * <li>Executes the same as the {@link #testButton} button.</li>
   * <li>Save activation code.</li>
   * <li>Changes config.properties to add new information about new plugins.</li>
   * <li>Update UI reading new icon and messages from the remote boot OSGI jar file. This 
   * jar will be found using the server license field URL.</li>
   * <li>Ask user to re-start Weasis.</li>
   * </ol>
   */
  @Override
  public void save() {
    execute(controller -> {
      try {
        String contents = codeDocument.getText(0, codeDocument.getLength());
        if (!StringUtils.isEmpty(contents.trim())) {
          if (test()) {
            if (licenceFile.exists()) {
              int option = JOptionPane.showConfirmDialog(null, Messages.getString("license.file.exists"),
                  Messages.getString("license"), JOptionPane.YES_NO_OPTION);
              if (option == JOptionPane.YES_OPTION) {
                generateBackupFile();
                writeFileContents(contents);
              }
            } else {
              writeFileContents(contents);
            }
            changeConfigProperties();
            updateUI();
            askUserToRestart();
            JOptionPane.showMessageDialog(licencesItem, Messages.getString("license.successfully.saved"),
                Messages.getString("license"), JOptionPane.INFORMATION_MESSAGE);
          }
        } else {
          JOptionPane.showMessageDialog(licencesItem, Messages.getString("license.field.empty"),
              Messages.getString("license"), JOptionPane.WARNING_MESSAGE);
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        String details = (e.getMessage() != null ? e.getMessage() : "");
        JOptionPane.showMessageDialog(licencesItem, Messages.getString("license.error.saving") + " " + details,
            Messages.getString("license"), JOptionPane.ERROR_MESSAGE);
      }
    });
  }

  private void askUserToRestart() {
  }

  private void updateUI() {
  }

  private void changeConfigProperties() throws Exception {
    if (service != null) {
      service.updateConfig();
      String version = service.getVersion();
      licencesItem.setVersionContents(!StringUtils.isEmpty(version) ? version : "");
    }
  }

  /**
   * Do not changes anything at user's local PC/installation. Executes the following steps
   * just to validate license/activation code.
   * <ol>
   * <li>Ping URL at the server license field. The ping needs to return HTTP 200 code in order to move
   * to next step.</li>
   * <li>Download the OSGi boot jar file from server license URL, installs and activates it.</li>
   * <li>Executes a check running a check test method inside the bundle, just to guarantee that the interfaces match.
   * This implementation implements a check, by: (1) validating each end point need by extensions - 
   * {@link LicensedPluginsService#validateEndpoints()}; (2) validating the license - 
   * {@link LicensedPluginsService#validateLicense(String)}</li>
   * </ol>
   * @see LicenseDialogController#installAndStartBundle(File, String)
   */
  @Override
  public boolean test() {
    if (tested) return tested;
    String errorDetails = "";
    try {
      String licenseContents = codeDocument.getText(0, codeDocument.getLength());
      String serverContents = serverDocument.getText(0, serverDocument.getLength());
      if (StringUtil.hasText(serverContents) && StringUtil.hasText(licenseContents)) {
        if (pingLicenseServerURL(serverContents)) {
          boolean download = downloadBootJarAndTestBundleAccess(licenseContents, serverContents);
          if (!download) {
            JOptionPane.showMessageDialog(licencesItem, Messages.getString("license.error.downloading"),
                Messages.getString("license"), JOptionPane.ERROR_MESSAGE);
          }
          tested = download;
          return tested;
        }
      }
    } catch (Exception e) {
      LOGGER.error(e.getMessage(), e);
      errorDetails = e.getMessage() != null ? e.getMessage() : "";
      if (bundle != null) {
        try {
          bundle.stop();
          bundle.uninstall();
        } catch (Exception e2) {
          LOGGER.error(e2.getMessage(), e2);
        }
      }
    }
    JOptionPane.showMessageDialog(licencesItem, Messages.getString("license.error.testing") + " " + errorDetails,
        Messages.getString("license"), JOptionPane.ERROR_MESSAGE);
    return false;

  }

  private boolean downloadBootJarAndTestBundleAccess(String licenseContents, String serverContents) throws Exception{
    boolean result = false;
    if (StringUtil.hasText(serverContents)) {
      HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(1)).build();
      HttpRequest request = HttpRequest.newBuilder(URI.create(serverContents)).GET().build();
      try {
        HttpResponse<InputStream> resp = httpClient.send(request, responseInfo -> {
          LOGGER.info("Validating URL: {}. Result: {}", serverContents, responseInfo.statusCode());
          return BodySubscribers.ofInputStream();
        });
        int status = resp.statusCode();
        if (status == SUCCESS_HTTP_STATUS_CODE) {
          InputStream is = resp.body();
          File f = Files.createTempFile("plugins-boot", ".jar").toFile();
          f.deleteOnExit();
          LOGGER.info("Trying to copy boot jar contents to: {}", f.getAbsolutePath());
          try (BufferedInputStream bis = new BufferedInputStream(is)) {
            Files.copy(bis, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
          }
          LOGGER.info("Boot jar contents successfully copied to file: {}", f.getAbsolutePath());
          result = installAndStartBundle(new File(f.getAbsolutePath()), licenseContents);
        } else {
          LOGGER.error("Error getting plugins boot bundle. Server returned: {}", status);
          result = false;
        }
      } catch (IOException | InterruptedException e) {
        LOGGER.error(e.getMessage(), e);
      }
      return result;
    }
    return result;
  }

  /**
   * Install bundle and activate services on it. After that, locate service
   * implementing interface {@link LicensedPluginsService} and call methods
   * <code>validateEndpoints </code>and <code>validateLicense</code>.
   * 
   * @param file the local file with bundle contents.
   * @param licenseContents the licenseContents to be validated
   * @return <code>true</code> if the whole cycle of installing, activating and validating is completed with success.
   * <code>False</code>, otherwise.
   */
  private boolean installAndStartBundle(File file, String licenseContents) throws Exception {
    BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    LOGGER.debug("Bundle context: {}", context);
    bundle = context.installBundle(file.toURI().toURL().toString());
    LOGGER.debug("New bundle: {}", bundle);
    BundleStartLevel bundleStartLevel = bundle.adapt(BundleStartLevel.class);
    int lastStartLevel = getLastStartLevel(context);
    bundleStartLevel.setStartLevel(lastStartLevel + 1);
    bundle.start(Bundle.START_ACTIVATION_POLICY);
    LOGGER.debug("Bundle status: {}", bundle.getState());
    ServiceReference<LicensedPluginsService> serviceRef = bundle.getBundleContext().getServiceReference(LicensedPluginsService.class);
    service = bundle.getBundleContext().getService(serviceRef);
    if (service != null) {
      service.validateEndpoints();
      service.validateLicense(licenseContents);
    }
    return true;
  }

  private int getLastStartLevel(BundleContext context) {
    List<Bundle> bundles = Arrays.asList(context.getBundles());
    Integer result = Integer.MIN_VALUE;
    try (Stream<Bundle> s = bundles.stream()) {
      result = s.reduce(result, (r, b) -> {
        BundleStartLevel bundleStartLevel = b.adapt(BundleStartLevel.class);
        int startLevel = bundleStartLevel.getStartLevel();
        if (startLevel > r && startLevel < 200) {
          return startLevel;
        } else {
          return r;
        }
      }, new BinaryOperator<Integer>() {
        @Override
        public Integer apply(Integer t, Integer u) {
          if (t > u) return t;
          else return u;
        }
      });
    };
    LOGGER.debug("Last start level : {}", result);
    return result;
  }

  private boolean pingLicenseServerURL(String serverContents) {
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(1)).build();
    try {
        HttpRequest request = HttpRequest.newBuilder(URI.create(serverContents)).GET().build();
        HttpResponse<Void> resp = httpClient.send(request, new BodyHandler<Void>() {
          @Override
          public BodySubscriber<Void> apply(final ResponseInfo responseInfo) {
              return BodySubscribers.discarding();
          }
        });
        int status = resp.statusCode();
        LOGGER.info("Validating URL: {}. Result: {}", serverContents, status);
        if (status == SUCCESS_HTTP_STATUS_CODE) {
            return true;
        }
    } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
    }
    return false;
  }

  @Override
  public void cancel() {
  }

  private void generateBackupFile() {
    try {
      Files.copy(licenceFile.toPath(), Paths.get(licenceFile.getPath() + "-backup"),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private void writeFileContents(String contents) {
    try (FileWriter fw = new FileWriter(licenceFile, StandardCharsets.UTF_8, false)) {
      fw.write(contents);
    } catch (IOException e) {
      LOGGER.error(e.getMessage(), e);
    }
  }

  private void execute(Consumer<LicenseDialogController> c) {
    try {
      statusSetter.accept(STATUS.START_PROCESSING);
      disable();
      c.accept(this);
    } finally {
      statusSetter.accept(STATUS.END_PROCESSING);
      enable();
    }
  }

  private void enable() {
    licencesItem.getSaveButton().setEnabled(true);
    licencesItem.getTestButton().setEnabled(true);
  }

  private void disable() {
    licencesItem.getSaveButton().setEnabled(false);
    licencesItem.getTestButton().setEnabled(false);
  }

}
