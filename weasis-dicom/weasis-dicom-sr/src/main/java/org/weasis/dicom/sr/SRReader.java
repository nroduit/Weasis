/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.sr;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Sequence;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.img.util.DicomUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.weasis.core.api.media.data.IdentityMask;
import org.weasis.core.api.media.data.MediaElement;
import org.weasis.core.api.media.data.Series;
import org.weasis.core.api.media.data.TagUtil;
import org.weasis.core.api.media.data.TagW;
import org.weasis.core.util.EscapeChars;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.DicomSpecialElement;
import org.weasis.dicom.codec.TagD;
import org.weasis.dicom.codec.WaveformAnnotation;
import org.weasis.dicom.macro.Code;
import org.weasis.dicom.macro.SOPInstanceReference;
import org.weasis.dicom.macro.SeriesAndInstanceReference;

/**
 * Renders a DICOM Structured Report (PS3.3 C.17) as HTML for {@link SRView}.
 *
 * <p>The content tree is first built in memory so that every content item gets its positional
 * identifier (root is {@code 1}, children are numbered from 1 in sequence order, PS3.3 C.17.3.2.5).
 * By-reference items and the SELECTED FROM targets of SCOORD items are resolved through that index,
 * whether the target appears before or after the referencing item.
 *
 * <p>Two kinds of hyperlinks are emitted:
 *
 * <ul>
 *   <li>{@code #<id>}: jumps to the anchor of content item {@code <id>} inside the document.
 *   <li>{@value #LINK_PREFIX}{@code <key>}: opens the referenced SOP instance registered under
 *       {@code <key>} in the {@link SRImageReference} map given to {@link
 *       #readDocumentGeneralModule(StringBuilder, Map)}.
 * </ul>
 */
