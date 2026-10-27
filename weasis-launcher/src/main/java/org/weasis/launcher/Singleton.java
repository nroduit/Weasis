/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.launcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.command.CommandError;
import org.weasis.core.api.command.CommandFailure;
import org.weasis.core.api.command.CommandManager;
import org.weasis.core.api.command.CommandResult;
import org.weasis.core.api.event.EventFilter;
import org.weasis.pref.ConfigData;

public class Singleton {
  private static final Logger LOGGER = LoggerFactory.getLogger(Singleton.class);

  private static final String LOCALHOST = "127.0.0.1";
  private static final Path SI_FILEDIR = SingletonFiles.directory();

  /** Longest wait a remote command may request, in seconds (system property). */
  public static final String MAX_TIMEOUT_PROP = "weasis.commands.remote.timeout.max";

  private static final int DEFAULT_TIMEOUT_SECONDS = 30;
  private static final String SI_MAGICWORD = "si.init";
  private static final String SI_ARG = "si.arg";
  private static final String SI_PROP = "si.prop";
  private static final String SI_ACK = "si.ack";
  private static final String SI_EXIT = "si.exit";
  private static final String SI_OTHER = "si.other";
  private static final String SI_STOP = "si.stop";
  private static final String SI_EOF = "si.EOF";
  private static final int ENCODING_PLATFORM = 1;
  private static final int ENCODING_UNICODE = 2;

  private static final String ENCODING_UNICODE_NAME = "UTF-16LE"; // NON-NLS
  private static final String ENCODING_UTF8_NAME = "UTF-8"; // NON-NLS

  private static int currPort;
  private static String stringId = null;
  private static String randomNumberString = null;

  private static SingletonServer siServer;
  private static SingletonApp siApp = null;

  private static final SecureRandom random = new SecureRandom();
  private static volatile boolean serverStarted = false;
  private static int randomNumber;

  public interface SingletonApp {

    boolean canStartNewActivation(Properties prop);

    void newActivation(List<String> arguments);

    /**
     * Whether an activation offered by the bootstrap, which knows the command line but not the
     * configuration, is addressed to this instance (same user, same configuration source). Another
     * instance is answered {@code si.other} and left alone.
     */
    default boolean isSameInstance(Properties prop) {
      return canStartNewActivation(prop);
    }

    /**
     * Whether the configuration of this instance changed since it started, re-read from its source;
     * an activation then ends this instance so a new one starts with the new configuration.
     */
    default boolean configurationChanged() {
      return false;
    }

    /**
     * Runs a command received on the socket ({@code si.run}); origin is always remote and {@code
     * assumeYes} answers yes to the confirmations the command would ask.
     */
    default CommandResult runCommand(List<String> arguments, boolean assumeYes) {
      return CommandResult.error("Remote commands are not supported by this application");
    }

    /**
     * Opens the event stream a remote caller asked for ({@code si.sub}); the grant decides what the
     * caller sees and is closed when the connection ends.
     *
     * @throws CommandFailure when the policy refuses
     */
    default CommandManager.EventStreamGrant openEventStream(EventFilter filter) {
      throw new CommandFailure(
          CommandError.of(CommandError.FAILED, "Events are not supported by this application"));
    }
  }

  public static void start(SingletonApp sia, String id) {
    Objects.requireNonNull(sia);
    Objects.requireNonNull(id);

    synchronized (Singleton.class) {
      if (!serverStarted) {
        try {
          siServer = new SingletonServer(id);
          siServer.start();
        } catch (Exception e) {
          LOGGER.error("Create a Singleton server", e);
          return;
        }
        siApp = sia;
        serverStarted = true;
      }
    }
  }

  public static void stop() {
    if (siApp == null) {
      LOGGER.warn("Singleton.stop() called when not running, id: {}", stringId);
      return;
    }

    synchronized (Singleton.class) {
      siApp = null;

      try (Socket socket = new Socket(LOCALHOST, siServer.getPort());
          OutputStream os = socket.getOutputStream();
          PrintStream out = new PrintStream(os, true, Charset.defaultCharset())) {
        byte[] encoding = new byte[1];
        encoding[0] = ENCODING_PLATFORM;
        os.write(encoding);
        out.println(randomNumber);
        out.println(Singleton.SI_STOP);
        out.flush();
        serverStarted = false;
      } catch (IOException e) {
        LOGGER.error("Stopping Singleton server", e);
      }

      siServer.runnable.removeSiFile();
    }
  }

