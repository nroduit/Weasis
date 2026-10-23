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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.weasis.core.ui.editor.image.HangingLayout;
import org.weasis.core.ui.editor.image.ViewState.ViewPresentation;
import org.weasis.dicom.codec.hp.FeatureSelector.Extreme;
import org.weasis.dicom.codec.hp.FeatureSelector.Key;
import org.weasis.dicom.codec.hp.ProtocolJson.Document;
import org.weasis.dicom.codec.hp.WeasisExtensions.Display;
import org.weasis.dicom.codec.hp.WeasisExtensions.DisplaySet;
import org.weasis.dicom.codec.hp.WeasisExtensions.ImageSet;
import org.weasis.dicom.codec.hp.WeasisExtensions.ViewerType;
import org.weasis.dicom.hp.HPComparatorFactory;
import org.weasis.dicom.hp.HPDefinition;
import org.weasis.dicom.hp.HPDisplaySet;
import org.weasis.dicom.hp.HPImageBox;
import org.weasis.dicom.hp.HPImageSet;
import org.weasis.dicom.hp.HPScrollingGroup;
import org.weasis.dicom.hp.HPSelectorFactory;
import org.weasis.dicom.hp.HPTimeBasedImageSet;
import org.weasis.dicom.hp.HangingProtocol;
import org.weasis.dicom.hp.RelativeTime;
import org.weasis.dicom.hp.enums.ImageBoxLayoutType;
import org.weasis.dicom.hp.enums.RelativeTimeUnits;
import org.weasis.dicom.hp.enums.SortingDirection;

/**
 * <i>Save as protocol</i>: turns the current display of a study, a {@link HangingLayout} whose
 * cells show series of the study, into a protocol that would produce it again on similar studies.
 * Per cell the smallest set of feature selectors that distinguishes its series from the other
 * series of the study, the most specific stable features first and the description tokens last; the
 * presentation intent from the view part of the cell: preset, color map, LUT shape, zoom mode,
 * inversion, sort and direction, tiling; the grid as display sets; the synchronization as a
 * scrolling group. The derivation is reported per cell so that a review panel can show the rule and
 * which series of the study match it now.
 */
public final class ProtocolDeriver {

  /** The categorical features tried in order, the most specific and stable first. */
  static final List<Key> CATEGORICAL =
      List.of(
          Key.ROLE,
          Key.ORIENTATION,
          Key.MR_WEIGHTING,
          Key.CT_KERNEL_FAMILY,
          Key.CONTRAST,
          Key.VIEW,
          Key.LATERALITY,
          Key.ANATOMY_GROUP,
          Key.TEMPORAL,
          Key.PET_ATTENUATION_CORRECTED,
          Key.SOP_CLASS);

  /**
   * The rule derived for one cell.
   *
   * @param cell the 0-based position in the layout
   * @param series the series shown
   * @param label the label of the display set
   * @param selectors the feature selectors, the DICOM modality selector apart
   * @param matching the series of the study the rule keeps, the best first
   * @param unique whether the rule singles the series out among the study
   */
  public record CellRule(
      int cell,
      String series,
      String label,
      List<FeatureSelector> selectors,
      List<String> matching,
      boolean unique) {}

  /** The derived protocol with the rule of every cell. */
  public record Derivation(Document document, List<CellRule> rules) {}

  private ProtocolDeriver() {}

