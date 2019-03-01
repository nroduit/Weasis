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
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Singleton {

    private static final Logger LOGGER = Logger.getLogger(Singleton.class.getName());

    private static final String LOCALHOST = "127.0.0.1";
    private static final File SI_FILEDIR =
        new File(System.getProperty("user.home") + File.separator + ".weasis", "singleton");
    private static final String SI_MAGICWORD = "si.init";
    private static final String SI_ACK = "si.ack";
    private static final String SI_EXIT = "si.exit";
    private static final String SI_STOP = "si.stop";
    private static final String SI_EOF = "si.EOF";
    private static final int ENCODING_PLATFORM = 1;
    private static final int ENCODING_UNICODE = 2;

    private static final String ENCODING_UNICODE_NAME = "UTF-16LE";

    private static int currPort;
    private static String stringId = null;
    private static String randomNumberString = null;

    private static SingletonServer siServer;
    private static SingletonApp siApp = null;

    private static final SecureRandom random = new SecureRandom();
    private static volatile boolean serverStarted = false;
    private static int randomNumber;

    public interface SingletonApp {
        public void newActivation(ConfigData data);

        boolean canStartNewActivation(ConfigData data);
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
                    LOGGER.log(Level.SEVERE, "Create a Singleton serveur", e); //$NON-NLS-1$
                    return;
                }
                siApp = sia;
                serverStarted = true;
            }
        }
    }

    public static void stop() {
        if (siApp == null) {
            LOGGER.log(Level.WARNING, "Singleton.stop() called when not running, id: {0}", stringId); //$NON-NLS-1$
            return;
        }

        synchronized (Singleton.class) {

            siApp = null;

            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
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
                    LOGGER.log(Level.SEVERE, "Stopping Singleton server", e); //$NON-NLS-1$
                }
                return null;
            });

            siServer.runnable.removeSiFile();
        }
    }

    public static boolean running(String id) {
        LOGGER.log(Level.FINE, "Check if another instance running for id: {0}", id); //$NON-NLS-1$
        String[] fList = SI_FILEDIR.list();
        if (fList != null) {
            for (String file : fList) {
                // if file with the same prefix already exist, server is running
                if (file.startsWith(id)) {
                    try {
                        currPort = Integer.parseInt(file.substring(file.lastIndexOf('_') + 1));
                    } catch (NumberFormatException nfe) {
                        LOGGER.log(Level.SEVERE, "Cannot parse port", nfe); //$NON-NLS-1$
                        return false;
                    }

                    File siFile = new File(SI_FILEDIR, file);
                    // get random number from single instance file
                    try (BufferedReader br = new BufferedReader(new FileReader(siFile))) {
                        randomNumberString = br.readLine();
                    } catch (IOException ioe) {
                        LOGGER.log(Level.SEVERE, "Cannot read random numbrer from file {0}", siFile.getPath()); //$NON-NLS-1$
                    }
                    stringId = id;
                    LOGGER.log(Level.CONFIG, "Singleton server {0} already running on port {1}", //$NON-NLS-1$
                        new Object[] { stringId, currPort });
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean invoke(String id, String[] args) {
        return running(id) && connectToServer(args);
    }

    /**
     * Returns true if we connect successfully to the server for the stringId
     */
    static boolean connectToServer(String[] args) {
        LOGGER.log(Level.CONFIG, "Connect to {0} on port {1}", new Object[] { stringId, currPort }); //$NON-NLS-1$
        if (randomNumberString == null) {
            // should not happen
            LOGGER.log(Level.SEVERE, "MAGIC number is null, cannot connect."); //$NON-NLS-1$
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

            for (String arg : args) {
                out.println(arg);
            }

            // indicate end of file transmission
            out.println(SI_EOF);
            out.flush();

            // wait for ACK (OK) response
            LOGGER.log(Level.FINE, "Waiting for ACK"); //$NON-NLS-1$
            final int tries = 5;

            // try to listen for ACK
            for (int i = 0; i < tries; i++) {
                String str = br.readLine();
                if (SI_ACK.equals(str)) {
                    LOGGER.log(Level.FINE, "Got ACK"); //$NON-NLS-1$
                    return true;
                } else if (SI_EXIT.equals(str)) {
                    File file = getSiFile(stringId, currPort);
                    waitDeletedFile(file);
                    return false;
                }
            }
            LOGGER.log(Level.SEVERE, "No ACK from server"); //$NON-NLS-1$
        } catch (java.net.SocketException se) {
            LOGGER.log(Level.SEVERE, "No server is running", se); //$NON-NLS-1$
        } catch (Exception ioe) {
            LOGGER.log(Level.SEVERE, "Cannot connect to server", ioe); //$NON-NLS-1$
        }
        return false;
    }

    private static void waitDeletedFile(File file) {
        int loop = 0;
        boolean runLoop = true;
        while (runLoop) {
            LOGGER.log(Level.INFO, "Wait 100 ms to start a the new instance once the previous has stopped"); //$NON-NLS-1$
            try {
                if (!file.exists()) {
                    break;
                }
                TimeUnit.MILLISECONDS.sleep(100);
                loop++;
                if (loop > 300) { // Let 30s max to setup Felix framework
                    LOGGER.log(Level.SEVERE, "The pid of the singleton still exists. Try to start the new instance."); //$NON-NLS-1$
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
            if ("1.8".equals(System.getProperty("java.specification.version"))) { //$NON-NLS-1$ //$NON-NLS-2$
                thread = new Thread(null, runnable, "SIThread", 0);
            } else {
                // TODO call directly the constructor when Java 8 will be dropped
                // thread = new Thread(null, runnable, "SIThread", 0, false);
                Class<?> clazz = Class.forName("java.lang.Thread"); //$NON-NLS-1$
                Constructor<?> constructor =
                    clazz.getConstructor(ThreadGroup.class, Runnable.class, String.class, long.class, boolean.class); // $NON-NLS-1$
                thread = (Thread) constructor.newInstance(null, runnable, "SIThread", 0, false);
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
            LOGGER.log(Level.CONFIG, "Local port of the Singleton server: {0}", port); //$NON-NLS-1$

            // create the single instance file with canonical home and port num
            createSingletonFile(stringId, port);
        }

        private void removeSiFile() {
            File file = getSiFile(stringId, port);
            LOGGER.log(Level.CONFIG, "Removing SingletonFile: {0}", file); //$NON-NLS-1$
            FileUtil.delete(file);
        }

        private static void createSingletonFile(final String id, final int port) {
            final File siFile = getSiFile(id, port);
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                SI_FILEDIR.mkdirs();
                String[] fList = SI_FILEDIR.list();
                if (fList != null) {
                    for (String file : fList) {
                        if (file.startsWith(id)) {
                            LOGGER.log(Level.CONFIG, "Remove file with same prefix {0}", file); //$NON-NLS-1$
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
                    LOGGER.log(Level.SEVERE, "Cannot add random number to file", e); //$NON-NLS-1$
                }
                return null;
            });
        }

        @Override
        public void run() {
            // handle all the incoming request from server port
            AccessController.doPrivileged((PrivilegedAction<Void>) this::runSingletonServer);
        }

        private static String getStreamEncoding(InputStream is) throws IOException {
            // read first byte for encoding type
            int encoding = is.read();
            if (encoding == ENCODING_PLATFORM) {
                return Charset.defaultCharset().name();
            } else if (encoding == ENCODING_UNICODE) {
                return ENCODING_UNICODE_NAME;
            } else {
                LOGGER.log(Level.SEVERE, "Unknown encoding: {0}", encoding); //$NON-NLS-1$
                return null;
            }
        }

        private Void runSingletonServer() {
            List<String> recvArgs = new ArrayList<>();
            while (true) {
                String line;
                recvArgs.clear();
                try (Socket s = ss.accept();
                                InputStream is = s.getInputStream();
                                InputStreamReader isr = new InputStreamReader(is, getStreamEncoding(is));
                                BufferedReader in = new BufferedReader(isr);) {
                    LOGGER.log(Level.FINE, "Singleton server is waiting a connection"); //$NON-NLS-1$

                    // First read the random number
                    line = in.readLine();
                    if (line.equals(String.valueOf(randomNumber))) {
                        line = in.readLine();

                        LOGGER.log(Level.FINE, "Recieve message: {0}", line); //$NON-NLS-1$
                        if (line.equals(SI_MAGICWORD)) {
                            LOGGER.log(Level.FINE, "Got Magic work"); //$NON-NLS-1$
                            while (true) {
                                try {
                                    line = in.readLine();
                                    if (line != null && line.equals(SI_EOF)) {
                                        break;
                                    } else {
                                        recvArgs.add(line);
                                    }
                                } catch (IOException ioe1) {
                                    LOGGER.log(Level.SEVERE, "Reading singleton lock file", ioe1); //$NON-NLS-1$
                                }
                            }
                            String[] arguments = recvArgs.toArray(new String[recvArgs.size()]);
                            ConfigData data = new ConfigData(arguments);
                            if (siApp.canStartNewActivation(data)) {
                                siApp.newActivation(data);
                                LOGGER.log(Level.FINE, "Sending ACK"); //$NON-NLS-1$
                                try (OutputStream os = s.getOutputStream();
                                                PrintStream ps = new PrintStream(os, true, isr.getEncoding())) {
                                    ps.println(SI_ACK);
                                    ps.flush();
                                }
                            } else {
                                LOGGER.log(Level.FINE, "Sending EXIT"); //$NON-NLS-1$
                                try (OutputStream os = s.getOutputStream();
                                                PrintStream ps = new PrintStream(os, true, isr.getEncoding())) {
                                    ps.println(SI_EXIT);
                                    ps.flush();
                                }
                                System.exit(0);
                            }
                        } else if (line.equals(SI_STOP)) {
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
                        LOGGER.log(Level.SEVERE, "Unexpected error: Singleton {0} disabled", stringId); //$NON-NLS-1$
                        return null;
                    }
                } catch (IOException ex) {
                    LOGGER.log(Level.SEVERE, "Starting Singleton server", ex); //$NON-NLS-1$
                }
            }
            return null;
        }
    }
}