  public static boolean running(String id) {
    LOGGER.debug("Check if another instance running for id: {}", id);
    Optional<SingletonFiles.Instance> instance = SingletonFiles.find(SI_FILEDIR, id);
    if (instance.isEmpty()) {
      return false;
    }
    currPort = instance.get().port();
    randomNumberString = instance.get().secret();
    stringId = id;
    LOGGER.info("Singleton server {} already running on port {}", stringId, currPort);
    return true;
  }

  public static boolean invoke(ConfigData configData) {
    return running(configData.getSourceID()) && connectToServer(configData);
  }

  private static void printProperty(PrintStream out, String key, Properties p) {
    out.printf("%s=%s%n", key, p.getProperty(key, "")); // NON-NLS
  }

  /** Returns true if we connect successfully to the server for the stringId */
  static boolean connectToServer(ConfigData configData) {
    LOGGER.info("Connect to {} on port {}", stringId, currPort);
    if (randomNumberString == null) {
      // should not happen
      LOGGER.error("MAGIC number is null, cannot connect.");
      return false;
    }

    String encodingName = Charset.defaultCharset().name();

    try (Socket socket = new Socket(LOCALHOST, currPort);
        OutputStream os = socket.getOutputStream();
        PrintStream out = new PrintStream(socket.getOutputStream(), true, encodingName);
        InputStreamReader isr = new InputStreamReader(socket.getInputStream(), encodingName);
        BufferedReader br = new BufferedReader(isr)) {
      byte[] encoding = new byte[1];
      encoding[0] = ENCODING_PLATFORM;
      os.write(encoding);

      // send random number
      out.println(randomNumberString);
      // send MAGICWORD
      out.println(SI_MAGICWORD);

      out.println(SI_ARG);
      for (String arg : configData.getArguments()) {
        out.println(arg);
      }

      out.println(SI_PROP);
      Properties p = configData.getProperties();
      printProperty(out, ConfigData.P_WEASIS_USER, p);
      printProperty(out, ConfigData.P_WEASIS_CONFIG_HASH, p);

      // indicate end of file transmission
      out.println(SI_EOF);
      out.flush();

      // wait for ACK (OK) response
      LOGGER.debug("Waiting for ACK");
      final int tries = 5;

      // try to listen for ACK
      for (int i = 0; i < tries; i++) {
        String str = br.readLine();
        if (SI_ACK.equals(str)) {
          LOGGER.debug("Got ACK");
          return true;
        } else if (SI_EXIT.equals(str)) {
          File file = getSiFile(stringId, currPort);
          waitDeletedFile(file);
          return false;
        }
      }
      LOGGER.error("No ACK from server");
    } catch (java.net.SocketException se) {
      LOGGER.error("No server is running", se);
    } catch (Exception ioe) {
      LOGGER.error("Cannot connect to server", ioe);
    }
    return false;
  }

  private static void waitDeletedFile(File file) {
    int loop = 0;
    boolean runLoop = true;
    while (runLoop) {
      LOGGER.info("Wait 100 ms to start a the new instance once the previous has stopped");
      try {
        if (!file.exists()) {
          break;
        }
        TimeUnit.MILLISECONDS.sleep(100);
        loop++;
        if (loop > 300) { // Let 30s max to set up the plugin framework
          LOGGER.error("The pid of the singleton still exists. Try to start the new instance.");
          runLoop = false;
        }
      } catch (InterruptedException e) {
        runLoop = false;
        Thread.currentThread().interrupt();
      }
    }
  }

  private static File getSiFile(final String id, final int port) {
    return SingletonFiles.file(SI_FILEDIR, id, port).toFile();
  }

  private static class SingletonServer {

    private final SingletonServerRunnable runnable;
    private final Thread thread;

    SingletonServer(SingletonServerRunnable runnable) throws Exception {
      thread = new Thread(null, runnable, "SIThread", 0, false);
      thread.setDaemon(true);
      this.runnable = runnable;
    }

    SingletonServer(String stringId) throws Exception {
      this(new SingletonServerRunnable(stringId));
    }

    int getPort() {
      return runnable.getPort();
    }

    void start() {
      thread.start();
    }
  }

  private static class SingletonServerRunnable implements Runnable {

    final ServerSocket ss;
    final int port;
    final String stringId;

    /** Serializes URI activations and stop requests in arrival order; si.run stays concurrent. */
    private final ReentrantLock activationLock = new ReentrantLock(true);

    int getPort() {
      return port;
    }

    SingletonServerRunnable(String id) throws IOException {
      stringId = id;
      // we should bind the server to the local InetAddress 127.0.0.1
      // port number is automatically allocated for current SI
      ss = new ServerSocket(0, 0, InetAddress.getByName(LOCALHOST));

      // get the port number
      port = ss.getLocalPort();
      LOGGER.info("Local port of the Singleton server: {}", port);

      // create the single instance file with canonical home and port num
      createSingletonFile(stringId, port);
    }

