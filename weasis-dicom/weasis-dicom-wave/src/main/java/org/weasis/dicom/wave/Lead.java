/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.wave;

import java.util.Objects;
import org.weasis.core.util.StringUtil;
import org.weasis.dicom.codec.macro.Code;

public class Lead {

  public static final Lead I = new Lead("I"); // NON-NLS
  public static final Lead II = new Lead("II");
  public static final Lead III = new Lead("III");
  public static final Lead AVR = new Lead("aVR");
  public static final Lead AVL = new Lead("aVL");
  public static final Lead AVF = new Lead("aVF");
  public static final Lead V1 = new Lead("V1"); // NON-NLS
  public static final Lead V2 = new Lead("V2"); // NON-NLS
  public static final Lead V3 = new Lead("V3"); // NON-NLS
  public static final Lead V4 = new Lead("V4"); // NON-NLS
  public static final Lead V5 = new Lead("V5"); // NON-NLS
  public static final Lead V6 = new Lead("V6"); // NON-NLS
  public static final Lead RHYTHM = new Lead("II (Rhythm)"); // NON-NLS

  static final Lead[] DEFAULT_12LEAD = {I, II, III, AVR, AVL, AVF, V1, V2, V3, V4, V5, V6};

  private final String name;

  private Lead(String name) {
    this.name = Objects.requireNonNull(name);
  }

  @Override
  public int hashCode() {
    return 31 + getName().hashCode();
  }

  @Override
  public String toString() {
    return name;
  }

  public String getName() {
    return name.toUpperCase();
  }

  private static String getCode(String n) {
    String[] val = n.split(" ");
    return val[val.length - 1];
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Lead other = (Lead) obj;
    return getName().equals(other.getName());
  }

  public static Lead buildLead(String title) {
    String val = getCode(title.toUpperCase());
    for (Lead lead : DEFAULT_12LEAD) {
      if (val.equals(getCode(lead.getName()))) {
        return lead;
      }
    }
    return new Lead(title);
  }

  public static Lead buildLead(Code code) {
    String title = code.getCodeMeaning();
    String codeValue = code.getExistingCodeValue();
    String schemeDesignator = code.getCodingSchemeDesignator();
    if (StringUtil.hasText(codeValue)) {
      Lead lead = null;
      if ("MDC".equals(schemeDesignator)) {
        lead = mdc2Lead(codeValue);
      } else if ("SCPECG".equals(schemeDesignator)) {
        lead = scpecg2Lead(codeValue);
      }
      if (lead != null) {
        return lead;
      }
    }
    return new Lead(title);
  }

  private static Lead mdc2Lead(String codeValue) {
    return switch (codeValue) {
      case "2:1" -> I;
      case "2:2" -> II;
      case "2:61" -> III;
      case "2:62" -> AVR;
      case "2:63" -> AVL;
      case "2:64" -> AVF;
      case "2:3" -> V1;
      case "2:4" -> V2;
      case "2:5" -> V3;
      case "2:6" -> V4;
      case "2:7" -> V5;
      case "2:8" -> V6;
      default -> null;
    };
  }

  private static Lead scpecg2Lead(String codeValue) {
    // http://dicom.nema.org/medical/Dicom/current/output/chtml/part16/sect_CID_3001.html
    return switch (codeValue) {
      case "5.6.3-9-1" -> I;
      case "5.6.3-9-2" -> II;
      case "5.6.3-9-61" -> III;
      case "5.6.3-9-62" -> AVR;
      case "5.6.3-9-63" -> AVL;
      case "5.6.3-9-64" -> AVF;
      case "5.6.3-9-3" -> V1;
      case "5.6.3-9-4" -> V2;
      case "5.6.3-9-5" -> V3;
      case "5.6.3-9-6" -> V4;
      case "5.6.3-9-7" -> V5;
      case "5.6.3-9-8" -> V6;
      default -> null;
    };
  }
}
