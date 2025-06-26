/*
 * Copyright (c) 2023 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Function;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.weasis.core.api.media.data.MediaSeriesGroup;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.api.util.ResourceUtil.ResourceIconPath;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.macro.SOPInstanceReference;

public class HiddenSeriesManager {
  private static final HiddenSeriesManager instance = new HiddenSeriesManager();
  public final Map<String, Set<HiddenSpecialElement>> series2Elements = new ConcurrentHashMap<>();
  public final Map<String, Set<String>> patient2Series = new ConcurrentHashMap<>();
  public final Map<String, Set<String>> reference2Series = new ConcurrentHashMap<>();

  public final Map<String, Set<String>> sopRef2Series = new ConcurrentHashMap<>();

  public static HiddenSeriesManager getInstance() {
    return instance;
  }

  public void extractReferencedSeries(Attributes dicom, String originSeriesUID) {
    extractReferencedSeries(dicom, originSeriesUID, null);
  }

  public void extractReferencedSeries(
      Attributes dicom,
      String originSeriesUID,
      Function<String, Map<String, Set<LazyContourLoader>>> addSeries) {
    if (dicom == null || !StringUtil.hasText(originSeriesUID)) {
      return;
    }
    Sequence seriesRef = dicom.getSequence(Tag.ReferencedSeriesSequence);
    if (seriesRef != null) {
      for (Attributes ref : seriesRef) {
        String seriesUID = ref.getString(Tag.SeriesInstanceUID);
        if (StringUtil.hasText(seriesUID)) {
          reference2Series
              .computeIfAbsent(seriesUID, _ -> new CopyOnWriteArraySet<>())
              .add(originSeriesUID);
          if (addSeries != null) {
            Map<String, Set<LazyContourLoader>> refMap = addSeries.apply(seriesUID);
            Sequence instanceSeq = ref.getSequence(Tag.ReferencedInstanceSequence);
            if (instanceSeq != null) {
              for (Attributes instance : instanceSeq) {
                String sopInstanceUID = instance.getString(Tag.ReferencedSOPInstanceUID);
                if (StringUtil.hasText(sopInstanceUID)) {
                  refMap.computeIfAbsent(sopInstanceUID, _ -> new LinkedHashSet<>());
                }
              }
            }
          }
        }
      }
    }
  }

  public static void addSourceImage(
      Attributes derivation, Map<String, List<Integer>> sopUIDToFramesMap) {
    if (derivation == null || sopUIDToFramesMap == null) {
      return;
    }
    Sequence srcSeq = derivation.getSequence(Tag.SourceImageSequence);
    if (srcSeq != null) {
      for (Attributes src : srcSeq) {
        SOPInstanceReference sopRef = new SOPInstanceReference(src);
        int[] frames = sopRef.getReferencedFrameNumber();
        String sopInstanceUID = src.getString(Tag.ReferencedSOPInstanceUID);

        if (sopInstanceUID != null) {
          if (frames == null || frames.length == 0) {
            // If no referenced frames are specified, assume all frames
            sopUIDToFramesMap.put(sopInstanceUID, Collections.emptyList());
          } else {
            sopUIDToFramesMap.put(sopInstanceUID, toIntegerList(frames));
          }
        }
      }
    }
  }

  private static List<Integer> toIntegerList(int[] array) {
    List<Integer> list = new ArrayList<>();
    for (int value : array) {
      list.add(value);
    }
    return list;
  }

  public static void addReferencedSOPInstanceUID(Attributes seriesRef, String originSeriesUID) {
    if (seriesRef == null || originSeriesUID == null) {
      return;
    }
    String structUID = seriesRef.getString(Tag.ReferencedSOPInstanceUID);
    if (StringUtil.hasText(structUID)) {
      HiddenSeriesManager.getInstance()
          .sopRef2Series
          .computeIfAbsent(structUID, _ -> new CopyOnWriteArraySet<>())
          .add(originSeriesUID);
    }
  }

  public static <E> boolean hasHiddenElementsFromSeries(Class<E> clazz, String... seriesUID) {
    if (clazz != null && clazz.isAssignableFrom(clazz)) {
      for (String uid : seriesUID) {
        if (hasHiddenElements(clazz, uid)) {
          return true;
        }
      }
    }
    return false;
  }

  private static <E> boolean hasHiddenElements(Class<E> clazz, String seriesUID) {
    if (StringUtil.hasText(seriesUID)) {
      Set<HiddenSpecialElement> hiddenElements = getInstance().series2Elements.get(seriesUID);
      if (hiddenElements != null) {
        for (HiddenSpecialElement media : hiddenElements) {
          if (clazz.isInstance(media)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static <E> void addHiddenElements(
      List<E> list, Class<E> clazz, String seriesUID, int childNumber) {
    if (StringUtil.hasText(seriesUID)) {
      Set<HiddenSpecialElement> hiddenElements = getInstance().series2Elements.get(seriesUID);
      if (hiddenElements != null) {
        for (HiddenSpecialElement media : hiddenElements) {
          if (clazz.isInstance(media) && !list.contains(media)) {
            list.add((E) media);
            if (childNumber > 0) {
              String sopUID = TagD.getTagValue(media, Tag.SOPInstanceUID, String.class);
              Set<String> refs = getInstance().sopRef2Series.get(sopUID);
              if (refs != null) {
                for (String ref : refs) {
                  addHiddenElements(list, clazz, ref, childNumber - 1);
                }
              }
            }
          }
        }
      }
    }
  }

  public static <E> List<E> getHiddenElementsFromSeries(Class<E> clazz, String... seriesUID) {
    if (clazz != null && clazz.isAssignableFrom(clazz)) {
      List<E> list = new ArrayList<>();
      for (String uid : seriesUID) {
        addHiddenElements(list, clazz, uid, 2);
      }
      return list;
    }
    return Collections.emptyList();
  }

  public static <E> boolean hasHiddenSpecialElements(Class<E> clazz, MediaSeriesGroup patient) {
    if (patient != null && clazz != null && clazz.isAssignableFrom(clazz)) {
      String patientPseudoUID = (String) patient.getTagValue(TagW.PatientPseudoUID);
      Set<String> patients = HiddenSeriesManager.getInstance().patient2Series.get(patientPseudoUID);
      if (patients != null) {
        for (String seriesUID : patients) {
          if (hasHiddenElements(clazz, seriesUID)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  public static <E> List<E> getHiddenElementsFromPatient(Class<E> clazz, MediaSeriesGroup patient) {
    if (patient != null) {
      String patientPseudoUID = (String) patient.getTagValue(TagW.PatientPseudoUID);
      return getHiddenElementsFromPatient(clazz, patientPseudoUID);
    }
    return Collections.emptyList();
  }

  public static <E> List<E> getHiddenElementsFromPatient(Class<E> clazz, String patientPseudoUID) {
    if (StringUtil.hasText(patientPseudoUID) && clazz != null && clazz.isAssignableFrom(clazz)) {
      Set<String> patients = HiddenSeriesManager.getInstance().patient2Series.get(patientPseudoUID);
      if (patients != null) {
        List<E> list = new ArrayList<>();
        for (String seriesUID : patients) {
          addHiddenElements(list, clazz, seriesUID, 2);
        }
        return list;
      }
    }
    return Collections.emptyList();
  }

  public static Set<ResourceIconPath> getRelatedIcons(String seriesUID) {
    if (StringUtil.hasText(seriesUID)) {
      Set<String> list = HiddenSeriesManager.getInstance().reference2Series.get(seriesUID);
      if (list != null && !list.isEmpty()) {
        Set<ResourceIconPath> icons = new LinkedHashSet<>();
        for (String uid : list) {
          Set<HiddenSpecialElement> hiddenElements = getInstance().series2Elements.get(uid);
          if (hiddenElements != null) {
            for (HiddenSpecialElement media : hiddenElements) {
              ResourceIconPath path = media.getIconPath();
              if (path != null) {
                icons.add(path);
              }
            }
          }
        }
        return icons;
      }
    }
    return Collections.emptySet();
  }
}
