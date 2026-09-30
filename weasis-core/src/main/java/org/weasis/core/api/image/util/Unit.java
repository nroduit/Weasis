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

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.weasis.core.Messages;

/** Units are organized by measurement system and provide conversion utilities. */
public enum Unit {

  // === Special Units ===
  PIXEL(-5, "Unit.pix", "Unit.pix_s", 1.0, UnitSystem.DIGITAL, "{pixel}", "{pixel2}"),

  // === Metric Units (SI) ===
  NANOMETER(-2, "Unit.nano", "Unit.nano_S", 1.0E-09, UnitSystem.METRIC, "nm", "nm2"),
  MICROMETER(-1, "Unit.micro", "Unit.micro_S", 1.0E-06, UnitSystem.METRIC, "um", "um2"),
  MILLIMETER(0, "Unit.milli", "Unit.milli_s", 1.0E-03, UnitSystem.METRIC, "mm", "mm2"),
  CENTIMETER(1, "Unit.cent", "Unit.cent_s", 1.0E-02, UnitSystem.METRIC, "cm", "cm2"),
  METER(2, "Unit.meter", "Unit.meter_s", 1.0, UnitSystem.METRIC, "m", "m2"),
  KILOMETER(3, "Unit.kilo", "Unit.kilo_s", 1.0E+03, UnitSystem.METRIC, "km", "km2"),

  // === Imperial Units ===
  MICROINCH(
      9,
      "Unit.minch",
      "Unit.minch_s",
      2.54E-08,
      UnitSystem.IMPERIAL,
      "10*-6.[in_i]",
      "10*-12.[in_i]2"),
  MILLIINCH(10, "Unit.mil_inch", "Unit.mil", 2.54E-05, UnitSystem.IMPERIAL, "[mil_i]", "[mil_i]2"),
  INCH(11, "Unit.inch", "Unit.inch_s", 2.54E-02, UnitSystem.IMPERIAL, "[in_i]", "[in_i]2"),
  FEET(12, "Unit.feet", "Unit.feet_s", 3.048E-01, UnitSystem.IMPERIAL, "[ft_i]", "[ft_i]2"),
  YARD(13, "Unit.yard", "Unit.yard_s", 9.144E-01, UnitSystem.IMPERIAL, "[yd_i]", "[yd_i]2"),
  MILE(14, "Unit.mile", "Unit.mile_s", 1.609344E+03, UnitSystem.IMPERIAL, "[mi_i]", "[mi_i]2");

  private static final Map<Integer, Unit> ID_TO_UNIT =
      Arrays.stream(values()).collect(Collectors.toMap(Unit::getId, u -> u));
  private static final Map<String, Unit> NAME_TO_UNIT =
      Arrays.stream(values()).collect(Collectors.toMap(Unit::getFullName, u -> u));
  private static final Map<String, Unit> ABBREV_TO_UNIT =
      Arrays.stream(values()).collect(Collectors.toMap(Unit::getAbbreviation, u -> u));

  private final int id;
  private final String fullName;
  private final String abbreviation;
  private final double factorToMeters;
  private final UnitSystem system;
  private final String ucum;
  private final String ucumArea;

  Unit(
      int id,
      String fullNameKey,
      String abbreviationKey,
      double factorToMeters,
      UnitSystem system,
      String ucum,
      String ucumArea) {
    this.id = id;
    this.fullName = Messages.getString(fullNameKey);
    this.abbreviation = Messages.getString(abbreviationKey);
    this.factorToMeters = factorToMeters;
    this.system = system;
    this.ucum = ucum;
    this.ucumArea = ucumArea;
  }

  public int getId() {
    return id;
  }

  public String getFullName() {
    return fullName;
  }

  public String getAbbreviation() {
    return abbreviation;
  }

  public double getFactorToMeters() {
    return factorToMeters;
  }

  public UnitSystem getSystem() {
    return system;
  }

  /**
   * Case-sensitive UCUM code of this unit, for machine-readable output; a pixel has no physical
   * unit and is written as the UCUM annotation {@code {pixel}}.
   */
  public String getUcum() {
    return ucum;
  }

