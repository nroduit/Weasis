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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.Document;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.startlevel.BundleStartLevel;
import org.osgi.service.prefs.Preferences;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.Messages;
import org.weasis.core.api.service.BundlePreferences;
import org.weasis.core.api.service.LicensedPluginsService;
import org.weasis.core.ui.util.LicenseBootURLProvider;
import org.weasis.core.util.StringUtil;

/**
 * Default behavior for a license controller.
 */
public class LicenseDialogController implements LicenseController {

  private static final Logger LOGGER = LoggerFactory.getLogger(LicenseDialogController.class);

  private ExecutorService executor = Executors.newFixedThreadPool(5);
  private Future<Boolean> executionFuture;
  
  private static final String PLUGINS_PACKAGE_VERSION_PREFERENCE = "plugins.package.version";
  private static final String LICENSE_SEVER_PREFERENCE = "license.sever";

  private static final int SUCCESS_HTTP_STATUS_CODE = 200;

  static final String CANCEL_COMMAND = "cancel"; // NON-NLS
  static final String OK_COMMAND = "ok"; // NON-NLS
  static final String TEST_COMMAND = "test";
  private final File licenceFile;

  private final AbstractTabLicense licensesItem;
  private final Document codeDocument;
  
  private LicensedPluginsService service;

  private Bundle bundle;

  private boolean tested;

  private Preferences preferences;

  private boolean executing = false;

  private boolean canceled;

  private TaskMonitor taskMonitor;

  private LicenseBootURLProvider bootUrlProvider;

  private String bootUrl;

  public LicenseDialogController(
      Document codeDocument, LicenseBootURLProvider bootUrlProvider, AbstractTabLicense licensesItem) {
    this.licensesItem = licensesItem;
    this.codeDocument = codeDocument;
    this.bootUrlProvider = bootUrlProvider;
    this.licenceFile =
        new File(
            AppProperties.WEASIS_PATH
                + File.separator
                + licensesItem.getLicenseName()
                + ".license"); // NON-NLS
    readLicenseContents();
  }

  private void readLicenseContents() {
    try {
      if (licenceFile.exists()) {
        String licenseContents = Files.readString(licenceFile.toPath());
        codeDocument.insertString(0, licenseContents, null);
      }
      Preferences prefs = getPreferences();
      if (prefs != null) {
        String prefsBootUrl = prefs.get(licensesItem.getPluginName() + "." + LICENSE_SEVER_PREFERENCE, null);
        if (StringUtil.hasText(prefsBootUrl)) {
          this.bootUrl = prefsBootUrl;
        }
        String version = prefs.get(licensesItem.getPluginName() + "." + PLUGINS_PACKAGE_VERSION_PREFERENCE, null);
        licensesItem.setVersionContents(StringUtil.hasText(version) ? version : "");
      }
    } catch (Exception e) {
      logErrorMessage(e, null);
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
    taskMonitor = new TaskMonitor(licensesItem);
    execute(controller -> {
      try {
        String contents = codeDocument.getText(0, codeDocument.getLength());
        if (StringUtil.hasText(contents.trim())) {
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
            JOptionPane.showMessageDialog(licensesItem, Messages.getString("license.successfully.saved"),
                Messages.getString("license"), JOptionPane.INFORMATION_MESSAGE);
            askUserToRestart();
            return true;
          }
        } else {
          JOptionPane.showMessageDialog(licensesItem, Messages.getString("license.field.empty"),
              Messages.getString("license"), JOptionPane.WARNING_MESSAGE);
        }
      } catch (Exception e) {
        logErrorMessage(e, null);
        String details = (e.getMessage() != null ? e.getMessage() : "");
        showErrorMessage(Messages.getString("license"), Messages.getString("license.error.saving") + " " + details);
      }
      return false;
    });
  }

  private void askUserToRestart() throws Exception {
    service.askUserForRestart(licensesItem);
  }

