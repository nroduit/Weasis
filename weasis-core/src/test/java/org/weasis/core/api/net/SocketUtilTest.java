/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.SortedSet;
import org.junit.jupiter.api.Test;

class SocketUtilTest {

  @Test
  void findAvailablePortReturnsPortInValidRange() {
    int port = SocketUtil.findAvailablePort();
    assertTrue(port >= SocketUtil.PORT_MIN && port <= SocketUtil.PORT_MAX);
    assertTrue(SocketUtil.isPortAvailable(port));
  }

  @Test
  void findAvailablePortsReturnsRequestedCount() {
    SortedSet<Integer> ports = SocketUtil.findAvailablePorts(3);
    assertTrue(ports.size() == 3);
    ports.forEach(p -> assertTrue(p >= SocketUtil.PORT_MIN && p <= SocketUtil.PORT_MAX));
    // Verify uniqueness implicit from SortedSet semantics
    assertNotEquals(ports.first(), ports.last());
  }

  @Test
  void findAvailablePortsRejectsZeroOrNegative() {
    assertThrows(IllegalArgumentException.class, () -> SocketUtil.findAvailablePorts(0));
    assertThrows(IllegalArgumentException.class, () -> SocketUtil.findAvailablePorts(-5));
  }

  @Test
  void isPortAvailableReturnsFalseForBoundPort() throws Exception {
    int port = SocketUtil.findAvailablePort();
    try (var server = new java.net.ServerSocket()) {
      server.bind(new java.net.InetSocketAddress("localhost", port));
      assertFalse(SocketUtil.isPortAvailable(port));
    }
  }

  @Test
  void isPortAvailableReturnsFalseForInvalidPort() {
    assertFalse(SocketUtil.isPortAvailable(-1));
    assertFalse(SocketUtil.isPortAvailable(70_000));
  }
}
