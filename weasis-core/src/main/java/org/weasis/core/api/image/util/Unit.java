/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import java.util.ArrayList;
import java.util.List;
import org.weasis.core.api.Messages;

public enum Unit {
  PIXEL(-5, Messages.getString("Unit.pix"), Messages.getString("Unit.pix_s"), 1.0),
  /*
   * The prefix "nano" means 1 x 10-9, so one nanometer = 0.000000001 meters. Alternatively, 1 meter contains
   * 1,000,000,000 nanometers. Visible light contains wavelengths from roughly 300 to 800 nm. To convert this
   * wavelength to energy.
   */
  NANOMETER(-2, Messages.getString("Unit.nano"), Messages.getString("Unit.nano_S"), 1.0E-09),
  /*
   * Also known a micron. The prefix "micro" means 1 x 10-6, so one micrometer = 0.000001 meters. Alternatively, 1
   * meter contains 1,000,000 micrometers. 1 micrometer = 10,000 Angstroms = 0.001 millimeters = 0.0009843 inches.
   */
  MICROMETER(-1, Messages.getString("Unit.micro"), Messages.getString("Unit.micro_S"), 1.0E-06),
  /*
   * The prefix "milli" means 1/1000, so one millimeter = 0.001 meters. Alternatively, 1 meter contains 1000
   * millimeters. 1 millimeter = 0.1 cm = 0.0394 inches.
   */
  MILLIMETER(0, Messages.getString("Unit.milli"), Messages.getString("Unit.milli_s"), 1.0E-03),
  /*
   * The prefix "centi" means 1/100, so one centimeter = 0.01 meters. Alternatively, 1 meter contains 100 cm. 1 cm =
   * 10 mm = 0.3937 inches.
   */
  CENTIMETER(1, Messages.getString("Unit.cent"), Messages.getString("Unit.cent_s"), 1.0E-02),
  /*
   * Defined as the length of 1,650,763.73 wavelengths of the orange-red radiation of 86Kr in a vacuum, one meter
   * contains 100 cm = 1000 mm. There are 1000 m in a kilometer. One meter = 39.37 inches = 3.28 feet = 1.09 yards.
   */
  METER(2, Messages.getString("Unit.meter"), Messages.getString("Unit.meter_s"), 1.0),
  /*
   * The prefix "kilo" means 1000, so one kilometer = 1,000 meters. 1 kilometer = 3,280.84 feet = 1,093.6 yards =
   * 0.6214 miles.
   */
  KILOMETER(3, Messages.getString("Unit.kilo"), Messages.getString("Unit.kilo_s"), 1.0E+03),

  // micro and mili iches are not official units because micro and mili comes from metric unit
  MICROINCH(9, Messages.getString("Unit.minch"), Messages.getString("Unit.minch_s"), 2.54E-08),

  MILLIINCH(10, Messages.getString("Unit.mil_inch"), Messages.getString("Unit.mil"), 2.54E-05),

  INCH(11, Messages.getString("Unit.inch"), Messages.getString("Unit.inch_s"), 2.54E-02),

  FEET(12, Messages.getString("Unit.feet"), Messages.getString("Unit.feet_s"), 3.048E-01),

  YARD(13, Messages.getString("Unit.yard"), Messages.getString("Unit.yard_s"), 9.144E-01),

  MILE(14, Messages.getString("Unit.mile"), Messages.getString("Unit.mile_s"), 1.609344E+03);

  private final int id;
  private final String fullName;
  private final String abbreviation;
  private final double convFactor;

  Unit(int id, String fullName, String abbreviation, double convFactor) {
    this.id = id;
    this.fullName = fullName;
    this.abbreviation = abbreviation;
    this.convFactor = convFactor;
  }

  public int getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public double getConvFactor() {
    return convFactor;
  }

  public String getAbbreviation() {
    return abbreviation;
  }

  @Override
  public String toString() {
    return fullName;
  }

  public double getConversionRatio(double calibRatio) {
    return calibRatio / convFactor;
  }

  public static List<AbbreviationUnit> getAbbreviationUnits() {
    ArrayList<AbbreviationUnit> units = new ArrayList<>();
    for (Unit u : Unit.values()) {
      if (u.getId() != Unit.PIXEL.getId()) {
        units.add(new AbbreviationUnit(u));
      }
    }
    return units;
  }

  public static Unit getCurrentIdUnit(int id) {
    for (Unit u : Unit.values()) {
      if (id == u.getId()) {
        return u;
      }
    }
    return Unit.PIXEL;
  }

  public static List<String> getUnitsName() {
    ArrayList<String> list = new ArrayList<>();
    for (Unit u : Unit.values()) {
      list.add(u.getFullName());
    }
    return list;
  }

  public Unit getUpUnit() {
    for (Unit u : Unit.values()) {
      if (u.getId() - getId() == 1) {
        return u;
      }
    }
    return null;
  }

  public Unit getDownUnit() {
    for (Unit u : Unit.values()) {
      if (getId() - u.getId() == 1) {
        return u;
      }
    }
    return null;
  }

  public static List<Unit> getUnitExceptPixel() {
    ArrayList<Unit> list = new ArrayList<>();
    for (Unit u : Unit.values()) {
      if (u.getId() != Unit.PIXEL.getId()) {
        list.add(u);
      }
    }
    return list;
  }
}
