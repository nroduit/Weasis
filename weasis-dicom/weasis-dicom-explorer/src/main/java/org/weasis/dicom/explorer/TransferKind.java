/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer;

/**
 * Where the DICOM objects of an import or export page come from or go to: the local device (files,
 * folders, media) or a network node. The kind decides which tool of the explorer side hosts the
 * page: {@code import} or {@code export} for the local kind, {@code retrieve} or {@code send} for
 * the network kind.
 */
public enum TransferKind {
  LOCAL,
  NETWORK
}
