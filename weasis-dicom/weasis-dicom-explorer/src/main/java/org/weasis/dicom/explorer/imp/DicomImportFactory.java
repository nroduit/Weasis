/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.imp;

import java.util.Map;
import org.pf4j.ExtensionPoint;
import org.weasis.dicom.explorer.TransferKind;

/**
 * Contributes an import page to the explorer side: to the {@code import} or the {@code retrieve}
 * tool.
 */
public interface DicomImportFactory extends ExtensionPoint {

  ImportDicom createDicomImportPage(Map<String, Object> properties);

  /** Whether the site enables the page; a tool with no available page gets no stripe button. */
  default boolean isAvailable() {
    return true;
  }

  /** The source of the page, which decides the tool that hosts it. */
  default TransferKind getKind() {
    return TransferKind.LOCAL;
  }
}