  /**
   * Derives a protocol from a display.
   *
   * @param id the entry id
   * @param name the protocol name
   * @param level the hanging protocol level, {@code SINGLE_USER} for a user protocol
   * @param layout the display: grid, synch and cells with their data and view parts
   * @param study the series of the study, the ones shown among them
   */
  public static Derivation derive(
      String id, String name, String level, HangingLayout layout, List<StudySeries> study) {
    Objects.requireNonNull(layout, "layout");
    Map<String, StudySeries> byUid = new LinkedHashMap<>();
    for (StudySeries s : study) {
      byUid.put(s.uid(), s);
    }
    HangingProtocol hp = new HangingProtocol();
    hp.setHangingProtocolName(name);
    hp.setHangingProtocolLevel(level);
    hp.setHangingProtocolCreator("Weasis"); // NON-NLS
    hp.setPartialDataDisplayHandling("ADAPT_LAYOUT"); // NON-NLS

    Map<Integer, ImageSet> imageSets = new LinkedHashMap<>();
    Map<Integer, DisplaySet> displaySets = new LinkedHashMap<>();
    Map<String, HPImageSet> setOfSeries = new LinkedHashMap<>();
    List<CellRule> rules = new ArrayList<>();
    List<HPDisplaySet> shown = new ArrayList<>();
    Set<String> modalities = new LinkedHashSet<>();
    int displayNumber = 0;
    for (HangingLayout.Cell cell : layout.cells()) {
      String uid = cell.seriesId().orElse(null);
      StudySeries series = uid == null ? null : byUid.get(uid);
      if (series == null) {
        continue; // an empty cell, or a series of another study: nothing to derive
      }
      HPImageSet set = setOfSeries.get(uid);
      CellRule rule;
      if (set == null) {
        rule = rule(cell.position(), series, study);
        set = hp.addNewImageSet(null);
        String modality = series.features() == null ? null : series.features().modality();
        if (modality != null) {
          modalities.add(modality);
          set.addImageSetSelector(
              HPSelectorFactory.createAttributeValueSelector(
                  "MATCH", null, Tag.Modality, 1, VR.CS, new String[] {modality})); // NON-NLS
        }
        HPTimeBasedImageSet now = new HPTimeBasedImageSet();
        now.setImageSetLabel(rule.label());
        now.setRelativeTime(new RelativeTime(0, 0, RelativeTimeUnits.DAYS));
        set.addTimeBasedImageSet(now);
        setOfSeries.put(uid, set);
        imageSets.put(set.getImageSetNumber(), new ImageSet(rule.label(), rule.selectors()));
      } else {
        CellRule first =
            rules.stream().filter(r -> uid.equals(r.series())).findFirst().orElseThrow();
        rule =
            new CellRule(
                cell.position(),
                uid,
                first.label(),
                first.selectors(),
                first.matching(),
                first.unique());
      }
      rules.add(rule);

      displayNumber++;
      HPDisplaySet displaySet = new HPDisplaySet();
      displaySet.setDisplaySetNumber(displayNumber);
      displaySet.setDisplaySetLabel(rule.label());
      displaySet.setImageSet(set);
      displaySet.setDisplaySetPresentationGroup(1);
      HPImageBox box = new HPImageBox();
      box.setImageBoxNumber(1);
      HangingLayout.Tiling tiling = cell.tiling();
      if (tiling != null && (tiling.cols() > 1 || tiling.rows() > 1)) {
        box.setImageBoxLayoutType(ImageBoxLayoutType.TILED);
        box.setImageBoxTileHorizontalDimension(tiling.cols());
        box.setImageBoxTileVerticalDimension(tiling.rows());
      } else {
        box.setImageBoxLayoutType(ImageBoxLayoutType.STACK);
      }
      displaySet.addImageBox(box);
      ViewPresentation view =
          cell.panes().isEmpty() ? ViewPresentation.DEFAULT : cell.panes().getFirst().view();
      sorting(view).ifPresent(displaySet::addSortingOperation);
      if (view.invert()) {
        displaySet.setShowGrayscaleInverted("Y"); // NON-NLS
      }
      if (view.justify() != null) {
        displaySet
            .getAttributes()
            .setString(
                Tag.DisplaySetHorizontalJustification, VR.CS, view.justify().horizontal().name());
        displaySet
            .getAttributes()
            .setString(
                Tag.DisplaySetVerticalJustification, VR.CS, view.justify().vertical().name());
      }
      hp.addDisplaySet(displaySet);
      shown.add(displaySet);
      displaySets.put(
          displayNumber,
          new DisplaySet(ViewerType.VIEWER_2D, display(view, layout.synch()), List.of(), null));
    }

    for (String modality : modalities) {
      HPDefinition definition = new HPDefinition();
      definition.setModality(modality);
      hp.addHangingProtocolDefinition(definition);
    }
    String synch = layout.synch();
    if (shown.size() > 1 && synch != null && !"none".equalsIgnoreCase(synch)) { // NON-NLS
      HPScrollingGroup scrolling = new HPScrollingGroup();
      shown.forEach(scrolling::addDisplaySet);
      scrolling.updateAttributes();
      hp.addScrollingGroup(scrolling);
    }
    WeasisExtensions weasis =
        new WeasisExtensions(imageSets, displaySets, Map.of(), null, null, null, null, null);
    return new Derivation(new Document(id, hp, weasis), rules);
  }

  /**
   * The rule of a series among the study: categorical filters added while they exclude another
   * series, then a preference on the thickness, the image count or the description when the filters
   * leave several.
   */
  public static CellRule rule(int cell, StudySeries series, List<StudySeries> study) {
    SeriesFeatures features = series.features();
    String modality = features == null ? null : features.modality();
    List<StudySeries> remaining = new ArrayList<>();
    for (StudySeries s : study) {
      if (modality == null || (s.features() != null && modality.equals(s.features().modality()))) {
        remaining.add(s);
      }
    }
    if (!remaining.contains(series)) {
      remaining.add(series);
    }
    List<FeatureSelector> selectors = new ArrayList<>();
    for (Key key : CATEGORICAL) {
      if (remaining.size() == 1) {
        break;
      }
      Set<String> values = valuesOf(key.read(features));
      if (values.isEmpty()) {
        continue;
      }
      FeatureSelector selector = FeatureSelector.match(key, values.toArray(String[]::new));
      List<StudySeries> kept =
          remaining.stream().filter(s -> selector.passes(s.features())).toList();
      if (kept.size() < remaining.size() && kept.contains(series)) {
        selectors.add(selector);
        remaining = new ArrayList<>(kept);
      }
    }
    boolean unique = remaining.size() == 1;
    if (!unique) {
      preference(series, remaining).ifPresent(selectors::add);
      remaining.remove(series);
      remaining.addFirst(series);
    }
    return new CellRule(
        cell,
        series.uid(),
        label(series),
        selectors,
        remaining.stream().map(StudySeries::uid).toList(),
        unique);
  }