  private void updateUI() {
    taskMonitor.processItem("Updating user interface...");
    String version = service.getVersion();
    licensesItem.setVersionContents(StringUtil.hasText(version) ? version : "");
    try {
      String serverContents = this.bootUrl;
      Preferences prefs = getPreferences();
      LOGGER.debug("Trying to store preferences at: {}", prefs);
      if (prefs != null) {
        BundlePreferences.putStringPreferences(prefs, licensesItem.getPluginName() + "." + LICENSE_SEVER_PREFERENCE, serverContents);
        BundlePreferences.putStringPreferences(prefs, licensesItem.getPluginName() + "." + PLUGINS_PACKAGE_VERSION_PREFERENCE, version);
      }
    } catch (Exception e) {
      logErrorMessage(e, "Unable to update UI and save license related values: {}");
    }
  }

  private Preferences getPreferences() {
    if (preferences == null) {
      Bundle bundle = FrameworkUtil.getBundle(this.getClass());
      BundleContext context = null;
      if (bundle != null) {
          context = bundle.getBundleContext();
      }
      preferences = BundlePreferences.getDefaultPreferences(context);
    }
    return preferences;
  }

  private void changeConfigProperties() throws Exception {
    if (service != null) {
      taskMonitor.processItem("Updating local user configuration...");
      service.updateConfig();
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
    if (!executing) { // in case call is NOT from save button, we need to create a new thread based processing
      taskMonitor = new TaskMonitor(licensesItem);
      execute(controller -> {
        return executeTest();
      });
      executor.submit(new LicenseTesterWaitTask(executionFuture));
      return true;
    } else {
      return executeTest();
    }
  }

  @Override
  public void close() {
    if (bundle != null) {
      try {
        LOGGER.debug("Stopping and uninstalling bundle: {}", bundle);
        bundle.stop();
        bundle.uninstall();
      } catch (Exception e2) {
        logErrorMessage(e2, null);
      }
    }
  }

  private boolean executeTest() {
    String errorDetails = "";
    try {
      String licenseContents = codeDocument.getText(0, codeDocument.getLength());
      defineBootUrl();
      String serverContents = this.bootUrl;
      if (StringUtil.hasText(serverContents) && StringUtil.hasText(licenseContents)) {
        boolean download = downloadBootJarAndTestBundleAccess(licenseContents, serverContents);
        if (!download) {
          showErrorMessage(Messages.getString("license"), Messages.getString("license.error.downloading"));
        }
        tested = download;
        return tested;
      }
    } catch (Exception e) {
      logErrorMessage(e, null);
      errorDetails = e.getMessage() != null ? e.getMessage() : "";
      if (bundle != null) {
        try {
          bundle.stop();
          bundle.uninstall();
        } catch (Exception e2) {
          logErrorMessage(e2, null);
        }
      }
    }
    showErrorMessage(Messages.getString("license"), Messages.getString("license.error.testing") + " " + errorDetails);
    return false;
  }

  private void defineBootUrl() {
    if (this.bootUrl == null) {
      List<URI> bootURLs = bootUrlProvider.getBootURLs();
      if (bootURLs != null) {
        for (URI uri : bootURLs) {
          String uriStr = uri.toString();
          if (StringUtil.hasText(uriStr)) {
            if (pingLicenseServerURL(uriStr)) {
              this.bootUrl = uriStr;
              break;
            }
          }
        }
      }
    }
  }

  private boolean downloadBootJarAndTestBundleAccess(String licenseContents, String serverContents) throws Exception{
    taskMonitor.processItem("Downloading boot bundle jar...");
    boolean result = false;
    if (StringUtil.hasText(serverContents)) {
      try (HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(1)).build();) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(serverContents)).GET().build();
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
          result = bootUrlProvider.validateSignedBootJar(f);
          if (!result) {
            throw new IOException("Error validating security of boot JAR file!");
          }
          result = installAndStartBundle(new File(f.getAbsolutePath()), licenseContents);
        } else {
          LOGGER.error("Error getting plugins boot bundle. Server returned: {}", status);
          result = false;
        }
      } catch (IOException | InterruptedException e) {
        logErrorMessage(e, null);
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
    taskMonitor.processItem("Installing boot bundle jar...");
    BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
    LOGGER.debug("Bundle context: {}", context);
    LOGGER.debug("Bundle: {}", bundle);
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
      taskMonitor.processItem("Validating extensions endpoints...");
      service.validateEndpoints();
      taskMonitor.processItem("Validating license...");
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
    taskMonitor.processItem("Reaching out boot bundle jar...");
    try (HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofMinutes(1)).build();) {
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
        logErrorMessage(e, null);
    }
    return false;
  }

  @Override
  public void cancel() {
    canceled = true;
    executionFuture.cancel(true);
    enable();
  }

  private void generateBackupFile() {
    try {
      Files.copy(licenceFile.toPath(), Paths.get(licenceFile.getPath() + "-backup"),
          StandardCopyOption.REPLACE_EXISTING);
    } catch (IOException e) {
      logErrorMessage(e, null);
    }
  }

  private void writeFileContents(String contents) {
    taskMonitor.processItem("Writing license contents...");
    try (FileWriter fw = new FileWriter(licenceFile, StandardCharsets.UTF_8, false)) {
      fw.write(contents);
    } catch (IOException e) {
      logErrorMessage(e, null);
    }
  }

  private void execute(Function<LicenseDialogController, Boolean> f) {
    canceled = false;
    executionFuture = executor.submit(new ExecutionTask(this, f));
  }

  private void enable() {
    licensesItem.endProcessing();
  }

  private void disable() {
    licensesItem.startProcessing();
  }

  private void showErrorMessage(String title, String message) {
    if (!canceled) {
      if (SwingUtilities.isEventDispatchThread()) {
        JOptionPane.showMessageDialog(licensesItem, message, title, JOptionPane.ERROR_MESSAGE);
      } else {
        SwingUtilities.invokeLater(() -> {
          JOptionPane.showMessageDialog(licensesItem, message, title, JOptionPane.ERROR_MESSAGE);
        });
      }
    }
  }

  private void logErrorMessage(Throwable t, String message) {
    if (canceled) {
      LOGGER.warn("License processing canceled!");
      if (message != null) {
        LOGGER.warn(message, t.getMessage());
      } else {
        LOGGER.warn(t.getMessage());
      }
    } else {
      if (message != null) {
        LOGGER.error(message, t.getMessage());
      }
      LOGGER.error(t.getMessage(), t);
    }
  }

  private static final class ExecutionTask implements Callable<Boolean> {

    private LicenseDialogController controller;
    private Function<LicenseDialogController, Boolean> function;

    ExecutionTask(LicenseDialogController controller, Function<LicenseDialogController, Boolean> f) {
      this.controller = controller;
      this.function = f;
    }

    @Override
    public Boolean call() throws Exception {
      try {
        controller.executing = true;
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            controller.disable();
          }
        });
        return function.apply(controller);
      } finally {
        controller.taskMonitor.processItem("License handling process finished!");
        SwingUtilities.invokeLater(new Runnable() {
          @Override
          public void run() {
            controller.enable();
          }
        });
        controller.executing = false;
      }
    }
  }

  private static final class LicenseTesterWaitTask implements Callable<Boolean> {

    private Future<Boolean> future;

    public LicenseTesterWaitTask(Future<Boolean> future) {
      this.future = future;
    }

    @Override
    public Boolean call() throws Exception {
      boolean result = true;
      LOGGER.debug("Executing test in a new thread");
      try {
        if (!future.get()) {
          LOGGER.warn("License processing test failed!");
          result = false;
        }
      } catch (Exception e) {
        LOGGER.error(e.getMessage(), e);
        result = false;
      }
      return result;
    }

  }

  private static final class TaskMonitor {

    private AbstractTabLicense liensesItem;

    public TaskMonitor(AbstractTabLicense licensesItem) {
      this.liensesItem = licensesItem;
    }

    public void processItem(String message) {
      liensesItem.logProgress(message);
    }

  }

}