    private void removeSiFile() {
      File file = getSiFile(stringId, port);
      LOGGER.info("Removing SingletonFile: {}", file);
      FileUtil.delete(file.toPath());
    }

    private static void createSingletonFile(final String id, final int port) {
      try {
        randomNumber = random.nextInt();
        Path file = SingletonFiles.create(SI_FILEDIR, id, port, String.valueOf(randomNumber));
        file.toFile().deleteOnExit();
      } catch (IOException e) {
        LOGGER.error("Cannot create the singleton file", e);
      }
    }

    @Override
    public void run() {
      // handle all the incoming request from server port
      runSingletonServer();
    }

    private static String getStreamEncoding(InputStream is) throws IOException {
      // read first byte for encoding type
      int encoding = is.read();
      switch (encoding) {
        case ENCODING_PLATFORM -> {
          return Charset.defaultCharset().name();
        }
        case ENCODING_UNICODE -> {
          return ENCODING_UNICODE_NAME;
        }
        case CommandProtocol.ENCODING_UTF8 -> {
          return ENCODING_UTF8_NAME;
        }
        default -> {
          LOGGER.error("Unknown encoding: {}", encoding);
          return null;
        }
      }
    }

    // One virtual thread per connection: a slow remote command must not block URI forwarding.
    private Void runSingletonServer() {
      while (!Thread.currentThread().isInterrupted()) {
        try {
          Socket socket = ss.accept();
          Thread.ofVirtual()
              .name("si-client") // NON-NLS
              .uncaughtExceptionHandler(
                  (thread, error) -> LOGGER.error("Unhandled error in {}", thread.getName(), error))
              .start(() -> handleClient(socket));
        } catch (IOException e) {
          if (ss.isClosed()) {
            break;
          }
          LOGGER.error("Error accepting connection", e);
        }
      }
      return null;
    }

    private void handleClient(Socket socket) {
      try (InputStream is = socket.getInputStream();
          InputStreamReader isr = new InputStreamReader(is, getStreamEncoding(is));
          BufferedReader in = new BufferedReader(isr)) {

        LOGGER.debug("Singleton server is waiting for a connection");

        // First read the random number
        String line = in.readLine();
        if (line.equals(String.valueOf(randomNumber))) {
          line = in.readLine();
          LOGGER.debug("Receive message: {}", line);

          if (CommandProtocol.RUN.equals(line)) {
            handleRun(in, socket, isr);
          } else if (CommandProtocol.SUBSCRIBE.equals(line)) {
            handleSubscribe(in, socket, isr);
          } else if (SI_MAGICWORD.equals(line)) {
            runSerially(() -> handleMagicWord(in, socket, isr));
          } else if (SI_STOP.equals(line)) {
            runSerially(
                () -> {
                  removeSiFile();
                  ss.close();
                });
          }
        } else {
          handleUnexpectedError();
        }
      } catch (Exception ex) {
        LOGGER.error("Error handling client", ex);
      }
    }

    private void runSerially(IoRunnable task) throws IOException {
      activationLock.lock();
      try {
        task.run();
      } finally {
        activationLock.unlock();
      }
    }

    @FunctionalInterface
    private interface IoRunnable {
      void run() throws IOException;
    }

    private void handleMagicWord(BufferedReader in, Socket s, InputStreamReader isr)
        throws IOException {
      List<String> args = new ArrayList<>();
      Properties props = new Properties();
      boolean arg = false;

      while (true) {
        String line = in.readLine();
        if (SI_EOF.equals(line)) {
          break;
        } else if (SI_ARG.equals(line)) {
          arg = true;
        } else if (SI_PROP.equals(line)) {
          arg = false;
        } else if (Utils.hasText(line)) {
          if (arg) {
            args.add(line);
          } else {
            String[] vals = line.split("=", 2);
            if (vals.length == 2) {
              props.put(vals[0], vals[1]);
            }
          }
        }
      }

      if (props.getProperty(BootstrapContract.P_VERSION) != null) {
        // The bootstrap offers the raw command line: this instance decides whether it is addressed
        // and, if so, whether its configuration changed since it started.
        if (!siApp.isSameInstance(props)) {
          LOGGER.debug("Sending OTHER: the activation is for another instance");
          sendResponse(s, isr, SI_OTHER);
          return;
        }
        if (siApp.configurationChanged()) {
          LOGGER.info("Configuration changed, exiting so that a new instance starts");
          sendResponse(s, isr, SI_EXIT);
          System.exit(0);
        }
        siApp.newActivation(args);
        sendResponse(s, isr, SI_ACK);
        return;
      }
      if (siApp.canStartNewActivation(props)) {
        siApp.newActivation(args);
        LOGGER.debug("Sending ACK for loading args");
        sendResponse(s, isr, SI_ACK);
      } else {
        LOGGER.debug("Sending EXIT");
        sendResponse(s, isr, SI_EXIT);
        System.exit(0);
      }
    }

