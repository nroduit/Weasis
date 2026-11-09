/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec.hp;

import java.util.Map;
import java.util.Set;
import org.pf4j.ExtensionPoint;
import org.weasis.core.api.media.data.MediaSeries;
import org.weasis.core.api.provenance.Sourced;
import org.weasis.dicom.codec.DicomImageElement;

/**
 * A plugin that derives series features the headers and the tables do not give: a sequence
 * classifier, a site table, a model. The extractor calls every provider after its own pass, in
 * plugin order, with what it knows so far; each value comes back with its provenance (a {@code
 * CLASSIFIER} with a confidence, or a {@code TABLE}) and is merged by the precedence rule of {@link
 * Sourced#merge}: a provider fills what no higher source produced, replaces a heuristic when its
 * confidence reaches the threshold of the provider ({@code weasis.hp.provider.<id>.threshold}, 0.8
 * by default), and otherwise contests, which the why panel and {@code hp:features} show. A provider
 * that throws is logged and skipped; it never blocks the loading of a series.
 */
public interface SeriesFeatureProvider extends ExtensionPoint {

  /** The id of the provider, the key of its threshold setting ({@code weasis-ai-seqclass}). */
  String id();

  /** The feature ids the provider may return, from {@link FeatureSelector.Key#id()}. */
  Set<String> provides();

  /**
   * The values the provider derives for the series, by feature id; a value it cannot say is left
   * out. The map may be empty.
   */
  Map<String, Sourced<?>> derive(MediaSeries<DicomImageElement> series, SeriesFeatures soFar);
}