  /** A preference that ranks the series first among the ones the filters keep, if any does. */
  static Optional<FeatureSelector> preference(StudySeries series, List<StudySeries> remaining) {
    Double thickness = series.features() == null ? null : series.features().sliceThickness();
    if (thickness != null) {
      boolean thinnest = true;
      boolean thickest = true;
      for (StudySeries other : remaining) {
        Double t = other.features() == null ? null : other.features().sliceThickness();
        if (other != series && t != null) {
          thinnest &= t > thickness;
          thickest &= t < thickness;
        }
      }
      if (thinnest) {
        return Optional.of(FeatureSelector.prefer(Key.SLICE_THICKNESS, 1, Extreme.MIN));
      }
      if (thickest) {
        return Optional.of(FeatureSelector.prefer(Key.SLICE_THICKNESS, 1, Extreme.MAX));
      }
    }
    boolean largest =
        remaining.stream().allMatch(o -> o == series || o.imageCount() < series.imageCount());
    if (largest) {
      return Optional.of(FeatureSelector.prefer(Key.IMAGE_COUNT, 1, Extreme.MAX));
    }
    List<String> tokens =
        series.features() == null || series.features().descriptionTokens() == null
            ? List.of()
            : series.features().descriptionTokens();
    Set<String> own = new LinkedHashSet<>();
    for (String token : tokens) {
      boolean shared =
          remaining.stream()
              .filter(o -> o != series && o.features() != null)
              .allMatch(
                  o ->
                      o.features().descriptionTokens() != null
                          && o.features().descriptionTokens().contains(token));
      if (!shared) {
        own.add(token.toUpperCase(Locale.ROOT));
      }
    }
    if (!own.isEmpty()) {
      return Optional.of(FeatureSelector.prefer(Key.DESCRIPTION, 0.5, own.toArray(String[]::new)));
    }
    return Optional.empty();
  }

  private static Set<String> valuesOf(Object value) {
    Set<String> values = new LinkedHashSet<>();
    if (value instanceof Collection<?> items) {
      for (Object item : items) {
        if (item != null) {
          values.add(item.toString().toUpperCase(Locale.ROOT));
        }
      }
    } else if (value != null) {
      String text = value.toString().toUpperCase(Locale.ROOT);
      if (!"UNKNOWN".equals(text)) { // NON-NLS
        values.add(text);
      }
    }
    return values;
  }

  /** The series description as the label, else the modality. */
  private static String label(StudySeries series) {
    String description =
        series.dataset() == null ? null : series.dataset().getString(Tag.SeriesDescription);
    if (description != null && !description.isBlank()) {
      return description.trim();
    }
    String modality = series.features() == null ? null : series.features().modality();
    return modality == null ? "Series " + series.uid() : modality; // NON-NLS
  }

  private static Optional<org.weasis.dicom.hp.HPComparator> sorting(ViewPresentation view) {
    SortingDirection direction =
        view.inverseStack() ? SortingDirection.DECREASING : SortingDirection.INCREASING;
    String sort = view.sort();
    if ("slice-position".equals(sort)) { // NON-NLS
      return Optional.of(HPComparatorFactory.createSortAlongAxis(direction));
    }
    if ("acquisition-time".equals(sort)) { // NON-NLS
      return Optional.of(HPComparatorFactory.createSortByAcqTime(direction));
    }
    return Optional.empty(); // the other keys have no DICOM sorting operation yet
  }

  private static Display display(ViewPresentation view, String synch) {
    String zoom = view.zoomType() == null ? null : view.zoomType().name().toLowerCase(Locale.ROOT);
    return new Display(
        view.preset(),
        view.colorMap(),
        view.lutShape(),
        null,
        null,
        null,
        null,
        zoom,
        synch == null || "none".equalsIgnoreCase(synch) ? null : synch, // NON-NLS
        view.scaleGroup());
  }

  /** The id of a user protocol from its name: {@code user.<name>} in kebab case. */
  public static String idOf(String name) {
    String slug =
        name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    return "user." + (slug.isEmpty() ? "protocol" : slug); // NON-NLS
  }

  /** The uids of a set of series, for a quick containment test. */
  static Set<String> uids(Collection<StudySeries> series) {
    Set<String> uids = new HashSet<>();
    series.forEach(s -> uids.add(s.uid()));
    return uids;
  }
}