    private void handleRun(BufferedReader in, Socket s, InputStreamReader isr) throws IOException {
      CommandProtocol.Request request = CommandProtocol.readRequest(in);
      CommandResult result;
      Throwable failure = null;
      try {
        result = execute(request);
      } catch (Exception | LinkageError e) {
        // A LinkageError is expected when the jars are replaced under a running JVM: a class loaded
        // for the first time after the rebuild is then missing.
        failure = e;
        result = CommandResult.error(e.toString());
      }
      try (OutputStream os = s.getOutputStream();
          PrintStream ps = new PrintStream(os, true, isr.getEncoding())) {
        CommandProtocol.writeResponse(ps, result, request.version());
      }
      if (failure != null) {
        // Logged after the answer: rendering a stack trace can fail for the very same reason.
        LOGGER.error("Cannot run {}", request.argv(), failure);
      }
    }

    private CommandResult execute(CommandProtocol.Request request) {
      SingletonApp app = siApp;
      CommandResult refused = refuseNewerClient(request.version());
      if (refused != null) {
        return refused;
      }
      if (app == null) {
        return CommandResult.error("Application is shutting down");
      }
      return CommandManager.runWithTimeout(
          () -> app.runCommand(request.argv(), request.assumeYes()),
          timeout(request.timeoutSeconds()));
    }

    private static CommandResult refuseNewerClient(int version) {
      if (version <= CommandProtocol.VERSION) {
        return null;
      }
      return CommandResult.of(
          CommandResult.CONNECTION,
          "The client speaks protocol version "
              + version
              + ", this Weasis supports "
              + CommandProtocol.VERSION
              + ": update Weasis");
    }

    /**
     * A subscription to the events: the policy decides, then the connection stays open and carries
     * one JSON line per event until the client closes it.
     */
    private void handleSubscribe(BufferedReader in, Socket s, InputStreamReader isr)
        throws IOException {
      CommandProtocol.Subscribe request = CommandProtocol.readSubscribe(in);
      SingletonApp app = siApp;
      CommandResult refused = refuseNewerClient(request.version());
      if (refused == null && app == null) {
        refused = CommandResult.error("Application is shutting down");
      }
      CommandManager.EventStreamGrant grant = null;
      if (refused == null) {
        try {
          grant =
              app.openEventStream(
                  EventFilter.parse(String.join(",", request.globs()), request.where()));
        } catch (CommandFailure e) {
          int status =
              CommandError.REFUSED_BY_POLICY.equals(e.error().code())
                  ? CommandResult.FORBIDDEN
                  : CommandResult.ERROR;
          refused = CommandResult.of(status, e.error().toJson(CommandManager.EVENTS, List.of()));
        } catch (IllegalArgumentException e) {
          refused =
              CommandResult.usage(
                  CommandError.of(CommandError.USAGE, e.getMessage())
                      .toJson(CommandManager.EVENTS, List.of()));
        }
      }
      try (OutputStream os = s.getOutputStream();
          PrintStream ps = new PrintStream(os, true, isr.getEncoding())) {
        if (refused != null) {
          CommandProtocol.writeResponse(ps, refused, request.version());
          return;
        }
        EventStream.serve(s, in, ps, request, grant);
      }
    }

    private static Duration timeout(int requestedSeconds) {
      int max = Integer.getInteger(MAX_TIMEOUT_PROP, 600);
      int seconds = requestedSeconds > 0 ? requestedSeconds : DEFAULT_TIMEOUT_SECONDS;
      return Duration.ofSeconds(Math.clamp(seconds, 1, Math.max(1, max)));
    }

    private void sendResponse(Socket s, InputStreamReader isr, String response) throws IOException {
      try (OutputStream os = s.getOutputStream();
          PrintStream ps = new PrintStream(os, true, isr.getEncoding())) {
        ps.println(response);
        ps.flush();
      }
    }

    private void handleUnexpectedError() throws IOException {
      // random number does not match should not happen
      // shutdown server socket
      removeSiFile();
      ss.close();
      serverStarted = false;
      LOGGER.error("Unexpected error: Singleton {} disabled", stringId);
    }
  }
}