public class SRReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(SRReader.class);

  /** Prefix of the hyperlinks that open a referenced object (image, waveform, composite). */
  public static final String LINK_PREFIX = "weasis-sr:"; // NON-NLS

  static final String CONTAINER = "CONTAINER"; // NON-NLS
  static final String IMAGE = "IMAGE"; // NON-NLS
  static final String SCOORD = "SCOORD"; // NON-NLS
  static final String SCOORD3D = "SCOORD3D"; // NON-NLS
  static final String WAVEFORM = "WAVEFORM"; // NON-NLS

  private final DicomSpecialElement dicomSR;
  private final Attributes dcmItems;

  public SRReader(Series series, DicomSpecialElement dicomSR) {
    if (dicomSR == null) {
      throw new IllegalArgumentException("Dicom parameter cannot be null");
    }
    this.dicomSR = dicomSR;
    this.dcmItems = dicomSR.getMediaReader().getDicomObject();
  }

  /**
   * Builds a reader on raw attributes.
   *
   * @param dicomSR the special element, used for the time zone of DICOM times; may be null
   * @param attributes the SR dataset, never null
   */
  public SRReader(DicomSpecialElement dicomSR, Attributes attributes) {
    this.dicomSR = dicomSR;
    this.dcmItems = Objects.requireNonNull(attributes);
  }

  public MediaElement getDicom() {
    return dicomSR;
  }

  public Attributes getAttributes() {
    return dcmItems;
  }

  public SeriesAndInstanceReference getSeriesAndInstanceReference() {
    if (dcmItems != null) {
      return new SeriesAndInstanceReference(dcmItems);
    }
    return null;
  }

  /**
   * Renders the whole document.
   *
   * @param html receives the HTML
   * @param map receives the link targets, keyed by the {@code <key>} part of the hyperlinks
   */
  public void readDocumentGeneralModule(StringBuilder html, Map<String, SRImageReference> map) {
    if (dcmItems == null) {
      return;
    }
    SRDocumentContentModule content = new SRDocumentContentModule(dcmItems);
    addCodeMeaning(html, content.getConceptNameCode(), "<h1>", "</h1>"); // NON-NLS
    writeDocumentOrigin(html);
    writeDocumentHeader(html, content);
    html.append("<hr size=2>"); // NON-NLS

    ContentNode root = buildTree(dcmItems);
    Map<String, ContentNode> index = new HashMap<>();
    indexTree(root, index);
    for (ContentNode child : root.children) {
      html.append("<BR>");
      writeAnchor(html, child.id);
      html.append("<B>").append(child.id).append(" </B>"); // NON-NLS
      Code code = child.content.getConceptNameCode();
      addCodeMeaning(html, code, "<B>", "</B>"); // NON-NLS
      writeValue(html, child, false, code == null, index, map);
      html.append("<BR>");
      writeChildren(html, child, index, map);
    }
  }

  // ================================================================================
  // Document header
  // ================================================================================

  private void writeDocumentOrigin(StringBuilder html) {
    String instName = dcmItems.getString(Tag.InstitutionName);
    String instDepName = dcmItems.getString(Tag.InstitutionalDepartmentName);
    String stationName = dcmItems.getString(Tag.StationName);
    LocalDateTime contentDateTime = TagD.dateTime(Tag.ContentDateAndTime, dcmItems);
    if (instName != null) {
      html.append(Messages.getString("SRReader.by"));
      html.append(" ");
      html.append(EscapeChars.forHTML(instName));
      if (instDepName != null) {
        html.append(" (");
        html.append(EscapeChars.forHTML(instDepName));
        html.append(")");
      }
    }
    if (stationName != null) {
      if (instName != null) {
        html.append(" ");
        html.append(Messages.getString("SRReader.on"));
        html.append(" ");
      }
      html.append(EscapeChars.forHTML(stationName));
    }
    if (contentDateTime != null) {
      if (instName != null || stationName != null) {
        html.append(", ");
      }
      html.append(TagUtil.formatDateTime(contentDateTime));
    }
    if (instName != null || stationName != null || contentDateTime != null) {
      html.append("<BR>");
    }
  }

  private void writeDocumentHeader(StringBuilder html, SRDocumentContentModule content) {
    html.append("<table border=\"0\" width=\"100%\" cellspacing=\"5\">"); // NON-NLS

    html.append("<tr align=\"left\" valign=\"top\"><td width=\"33%\" >"); // NON-NLS
    html.append("<font size=\"+1\">"); // NON-NLS
    html.append(Messages.getString("SRReader.patient"));
    html.append("</font>"); // NON-NLS
    html.append("<BR>");
    writeItem(Tag.PatientName, html);
    html.append("<BR>");
    writeItem(Tag.PatientID, html);
    html.append("<BR>");
    writeItem(Tag.PatientBirthDate, html);
    html.append("<BR>");
    writeItem(Tag.PatientSex, html);

    html.append("</td><td width=\"33%\" >"); // NON-NLS
    html.append("<font size=\"+1\">"); // NON-NLS
    html.append(Messages.getString("SRReader.study"));
    html.append("</font>"); // NON-NLS
    html.append("<BR>");
    writeStudyDateTime(html);
    html.append("<BR>");
    writeItem(Tag.StudyID, html);
    html.append("<BR>");
    writeItem(Tag.AccessionNumber, html);
    html.append("<BR>");
    writeItem(Tag.ReferringPhysicianName, html);

    html.append("</td><td width=\"33%\" >"); // NON-NLS
    html.append("<font size=\"+1\">"); // NON-NLS
    html.append(Messages.getString("SRReader.report_status"));
    html.append("</font><BR>"); // NON-NLS
    writeItem(Tag.CompletionFlag, html);
    html.append("<BR>");
    writeItem(Tag.VerificationFlag, html);
    html.append("<BR>");
    if (dcmItems.containsValue(Tag.PreliminaryFlag)) {
      writeItem(Tag.PreliminaryFlag, html);
      html.append("<BR>");
    }
    writeTemplate(html, content.getContentTemplate());
    writeVerifyingObservers(html);
    html.append("</td></tr>"); // NON-NLS

    html.append("</table>"); // NON-NLS
  }

  private static void writeTemplate(StringBuilder html, Attributes template) {
    if (template != null) {
      String tid = template.getString(Tag.TemplateIdentifier);
      if (StringUtil.hasText(tid)) {
        html.append("<B>");
        html.append(Messages.getString("SRReader.template"));
        html.append("</B>");
        html.append(StringUtil.COLON_AND_SPACE);
        String resource = template.getString(Tag.MappingResource);
        if (StringUtil.hasText(resource)) {
          html.append(EscapeChars.forHTML(resource)).append(" ");
        }
        html.append(EscapeChars.forHTML(tid));
        html.append("<BR>");
      }
    }
  }

  private void writeItem(int tagID, StringBuilder html) {
    TagW tag = TagD.getNullable(tagID, null);
    if (tag != null && html != null && dcmItems != null) {
      html.append("<B>");
      html.append(tag.getDisplayedName());
      html.append("</B>");
      html.append(StringUtil.COLON_AND_SPACE);
      String format = dicomSR == null ? null : tag.addGMTOffset(null, dicomSR);
      html.append(
          EscapeChars.forHTML(
              tag.getFormattedTagValue(
                  IdentityMask.maskValue(tag, tag.getValue(dcmItems)), format)));
    }
  }

  private void writeStudyDateTime(StringBuilder html) {
    TagW tagDate = TagD.getNullable(Tag.StudyDate, null);
    if (tagDate != null && html != null && dcmItems != null) {
      LocalDateTime date = TagD.dateTime(Tag.StudyDateAndTime, dcmItems);
      if (date != null) {
        html.append("<B>");
        html.append(tagDate.getDisplayedName());
        html.append("</B>");
        html.append(StringUtil.COLON_AND_SPACE);
        html.append(TagUtil.formatDateTime(date));
      }
    }
  }

  private void writeVerifyingObservers(StringBuilder html) {
    if (html != null && dcmItems != null) {
      Sequence seq = dcmItems.getSequence(Tag.VerifyingObserverSequence);
      if (seq != null && !seq.isEmpty()) {
        html.append("<B>");
        html.append(Messages.getString("SRReader.ver_observer"));
        html.append("</B>");
        html.append(StringUtil.COLON);
        html.append("<BR>");
        for (Attributes v : seq) {
          TemporalAccessor date = (TemporalAccessor) TagD.get(Tag.VerificationDateTime).getValue(v);
          if (date != null) {
            html.append(" * ");
            html.append(TagUtil.formatDateTime(date));
            html.append(" - ");
            String name = TagD.getDicomPersonName(v.getString(Tag.VerifyingObserverName));
            if (name != null) {
              html.append(EscapeChars.forHTML(name));
              html.append(", ");
            }
            String org = v.getString(Tag.VerifyingOrganization);
            if (org != null) {
              html.append(EscapeChars.forHTML(org));
            }
            html.append("<BR>");
          }
        }
      }
    }
  }

  // ================================================================================
  // Content tree
  // ================================================================================

  /** A content item with its positional identifier, e.g. "1.4.1" (PS3.3 C.17.3.2.5). */
  static final class ContentNode {
    final SRDocumentContent content;
    final String id;
    final ContentNode parent;
    final List<ContentNode> children = new ArrayList<>();

    ContentNode(SRDocumentContent content, String id, ContentNode parent) {
      this.content = content;
      this.id = id;
      this.parent = parent;
    }

    boolean isValueType(String type) {
      return type.equals(content.getValueType());
    }
  }

  /** Builds the tree of the dataset: the root content item is node "1". */
  static ContentNode buildTree(Attributes root) {
    ContentNode rootNode = new ContentNode(new SRDocumentContent(root), "1", null);
    addChildren(rootNode);
    return rootNode;
  }

  private static void addChildren(ContentNode node) {
    Sequence cts = node.content.getContent();
    if (cts != null) {
      for (int i = 0; i < cts.size(); i++) {
        ContentNode child =
            new ContentNode(new SRDocumentContent(cts.get(i)), node.id + "." + (i + 1), node);
        node.children.add(child);
        addChildren(child);
      }
    }
  }

  static void indexTree(ContentNode node, Map<String, ContentNode> index) {
    index.put(node.id, node);
    for (ContentNode child : node.children) {
      indexTree(child, index);
    }
  }

  /** Follows a by-reference item to its target; a by-value item resolves to itself. */
  private static ContentNode resolveTarget(ContentNode node, Map<String, ContentNode> index) {
    if (node.content.isByReference()) {
      ContentNode target = index.get(node.content.getReferencedContentItemNodeId());
      if (target == null) {
        LOGGER.debug(
            "SR content item {} references the missing node {}",
            node.id,
            node.content.getReferencedContentItemNodeId());
      }
      return target;
    }
    return node;
  }

  private static boolean isReferencedImage(ContentNode node) {
    return isReferencedObject(node, IMAGE);
  }

  private static boolean isReferencedObject(ContentNode node, String valueType) {
    return node != null
        && node.isValueType(valueType)
        && node.content.getReferencedSOPInstance() != null;
  }

  /**
   * Finds the IMAGE items an SCOORD applies to. The standard requires one or more SELECTED FROM
   * children of value type IMAGE, given by value or by reference. Two lenient fallbacks cover
   * common non-conformant documents: a Referenced Content Item Identifier set directly on the
   * SCOORD item, and an SCOORD nested under the IMAGE item it belongs to.
   */
  static List<ContentNode> resolveImageTargets(ContentNode scoord, Map<String, ContentNode> index) {
    return resolveTargets(scoord, index, IMAGE);
  }

  /** Same as {@link #resolveImageTargets} for any referencing value type (IMAGE, WAVEFORM). */
  static List<ContentNode> resolveTargets(
      ContentNode source, Map<String, ContentNode> index, String valueType) {
    List<ContentNode> targets = new ArrayList<>();
    for (ContentNode child : source.children) {
      ContentNode target = resolveTarget(child, index);
      if (isReferencedObject(target, valueType) && !targets.contains(target)) {
        targets.add(target);
      }
    }
    if (targets.isEmpty()) {
      String refId = source.content.getReferencedContentItemNodeId();
      if (refId != null) {
        ContentNode target = index.get(refId);
        if (isReferencedObject(target, valueType)) {
          targets.add(target);
        }
      }
    }
    if (targets.isEmpty()) {
      for (ContentNode p = source.parent; p != null; p = p.parent) {
        if (isReferencedObject(p, valueType)) {
          targets.add(p);
          break;
        }
      }
    }
    if (targets.isEmpty()) {
      LOGGER.debug("SR item {} has no resolvable {} reference", source.id, valueType);
    }
    return targets;
  }

  /**
   * Text identifying a region on the image: the Tracking Identifier of the enclosing measurement
   * group, else the finding the region belongs to, else the region's own concept name.
   */
  static String graphicLabel(ContentNode node) {
    for (ContentNode n = node.parent; n != null; n = n.parent) {
      for (ContentNode child : n.children) {
        if (child.isValueType("TEXT") && hasConcept(child, "112039")) { // NON-NLS
          String id = child.content.getTextValue();
          if (StringUtil.hasText(id)) {
            return id.trim();
          }
        }
      }
      if (n.isValueType("CODE") // NON-NLS
          && (hasConcept(n, "121071") || hasConcept(n, "111059"))) { // NON-NLS
        Code finding = n.content.getConceptCode();
        if (finding != null && StringUtil.hasText(finding.getCodeMeaning())) {
          return finding.getCodeMeaning();
        }
      }
      for (ContentNode child : n.children) {
        if (child.isValueType("CODE") && hasConcept(child, "121071")) { // NON-NLS
          Code finding = child.content.getConceptCode();
          if (finding != null && StringUtil.hasText(finding.getCodeMeaning())) {
            return finding.getCodeMeaning();
          }
        }
      }
    }
    Code name = node.content.getConceptNameCode();
    return name == null ? null : name.getCodeMeaning();
  }

  private static boolean hasConcept(ContentNode node, String dcmCodeValue) {
    Code name = node.content.getConceptNameCode();
    return name != null
        && dcmCodeValue.equals(name.getCodeValue())
        && "DCM".equals(name.getCodingSchemeDesignator()); // NON-NLS
  }

  // ================================================================================
  // Content rendering
  // ================================================================================

  private static void writeChildren(
      StringBuilder html,
      ContentNode node,
      Map<String, ContentNode> index,
      Map<String, SRImageReference> map) {
    if (node.children.isEmpty()) {
      return;
    }
    boolean continuity = "CONTINUOUS".equals(node.content.getContinuityOfContent()); // NON-NLS
    if (!continuity) {
      html.append("<OL>");
    }
    for (ContentNode child : node.children) {
      html.append(continuity ? " " : "<LI>");
      writeAnchor(html, child.id);
      Code code = null;
      if (!continuity) {
        code = child.content.getConceptNameCode();
        addCodeMeaning(html, code, "<B>", "</B>");
      }
      writeValue(html, child, continuity, code == null, index, map);
      writeChildren(html, child, index, map);
      html.append(continuity ? " " : "</LI>");
    }
    if (!continuity) {
      html.append("</OL>");
    }
  }

  private static void writeAnchor(StringBuilder html, String id) {
    html.append("<a name=\"").append(id).append("\"></a>"); // NON-NLS
  }

  private static String separator(boolean continuous, boolean noCodeName) {
    return continuous || noCodeName ? " " : StringUtil.COLON_AND_SPACE;
  }

  private static void writeValue(
      StringBuilder html,
      ContentNode node,
      boolean continuous,
      boolean noCodeName,
      Map<String, ContentNode> index,
      Map<String, SRImageReference> map) {
    SRDocumentContent c = node.content;
    String type = c.getValueType();
    if (type == null) {
      if (c.isByReference()) {
        writeByReference(html, node, index);
      }
      return;
    }
    if (c.getReferencedContentItemIdentifier() != null) {
      // Not allowed by PS3.3 C.17.3.2.5 on a by-value item: keep the value, ignore the reference
      LOGGER.debug(
          "SR content item {} ({}) carries a Referenced Content Item Identifier", node.id, type);
    }
    String sep = separator(continuous, noCodeName);

    switch (type) {
      case "TEXT" -> { // NON-NLS
        html.append(sep);
        convertTextToHTML(html, c.getTextValue());
      }
      case "CODE" -> { // NON-NLS
        html.append(sep);
        addCodeMeaning(html, c.getConceptCode(), null, null);
      }
      case "PNAME" -> { // NON-NLS
        html.append(sep);
        convertTextToHTML(html, TagD.getDicomPersonName(c.getPersonName()));
      }
      case "NUM" -> { // NON-NLS
        html.append(continuous || noCodeName ? " " : " = ");
        writeNumericValue(html, c);
      }
      case CONTAINER -> {
        // Children are written by writeChildren()
      }
      case IMAGE -> writeImage(html, node, sep, map);
      case "COMPOSITE" -> writeComposite(html, node, sep, map, false); // NON-NLS
      case WAVEFORM -> writeComposite(html, node, sep, map, true);
      case SCOORD -> writeScoord(html, node, sep, index, map, false);
      case SCOORD3D -> writeScoord(html, node, sep, index, map, true);
      case "TCOORD" -> writeTcoord(html, node, sep, index, map); // NON-NLS
      case "DATETIME" -> { // NON-NLS
        html.append(sep);
        writeTemporal(html, TagD.getDicomDateTime(c.getDateTimeString()), c.getDateTimeString());
      }
      case "DATE" -> { // NON-NLS
        html.append(sep);
        writeTemporal(html, TagD.getDicomDate(c.getDateString()), c.getDateString());
      }
      case "TIME" -> { // NON-NLS
        html.append(sep);
        writeTemporal(html, TagD.getDicomTime(c.getTimeString()), c.getTimeString());
      }
      case "UIDREF" -> { // NON-NLS
        html.append(sep);
        convertTextToHTML(html, c.getUID());
      }
      default -> {
        html.append(sep);
        html.append("<i>");
        html.append(EscapeChars.forHTML(type));
        html.append(" ");
        html.append(Messages.getString("SRReader.tag_missing"));
        html.append("</i>");
      }
    }
  }

  private static void writeByReference(
      StringBuilder html, ContentNode node, Map<String, ContentNode> index) {
    String refId = node.content.getReferencedContentItemNodeId();
    ContentNode target = resolveTarget(node, index);
    html.append(" <i>");
    String relationship = node.content.getRelationshipType();
    if (StringUtil.hasText(relationship)) {
      html.append(EscapeChars.forHTML(relationship)).append(" ");
    }
    html.append(Messages.getString("SRReader.content_ref")).append("</i>");
    html.append(StringUtil.COLON_AND_SPACE);
    if (target == null) {
      html.append(Messages.getString("SRReader.node")).append(" ").append(refId);
      html.append(" (").append(Messages.getString("SRReader.ref_missing")).append(")");
    } else {
      html.append("<a href=\"#").append(refId).append("\">"); // NON-NLS
      html.append(Messages.getString("SRReader.node")).append(" ").append(refId);
      html.append("</a>"); // NON-NLS
      Code code = target.content.getConceptNameCode();
      if (code != null) {
        html.append(" ");
        addCodeMeaning(html, code, "<B>", "</B>");
      }
    }
  }

  private static void writeNumericValue(StringBuilder html, SRDocumentContent c) {
    boolean hasValue = false;
    Attributes val = c.getMeasuredValue();
    if (val != null) {
      String[] values = val.getStrings(Tag.NumericValue);
      if (values == null || values.length == 0) {
        // Floating Point Value (0040,A161) carries values that DS cannot represent
        double[] fp = val.getDoubles(Tag.FloatingPointValue);
        if (fp != null && fp.length > 0) {
          values = DoubleStream.of(fp).mapToObj(String::valueOf).toArray(String[]::new);
        }
      }
      if (values != null && values.length > 0) {
        html.append(
            EscapeChars.forHTML(
                Arrays.stream(values).map(String::trim).collect(Collectors.joining(", "))));
        hasValue = true;
        Code unit = Code.getNestedCode(val, Tag.MeasurementUnitsCodeSequence);
        // UCUM "1" means "no units"
        if (unit != null && !"1".equals(unit.getCodeValue())) {
          html.append(" ");
          String meaning = unit.getCodeMeaning();
          html.append(
              EscapeChars.forHTML(StringUtil.hasText(meaning) ? meaning : unit.getCodeValue()));
        }
      }
    }
    Code qualifier = c.getNumericValueQualifierCode();
    if (qualifier != null) {
      if (hasValue) {
        html.append(" ");
      }
      addCodeMeaning(html, qualifier, "<i>", "</i>");
    }
  }

  private static void writeTemporal(StringBuilder html, TemporalAccessor value, String raw) {
    String text = value == null ? null : TagUtil.formatDateTime(value);
    if (!StringUtil.hasText(text)) {
      text = raw;
    }
    if (text != null) {
      html.append(EscapeChars.forHTML(text));
    }
  }

  private static void writeImage(
      StringBuilder html, ContentNode node, String sep, Map<String, SRImageReference> map) {
    SOPInstanceReference sop = node.content.getReferencedSOPInstance();
    if (sop == null) {
      return;
    }
    html.append(sep);
    registerReference(map, node.id, sop);
    appendLink(html, node.id, Messages.getString("SRReader.show_img"));
    int[] frames = sop.getReferencedFrameNumber();
    if (frames != null && frames.length > 0) {
      html.append(" (").append(Messages.getString("SRReader.frame")).append(" ");
      html.append(IntStream.of(frames).mapToObj(String::valueOf).collect(Collectors.joining(", ")));
      html.append(")");
    }
  }

  private static void writeComposite(
      StringBuilder html,
      ContentNode node,
      String sep,
      Map<String, SRImageReference> map,
      boolean waveform) {
    Sequence seq = node.content.getReferencedSOPSequence();
    if (seq == null || seq.isEmpty()) {
      return;
    }
    html.append(sep);
    int n = 0;
    for (Attributes item : seq) {
      if (n > 0) {
        html.append("<BR>");
      }
      SOPInstanceReference sop = new SOPInstanceReference(item);
      String key = linkKey(node.id, n);
      registerReference(map, key, sop);
      html.append(EscapeChars.forHTML(sopClassName(sop.getReferencedSOPClassUID())));
      html.append(" ");
      appendLink(
          html, key, Messages.getString(waveform ? "SRReader.show_wave" : "SRReader.show_obj"));
      if (waveform) {
        int[] channels = item.getInts(Tag.ReferencedWaveformChannels);
        if (channels != null && channels.length > 1) {
          html.append(" (").append(Messages.getString("SRReader.channels")).append(" ");
          List<String> pairs = new ArrayList<>();
          for (int i = 0; i + 1 < channels.length; i += 2) {
            pairs.add(channels[i] + "/" + channels[i + 1]);
          }
          html.append(String.join(", ", pairs)).append(")");
        }
      }
      String uid = sop.getReferencedSOPInstanceUID();
      if (StringUtil.hasText(uid)) {
        html.append(" <font size=\"-2\">").append(uid).append("</font>"); // NON-NLS
      }
      n++;
    }
  }

  /**
   * Writes an SCOORD or SCOORD3D item as one link per image it applies to. An SCOORD3D item that
   * references no image is linked to its Frame of Reference instead, so the viewer can locate the
   * slices itself.
   */
  private static void writeScoord(
      StringBuilder html,
      ContentNode node,
      String sep,
      Map<String, ContentNode> index,
      Map<String, SRImageReference> map,
      boolean threeD) {
    SRDocumentContent c = node.content;
    String graphicType = c.getGraphicType();
    String label = StringUtil.hasText(graphicType) ? graphicType : (threeD ? SCOORD3D : SCOORD);
    html.append(sep);
    SRGraphic graphic =
        new SRGraphic(node.id, c.getAttributes(), threeD, renderingIntent(node));
    String forUID = threeD ? c.getReferencedFrameOfReferenceUID() : null;

    List<ContentNode> targets = resolveImageTargets(node, index);
    if (!targets.isEmpty()) {
      int n = 0;
      for (ContentNode target : targets) {
        if (n > 0) {
          html.append(", ");
        }
        String key = linkKey(node.id, n);
        SRImageReference ref =
            registerReference(map, key, target.content.getReferencedSOPInstance());
        ref.setFrameOfReferenceUID(forUID);
        ref.addGraphic(graphic);
        appendLink(html, key, label);
        n++;
      }
    } else if (StringUtil.hasText(forUID)) {
      SRImageReference ref = registerReference(map, node.id, null);
      ref.setFrameOfReferenceUID(forUID);
      ref.addGraphic(graphic);
      appendLink(html, node.id, label);
    } else {
      html.append(EscapeChars.forHTML(label));
      html.append(" <i>(").append(Messages.getString("SRReader.no_img_ref")).append(")</i>");
    }

    if (threeD) {
      float[] data = c.getGraphicData();
      int points = data == null ? 0 : data.length / 3;
      html.append(" <i>3D, ").append(points).append(" "); // NON-NLS
      html.append(Messages.getString("SRReader.points")).append("</i>");
    }
    if (graphic.intent() == SRGraphic.RenderingIntent.PRESENTATION_OPTIONAL) {
      html.append(" <i>(").append(Messages.getString("SRReader.intent_optional")).append(")</i>");
    } else if (graphic.intent() == SRGraphic.RenderingIntent.NOT_FOR_PRESENTATION) {
      html.append(" <i>(").append(Messages.getString("SRReader.intent_hidden")).append(")</i>");
    }
  }

  /**
   * The Rendering Intent (111056, DCM) governing a graphic item: the value of the nearest such
   * concept modifier on the item itself or on one of its ancestors, as CAD reports attach it to the
   * finding that owns the regions (TID 4006, TID 4104). Without one the item is to be presented.
   */
  static SRGraphic.RenderingIntent renderingIntent(ContentNode node) {
    for (ContentNode n = node; n != null; n = n.parent) {
      for (ContentNode child : n.children) {
        SRDocumentContent c = child.content;
        if ("HAS CONCEPT MOD".equals(c.getRelationshipType()) // NON-NLS
            && "CODE".equals(c.getValueType())) { // NON-NLS
          Code name = c.getConceptNameCode();
          if (name != null
              && "111056".equals(name.getCodeValue())
              && "DCM".equals(name.getCodingSchemeDesignator())) { // NON-NLS
            SRGraphic.RenderingIntent intent =
                SRGraphic.RenderingIntent.fromCode(c.getConceptCode());
            if (intent != null) {
              return intent;
            }
          }
        }
      }
    }
    return SRGraphic.RenderingIntent.PRESENTATION_REQUIRED;
  }

  private static void writeTcoord(
      StringBuilder html,
      ContentNode node,
      String sep,
      Map<String, ContentNode> index,
      Map<String, SRImageReference> map) {
    SRDocumentContent c = node.content;
    html.append(sep);
    String range = c.getTemporalRangeType();
    String label = StringUtil.hasText(range) ? range : "TCOORD"; // NON-NLS
    List<ContentNode> targets = resolveTargets(node, index, WAVEFORM);
    if (targets.isEmpty()) {
      html.append(EscapeChars.forHTML(label));
    } else {
      int n = 0;
      for (ContentNode target : targets) {
        Sequence seq = target.content.getReferencedSOPSequence();
        if (seq == null) {
          continue;
        }
        for (Attributes item : seq) {
          if (n > 0) {
            html.append(", ");
          }
          String key = linkKey(node.id, n);
          SRImageReference ref = registerReference(map, key, new SOPInstanceReference(item));
          ref.addAnnotation(
              new WaveformAnnotation(
                  annotationLabel(node),
                  label,
                  c.getReferencedSamplePositions(),
                  c.getReferencedTimeOffsets(),
                  c.getReferencedDateTime(),
                  item.getInts(Tag.ReferencedWaveformChannels)));
          appendLink(html, key, label);
          n++;
        }
      }
    }
    int[] samples = c.getReferencedSamplePositions();
    double[] offsets = c.getReferencedTimeOffsets();
    String[] dateTimes = c.getReferencedDateTime();
    if (samples != null && samples.length > 0) {
      html.append(StringUtil.COLON_AND_SPACE);
      html.append(
          IntStream.of(samples).mapToObj(String::valueOf).collect(Collectors.joining(", ")));
      html.append(" ").append(Messages.getString("SRReader.samples"));
    } else if (offsets != null && offsets.length > 0) {
      html.append(StringUtil.COLON_AND_SPACE);
      html.append(
          DoubleStream.of(offsets).mapToObj(String::valueOf).collect(Collectors.joining(", ")));
      html.append(" s"); // NON-NLS
    } else if (dateTimes != null && dateTimes.length > 0) {
      html.append(StringUtil.COLON_AND_SPACE);
      for (int i = 0; i < dateTimes.length; i++) {
        if (i > 0) {
          html.append(", ");
        }
        writeTemporal(html, TagD.getDicomDateTime(dateTimes[i]), dateTimes[i]);
      }
    }
  }

  /** The text of a waveform annotation: the measurement it belongs to, else its concept name. */
  private static String annotationLabel(ContentNode node) {
    ContentNode parent = node.parent;
    if (parent != null && parent.isValueType("NUM")) { // NON-NLS
      StringBuilder text = new StringBuilder();
      Code name = parent.content.getConceptNameCode();
      if (name != null && StringUtil.hasText(name.getCodeMeaning())) {
        text.append(name.getCodeMeaning());
      }
      StringBuilder value = new StringBuilder();
      writeNumericValue(value, parent.content);
      if (!value.isEmpty()) {
        text.append(text.isEmpty() ? "" : " = ").append(value.toString().replaceAll("<[^>]+>", ""));
      }
      if (!text.isEmpty()) {
        return text.toString();
      }
    }
    Code name = node.content.getConceptNameCode();
    if (name != null && StringUtil.hasText(name.getCodeMeaning())) {
      return name.getCodeMeaning();
    }
    return node.id;
  }

  // ================================================================================
  // Helpers
  // ================================================================================

  /** Key of the n-th link of a content item: the node id, then "id/1", "id/2"... */
  static String linkKey(String nodeId, int n) {
    return n == 0 ? nodeId : nodeId + "/" + n;
  }

  private static SRImageReference registerReference(
      Map<String, SRImageReference> map, String key, SOPInstanceReference sop) {
    SRImageReference ref = map.computeIfAbsent(key, SRImageReference::new);
    if (sop != null) {
      ref.setSopInstanceReference(sop);
    }
    return ref;
  }

  private static void appendLink(StringBuilder html, String key, String text) {
    html.append("<a href=\"").append(LINK_PREFIX).append(key).append("\">"); // NON-NLS
    html.append(EscapeChars.forHTML(text));
    html.append("</a>"); // NON-NLS
  }

  static String sopClassName(String sopClassUID) {
    if (!StringUtil.hasText(sopClassUID)) {
      return "";
    }
    String name = UID.nameOf(sopClassUID);
    return StringUtil.hasText(name) && !"?".equals(name) ? name : sopClassUID;
  }

  private static void addCodeMeaning(
      StringBuilder html, Code code, String startTag, String endTag) {
    if (code != null) {
      String meaning = code.getCodeMeaning();
      if (!StringUtil.hasText(meaning)) {
        meaning = code.getCodeValue();
      }
      if (meaning == null) {
        return;
      }
      if (startTag != null) {
        html.append(startTag);
      }
      html.append(EscapeChars.forHTML(meaning));
      if (endTag != null) {
        html.append(endTag);
      }
    }
  }

  private static void convertTextToHTML(StringBuilder html, String text) {
    if (text != null) {
      String[] lines = EscapeChars.convertToLines(text);
      if (lines.length > 0) {
        html.append(EscapeChars.forHTML(lines[0]));
        for (int i = 1; i < lines.length; i++) {
          html.append("<BR>");
          html.append(EscapeChars.forHTML(lines[i]));
        }
      }
    }
  }
}
