/*
 * Copyright (c) 2021 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.util;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.security.SecureRandom;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.net.ServerSocketFactory;

public class SocketUtil {

  public static final int PORT_MIN = 1024;
  public static final int PORT_MAX = 65535;
  private static final SecureRandom random = new SecureRandom();

  private SocketUtil() {}

  public static boolean isPortAvailable(int port) {
    try {
      ServerSocket socket =
          ServerSocketFactory.getDefault()
              .createServerSocket(port, 1, InetAddress.getByName("localhost")); // NON-NLS
      socket.close();
      return true;
    } catch (Exception ex) {
      return false;
    }
  }

  public static SortedSet<Integer> findAvailablePorts(int numberOfPorts) {
    SortedSet<Integer> availablePorts = new TreeSet<>();
    int attemptCount = 0;
    while ((++attemptCount <= numberOfPorts + 100) && availablePorts.size() < numberOfPorts) {
      availablePorts.add(findAvailablePort());
    }

    if (availablePorts.size() != numberOfPorts) {
      throw new IllegalStateException(
          String.format(
              "Could not find %s TCP ports available in the range [%d, %d]",
              numberOfPorts, PORT_MIN, PORT_MAX));
    }
    return availablePorts;
  }

  private static int findRandomPort() {
    int portRange = PORT_MAX - PORT_MIN;
    return PORT_MIN + random.nextInt(portRange + 1);
  }

  public static int findAvailablePort() {
    int portRange = PORT_MAX - PORT_MIN;
    int port;
    int count = 0;
    do {
      if (count > portRange) {
        throw new IllegalStateException(
            String.format(
                "Could not find an available TCP port in the range [%d, %d] after %d attempts",
                PORT_MIN, PORT_MAX, count));
      }
      port = findRandomPort();
      count++;
    } while (!isPortAvailable(port));

    return port;
  }
}