  /** UCUM code of an area measured in this unit. */
  public String getUcumArea() {
    return ucumArea;
  }

  @Override
  public String toString() {
    return fullName;
  }

  // === Conversion Methods ===

  /** Gets the conversion ratio for calibration. */
  public double getConversionRatio(double calibrationRatio) {
    return calibrationRatio / factorToMeters;
  }

  /**
   * Converts a value from this unit to another unit.
   *
   * @throws IllegalArgumentException if PIXEL conversion is attempted without calibration
   */
  public double convertTo(double value, Unit targetUnit) {
    if (this == targetUnit) return value;

    if (this == PIXEL || targetUnit == PIXEL) {
      throw new IllegalArgumentException(
          "Cannot convert between pixel and physical units without calibration");
    }

    return toMeters(value) / targetUnit.factorToMeters;
  }

  public double toMeters(double value) {
    return value * factorToMeters;
  }

  public double fromMeters(double meters) {
    return meters / factorToMeters;
  }

  // === Navigation Methods ===

  /** Gets the next larger unit in the same system. */
  public Optional<Unit> getNextLargerUnit() {
    return findAdjacentUnit(id + 1);
  }

  /** Gets the next smaller unit in the same system. */
  public Optional<Unit> getNextSmallerUnit() {
    return findAdjacentUnit(id - 1);
  }

  private Optional<Unit> findAdjacentUnit(int targetId) {
    return streamSameSystem().filter(u -> u.id == targetId).findFirst();
  }

  /** Gets all units in the same system, ordered by size. */
  public List<Unit> getUnitsInSameSystem() {
    return streamSameSystem().sorted(Comparator.comparingInt(Unit::getId)).toList();
  }

  private Stream<Unit> streamSameSystem() {
    return Arrays.stream(values()).filter(u -> u.system == this.system);
  }

  // === Static Utility Methods ===

  public static Unit getById(int id) {
    return ID_TO_UNIT.getOrDefault(id, PIXEL);
  }

  public static Optional<Unit> getByName(String name) {
    return Optional.ofNullable(NAME_TO_UNIT.get(name));
  }

  public static Optional<Unit> getByAbbreviation(String abbreviation) {
    return Optional.ofNullable(ABBREV_TO_UNIT.get(abbreviation));
  }

  /** Gets all physical units (excluding PIXEL), ordered by size. */
  public static List<Unit> getPhysicalUnits() {
    return streamFiltered(u -> u != PIXEL);
  }

  public static List<Unit> getMetricUnits() {
    return getUnitsBySystem(UnitSystem.METRIC);
  }

  public static List<Unit> getImperialUnits() {
    return getUnitsBySystem(UnitSystem.IMPERIAL);
  }

  public static List<Unit> getUnitsBySystem(UnitSystem system) {
    return streamFiltered(u -> u.system == system);
  }

  private static List<Unit> streamFiltered(Predicate<Unit> filter) {
    return Arrays.stream(values())
        .filter(filter)
        .sorted(Comparator.comparingInt(Unit::getId))
        .toList();
  }

  /**
   * Finds the most appropriate unit for a given value in meters. Prefers units that display values
   * between 0.1 and 1000.
   */
  public static Unit findBestUnit(double valueInMeters, UnitSystem preferredSystem) {
    return getUnitsBySystem(preferredSystem).stream()
        .filter(u -> u != PIXEL)
        .min(Comparator.comparingDouble(u -> calculateScore(u.fromMeters(valueInMeters))))
        .orElse(METER);
  }

  private static double calculateScore(double value) {
    double logValue = Math.abs(Math.log10(Math.abs(value)));
    return (value >= 0.1 && value <= 1000) ? logValue : logValue + 10;
  }

  public enum UnitSystem {
    METRIC("Metric System"),
    IMPERIAL("Imperial System"),
    DIGITAL("Digital Units");

    private final String displayName;

    UnitSystem(String displayName) {
      this.displayName = displayName;
    }

    public String getDisplayName() {
      return displayName;
    }

    @Override
    public String toString() {
      return displayName;
    }
  }
}
