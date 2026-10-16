/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.items;

/** Whether the pixel data of a series is on this machine, being transferred, or neither. */
public enum DownloadState {
  /** No transfer is attached to the series: local files, or a download that finished. */
  COMPLETE,
  /** A transfer is attached but not running: never started, or stopped by the user. */
  PENDING,
  /** The series is being downloaded. */
  DOWNLOADING;

  public String getId() {
    return name().toLowerCase();
  }
}
