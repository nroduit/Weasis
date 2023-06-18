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
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class Singleton {

  private static final Logger LOGGER = System.getLogger(Singleton.class.getName());

  private static final String LOCALHOST = "127.0.0.1";
  private static final File SI_FILEDIR =
      new File(
          System.getProperty("user.home") + File.separator + ".weasis", "singleton"); // NON-NLS
  private static final String SI_MAGICWORD = "si.init";
  private static final String SI_ARG = "si.arg";
  private static final String SI_PROP = "si.prop";
  private static final String SI_ACK = "si.ack";
  private static final String SI_EXIT = "si.exit";
  private static final String SI_STOP = "si.stop";
  private static final String SI_EOF = "si.EOF";
  private static final int ENCODING_PLATFORM = 1;
  private static final int ENCODING_UNICODE = 2;

  private static final String ENCODING_UNICODE_NAME = "UTF-16LE"; // NON-NLS

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
          LOGGER.log(Level.ERROR, "Create a Singleton serveur", e);
          return;
        }
        siApp = sia;
        serverStarted = true;
      }
    }
  }

  public static void stop() {
    if (siApp == null) {
      LOGGER.log(Level.WARNING, "Singleton.stop() called when not running, id: {0}", stringId);
      return;
    }

    synchronized (Singleton.class) {
      siApp = null;

      try (Socket socket = new Socket(LOCALHOST, siServer.getPort());
          OutputStream os = socket.getOutputStream();
          PrintStream out = new PrintStream(os, true, Charset.defaultCharset().name())) {
        byte[] encoding = new byte[1];
        encoding[0] = ENCODING_PLATFORM;
        os.write(encoding);
        out.println(randomNumber);
        out.println(Singleton.SI_STOP);
        out.flush();
        serverStarted = false;
      } catch (IOException e) {
        LOGGER.log(Level.ERROR, "Stopping Singleton server", e);
      }

      siServer.runnable.removeSiFile();
    }
  }

  public static boolean running(String id) {
    LOGGER.log(Level.DEBUG, "Check if another instance running for id: {0}", id);
    String[] fList = SI_FILEDIR.list();
    if (fList != null) {
      for (String file : fList) {
        // if file with the same prefix already exist, server is running
        if (file.startsWith(id)) {
          try {
            currPort = Integer.parseInt(file.substring(file.lastIndexOf('_') + 1));
          } catch (NumberFormatException nfe) {
            LOGGER.log(Level.ERROR, "Cannot parse port", nfe);
            return false;
          }

          File siFile = new File(SI_FILEDIR, file);
          // get random number from single instance file
          try (BufferedReader br =
              new BufferedReader(new FileReader(siFile, StandardCharsets.UTF_8))) {
            randomNumberString = br.readLine();
          } catch (IOException ioe) {
            LOGGER.log(Level.ERROR, "Cannot read random numbrer from file {0}", siFile.getPath());
          }
          stringId = id;
          LOGGER.log(
              Level.INFO, "Singleton server {0} already running on port {1}", stringId, currPort);
          return true;
        }
      }
    }
    return false;
  }

  public static boolean invoke(ConfigData configData) {
    return running(configData.getSourceID()) && connectToServer(configData);
  }

  private static void printProperty(PrintStream out, String key, Properties p) {
    out.printf("%s=%s%n", key, p.getProperty(key, "")); // NON-NLS
  }

  /** Returns true if we connect successfully to the server for the stringId */
  static boolean connectToServer(ConfigData configData) {
    LOGGER.log(Level.INFO, "Connect to {0} on port {1}", stringId, currPort);
    if (randomNumberString == null) {
      // should not happen
      LOGGER.log(Level.ERROR, "MAGIC number is null, cannot connect.");
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
      printProperty(out, WeasisLauncher.P_WEASIS_USER, p);
      printProperty(out, WeasisLauncher.P_WEASIS_CONFIG_HASH, p);

      // indicate end of file transmission
      out.println(SI_EOF);
      out.flush();

      // wait for ACK (OK) response
      LOGGER.log(Level.DEBUG, "Waiting for ACK");
      final int tries = 5;

      // try to listen for ACK
      for (int i = 0; i < tries; i++) {
        String str = br.readLine();
        if (SI_ACK.equals(str)) {
          LOGGER.log(Level.DEBUG, "Got ACK");
          return true;
        } else if (SI_EXIT.equals(str)) {
          File file = getSiFile(stringId, currPort);
          waitDeletedFile(file);
          return false;
        }
      }
      LOGGER.log(Level.ERROR, "No ACK from server");
    } catch (java.net.SocketException se) {
      LOGGER.log(Level.ERROR, "No server is running", se);
    } catch (Exception ioe) {
      LOGGER.log(Level.ERROR, "Cannot connect to server", ioe);
    }
    return false;
  }

  private static void waitDeletedFile(File file) {
    int loop = 0;
    boolean runLoop = true;
    while (runLoop) {
      LOGGER.log(
          Level.INFO, "Wait 100 ms to start a the new instance once the previous has stopped");
      try {
        if (!file.exists()) {
          break;
        }
        TimeUnit.MILLISECONDS.sleep(100);
        loop++;
        if (loop > 300) { // Let 30s max to set up Felix framework
          LOGGER.log(
              Level.ERROR, "The pid of the singleton still exists. Try to start the new instance.");
          runLoop = false;
        }
      } catch (InterruptedException e) {
        runLoop = false;
        Thread.currentThread().interrupt();
      }
    }
  }

  private static File getSiFile(final String id, final int port) {
    return new File(SI_FILEDIR, id + "_" + port);
  }

  private static class SingletonServer {

    private final SingletonServerRunnable runnable;
    private final Thread thread;

    SingletonServer(SingletonServerRunnable runnable) throws Exception {
      if ("1.8".equals(System.getProperty("java.specification.version"))) { // NON-NLS
        thread = new Thread(null, runnable, "SIThread", 0);
      } else {
        thread = new Thread(null, runnable, "SIThread", 0, false);
      }

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
      LOGGER.log(Level.INFO, "Local port of the Singleton server: {0}", port);

      // create the single instance file with canonical home and port num
      createSingletonFile(stringId, port);
    }

    private void removeSiFile() {
      File file = getSiFile(stringId, port);
      LOGGER.log(Level.INFO, "Removing SingletonFile: {0}", file);
      FileUtil.delete(file);
    }

    private static void createSingletonFile(final String id, final int port) {
      final File siFile = getSiFile(id, port);

      SI_FILEDIR.mkdirs();
      String[] fList = SI_FILEDIR.list();
      if (fList != null) {
        for (String file : fList) {
          if (file.startsWith(id)) {
            LOGGER.log(Level.INFO, "Remove file with same prefix {0}", file);
            FileUtil.delete(new File(SI_FILEDIR, file));
          }
        }
      }

      try (PrintStream out = new PrintStream(new FileOutputStream(siFile))) {
        siFile.deleteOnExit();
        // write random number to single instance file
        randomNumber = random.nextInt();
        out.print(randomNumber);
      } catch (IOException e) {
        LOGGER.log(Level.ERROR, "Cannot add random number to file", e);
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
      if (encoding == ENCODING_PLATFORM) {
        return Charset.defaultCharset().name();
      } else if (encoding == ENCODING_UNICODE) {
        return ENCODING_UNICODE_NAME;
      } else {
        LOGGER.log(Level.ERROR, "Unknown encoding: {0}", encoding);
        return null;
      }
    }

    private Void runSingletonServer() {
      while (true) {
        try (Socket s = ss.accept();
            InputStream is = s.getInputStream();
            InputStreamReader isr = new InputStreamReader(is, getStreamEncoding(is));
            BufferedReader in = new BufferedReader(isr)) {
          LOGGER.log(Level.DEBUG, "Singleton server is waiting a connection");

          // First read the random number
          String line = in.readLine();
          if (line.equals(String.valueOf(randomNumber))) {
            line = in.readLine();

            LOGGER.log(Level.DEBUG, "Recieve message: {0}", line);
            if (SI_MAGICWORD.equals(line)) {
              LOGGER.log(Level.DEBUG, "Got Magic work");
              List<String> recvArgs = new ArrayList<>();
              Properties props = new Properties();
              boolean arg = false;
              while (true) {
                try {
                  line = in.readLine();
                  if (SI_EOF.equals(line)) {
                    break;
                  } else if (SI_ARG.equals(line)) {
                    arg = true;
                  } else if (SI_PROP.equals(line)) {
                    arg = false;
                  } else if (Utils.hasText(line)) {
                    if (arg) {
                      recvArgs.add(line);
                    } else {
                      String[] vals = line.split("=", 2);
                      if (vals.length == 2) {
                        props.put(vals[0], vals[1]);
                      }
                    }
                  }
                } catch (IOException ioe1) {
                  LOGGER.log(Level.ERROR, "Reading singleton lock file", ioe1);
                }
              }
              if (siApp.canStartNewActivation(props)) {
                siApp.newActivation(recvArgs);
                LOGGER.log(Level.DEBUG, "Sending ACK");
                try (OutputStream os = s.getOutputStream();
                    PrintStream ps = new PrintStream(os, true, isr.getEncoding())) {
                  ps.println(SI_ACK);
                  ps.flush();
                }
              } else {
                LOGGER.log(Level.DEBUG, "Sending EXIT");
                try (OutputStream os = s.getOutputStream();
                    PrintStream ps = new PrintStream(os, true, isr.getEncoding())) {
                  ps.println(SI_EXIT);
                  ps.flush();
                }
                System.exit(0);
              }
            } else if (SI_STOP.equals(line)) {
              removeSiFile();
              break;
            }
          } else {
            // random number does not match
            // should not happen
            // shutdown server socket
            removeSiFile();
            ss.close();
            serverStarted = false;
            LOGGER.log(Level.ERROR, "Unexpected error: Singleton {0} disabled", stringId);
            return null;
          }
        } catch (IOException ex) {
          LOGGER.log(Level.ERROR, "Starting Singleton server", ex);
        }
      }
      return null;
    }
  }
}
