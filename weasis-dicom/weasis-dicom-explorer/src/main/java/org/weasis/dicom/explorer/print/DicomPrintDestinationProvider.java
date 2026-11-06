/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.explorer.print;

import java.util.List;
import org.pf4j.Extension;
import org.weasis.core.ui.print.PrintDestination;
import org.weasis.core.ui.print.PrintDestinationProvider;
import org.weasis.plugin.SingletonExtension;

/** Lists the DICOM print nodes among the destinations of the print dialog. */
@Extension
@SingletonExtension
public class DicomPrintDestinationProvider implements PrintDestinationProvider {

  @Override
  public List<PrintDestination> destinations() {
    return List.copyOf(DicomPrintDestination.lookup());
  }
}
