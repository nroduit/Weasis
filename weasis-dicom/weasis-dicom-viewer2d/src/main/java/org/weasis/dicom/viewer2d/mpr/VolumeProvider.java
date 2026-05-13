/*
 * Copyright (c) 2024 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.viewer2d.mpr;

/**
 * Interface for viewer plugins that can provide a shared {@link Volume} for a given {@link
 * OriginalStack}. Implementing this interface allows volume data to be shared across different
 * viewer types (e.g. MPR and Volume Rendering), avoiding redundant memory allocation when the same
 * DICOM series is opened in both an MPR view and a 3D Volume Rendering view.
 *
 * <p>Plugins located in modules that {@code weasis-dicom-viewer2d} cannot depend on (such as {@code
 * weasis-dicom-viewer3d}) can implement this interface to participate in the shared-volume lookup
 * performed by {@link Volume#getSharedVolume(OriginalStack)}.
 *
 * @see Volume#getSharedVolume(OriginalStack)
 */
public interface VolumeProvider {

  /**
   * Returns the {@link Volume} held by this provider whose {@link OriginalStack} matches the given
   * one, or {@code null} if this provider has no matching volume.
   *
   * @param originalStack the stack to match; must not be {@code null}
   * @return the matching volume, or {@code null} if not found
   */
  Volume<?, ?> getVolumeForStack(OriginalStack originalStack);
}
