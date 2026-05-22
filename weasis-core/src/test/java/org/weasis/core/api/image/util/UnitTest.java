/*
 * Copyright (c) 2026 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.core.api.image.util;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.image.util.Unit.UnitSystem;

class UnitTest {

  private static final double EPS = 1.0e-9;
  // Loose tolerance for inch↔metric conversions accumulated over multiplications.
  private static final double METRIC_EPS = 1.0e-10;

  // -- Conversion math (clinically load-bearing) ---------------------------

  @Test
  void convertTo_sameUnitReturnsValueUnchanged() {
    assertEquals(42.0, Unit.MILLIMETER.convertTo(42.0, Unit.MILLIMETER), EPS);
  }

  @Test
  void convertTo_inchToMillimeterIsExact25Point4() {
    // ISO 31-1 defines 1 inch = 25.4 mm exactly.
    assertEquals(25.4, Unit.INCH.convertTo(1.0, Unit.MILLIMETER), METRIC_EPS);
  }

  @Test
  void convertTo_millimeterToCentimeter() {
    assertEquals(1.0, Unit.MILLIMETER.convertTo(10.0, Unit.CENTIMETER), EPS);
  }

  @Test
  void convertTo_meterToKilometer() {
    assertEquals(0.001, Unit.METER.convertTo(1.0, Unit.KILOMETER), EPS);
  }

  @Test
  void convertTo_meterToMillimeter() {
    assertEquals(1000.0, Unit.METER.convertTo(1.0, Unit.MILLIMETER), EPS);
  }

  @Test
  void convertTo_footToYard() {
    assertEquals(1.0, Unit.FEET.convertTo(3.0, Unit.YARD), EPS);
  }

  @Test
  void convertTo_mileToKilometer() {
    assertEquals(1.609344, Unit.MILE.convertTo(1.0, Unit.KILOMETER), 1.0e-7);
  }

  @Test
  void convertTo_isReversible() {
    // Critical for clinical measurements: round-trip through any pair must return the original.
    double original = 12.345;
    double roundTrip =
        Unit.MILLIMETER.convertTo(Unit.INCH.convertTo(original, Unit.MILLIMETER), Unit.INCH);

    assertEquals(original, roundTrip, METRIC_EPS);
  }

  @Test
  void convertTo_pixelAsSourceThrows() {
    // Pixel→physical conversion without calibration must be refused; otherwise unit-less
    // pixel counts would silently be reported as mm.
    assertThrows(IllegalArgumentException.class, () -> Unit.PIXEL.convertTo(1.0, Unit.MILLIMETER));
  }

  @Test
  void convertTo_pixelAsTargetThrows() {
    assertThrows(IllegalArgumentException.class, () -> Unit.MILLIMETER.convertTo(1.0, Unit.PIXEL));
  }

  @Test
  void convertTo_pixelToPixelAllowedAsIdentity() {
    // The same-unit fast path returns unchanged, even for PIXEL.
    assertEquals(42.0, Unit.PIXEL.convertTo(42.0, Unit.PIXEL), EPS);
  }

  @Test
  void toMetersAndFromMeters_areInverse() {
    double meters = Unit.INCH.toMeters(10.0);
    assertEquals(10.0, Unit.INCH.fromMeters(meters), EPS);
  }

  @Test
  void toMeters_inchMatchesIsoDefinition() {
    assertEquals(0.0254, Unit.INCH.toMeters(1.0), EPS);
  }

  @Test
  void getConversionRatio_dividesCalibrationByFactor() {
    // For a calibration ratio of 0.05 m/px applied to MILLIMETER (factor 1e-3),
    // the resulting per-pixel-mm ratio = 0.05 / 1e-3 = 50.
    assertEquals(50.0, Unit.MILLIMETER.getConversionRatio(0.05), EPS);
  }

  // -- System & metadata ---------------------------------------------------

  @Test
  void getSystem_classifiesEachUnit() {
    assertAll(
        () -> assertSame(UnitSystem.DIGITAL, Unit.PIXEL.getSystem()),
        () -> assertSame(UnitSystem.METRIC, Unit.MILLIMETER.getSystem()),
        () -> assertSame(UnitSystem.METRIC, Unit.METER.getSystem()),
        () -> assertSame(UnitSystem.IMPERIAL, Unit.INCH.getSystem()),
        () -> assertSame(UnitSystem.IMPERIAL, Unit.MILE.getSystem()));
  }

  @Test
  void getFactorToMeters_matchesDefinition() {
    assertAll(
        () -> assertEquals(1.0, Unit.METER.getFactorToMeters(), EPS),
        () -> assertEquals(1.0e-3, Unit.MILLIMETER.getFactorToMeters(), EPS),
        () -> assertEquals(2.54e-2, Unit.INCH.getFactorToMeters(), EPS),
        () -> assertEquals(1.0, Unit.PIXEL.getFactorToMeters(), EPS));
  }

  // -- Navigation (next-larger / next-smaller within same system) ----------

  @Test
  void getNextLargerUnit_metricChain() {
    assertAll(
        () -> assertEquals(Optional.of(Unit.CENTIMETER), Unit.MILLIMETER.getNextLargerUnit()),
        () -> assertEquals(Optional.of(Unit.METER), Unit.CENTIMETER.getNextLargerUnit()),
        () -> assertEquals(Optional.of(Unit.KILOMETER), Unit.METER.getNextLargerUnit()),
        () -> assertTrue(Unit.KILOMETER.getNextLargerUnit().isEmpty()));
  }

  @Test
  void getNextSmallerUnit_metricChain() {
    assertAll(
        () -> assertEquals(Optional.of(Unit.CENTIMETER), Unit.METER.getNextSmallerUnit()),
        () -> assertEquals(Optional.of(Unit.MILLIMETER), Unit.CENTIMETER.getNextSmallerUnit()),
        () -> assertEquals(Optional.of(Unit.MICROMETER), Unit.MILLIMETER.getNextSmallerUnit()),
        () -> assertTrue(Unit.NANOMETER.getNextSmallerUnit().isEmpty()));
  }

  @Test
  void getNextLargerUnit_doesNotCrossSystems() {
    // Imperial unit boundary must not return a metric neighbour.
    Optional<Unit> next = Unit.MILE.getNextLargerUnit();

    assertTrue(next.isEmpty(), "no larger imperial unit beyond MILE");
  }

  @Test
  void getUnitsInSameSystem_orderedAndScoped() {
    List<Unit> metric = Unit.METER.getUnitsInSameSystem();

    assertAll(
        () -> assertEquals(6, metric.size(), "six metric units"),
        () -> assertEquals(Unit.NANOMETER, metric.get(0), "smallest first"),
        () -> assertEquals(Unit.KILOMETER, metric.get(metric.size() - 1), "largest last"),
        () -> assertFalse(metric.contains(Unit.INCH), "no imperial units leak in"),
        () -> assertFalse(metric.contains(Unit.PIXEL), "no digital units leak in"));
  }

  // -- Static lookup -------------------------------------------------------

  @Test
  void getById_returnsKnownUnit() {
    assertEquals(Unit.METER, Unit.getById(2));
  }

  @Test
  void getById_unknownIdFallsBackToPixel() {
    // Critical: an unknown id must never throw — it falls back to PIXEL (uncalibrated).
    assertEquals(Unit.PIXEL, Unit.getById(9999));
  }

  @Test
  void getByName_resolvesAllUnitsFromTheirLocalizedName() {
    // Round-trip via the live localized full name avoids hard-coding i18n strings.
    for (Unit u : Unit.values()) {
      assertEquals(
          Optional.of(u), Unit.getByName(u.getFullName()), "round-trip getByName for " + u.name());
    }
  }

  @Test
  void getByName_unknownReturnsEmpty() {
    assertTrue(Unit.getByName("definitely-not-a-real-unit-xyz").isEmpty());
  }

  @Test
  void getByName_nullReturnsEmpty() {
    assertTrue(Unit.getByName(null).isEmpty());
  }

  @Test
  void getByAbbreviation_resolvesAllUnitsFromTheirLocalizedAbbreviation() {
    for (Unit u : Unit.values()) {
      assertEquals(
          Optional.of(u),
          Unit.getByAbbreviation(u.getAbbreviation()),
          "round-trip getByAbbreviation for " + u.name());
    }
  }

  @Test
  void getByAbbreviation_unknownReturnsEmpty() {
    assertTrue(Unit.getByAbbreviation("xx").isEmpty());
  }

  // -- Group selectors -----------------------------------------------------

  @Test
  void getMetricUnits_containsAllMetricNotImperialNorDigital() {
    List<Unit> metric = Unit.getMetricUnits();

    assertAll(
        () -> assertTrue(metric.contains(Unit.MILLIMETER)),
        () -> assertTrue(metric.contains(Unit.METER)),
        () -> assertFalse(metric.contains(Unit.INCH)),
        () -> assertFalse(metric.contains(Unit.PIXEL)));
  }

  @Test
  void getImperialUnits_containsAllImperialNotMetricNorDigital() {
    List<Unit> imperial = Unit.getImperialUnits();

    assertAll(
        () -> assertTrue(imperial.contains(Unit.INCH)),
        () -> assertTrue(imperial.contains(Unit.MILE)),
        () -> assertFalse(imperial.contains(Unit.METER)),
        () -> assertFalse(imperial.contains(Unit.PIXEL)));
  }

  @Test
  void getPhysicalUnits_excludesPixel() {
    List<Unit> physical = Unit.getPhysicalUnits();

    assertFalse(physical.contains(Unit.PIXEL), "PIXEL must not be classified as a physical unit");
    assertTrue(physical.contains(Unit.METER));
    assertTrue(physical.contains(Unit.INCH));
  }

  @Test
  void getUnitsBySystem_returnsAllMatchingUnits() {
    assertEquals(Unit.getMetricUnits().size(), Unit.getUnitsBySystem(UnitSystem.METRIC).size());
    assertEquals(Unit.getImperialUnits().size(), Unit.getUnitsBySystem(UnitSystem.IMPERIAL).size());
    assertEquals(1, Unit.getUnitsBySystem(UnitSystem.DIGITAL).size());
  }

  // -- findBestUnit (heuristic that drives display labels) -----------------

  @Test
  void findBestUnit_oneMeterChoosesMeter() {
    assertEquals(Unit.METER, Unit.findBestUnit(1.0, UnitSystem.METRIC));
  }

  @Test
  void findBestUnit_oneMillimeterChoosesMillimeter() {
    assertEquals(Unit.MILLIMETER, Unit.findBestUnit(0.001, UnitSystem.METRIC));
  }

  @Test
  void findBestUnit_oneCentimeterChoosesCentimeter() {
    assertEquals(Unit.CENTIMETER, Unit.findBestUnit(0.01, UnitSystem.METRIC));
  }

  @Test
  void findBestUnit_fifteenHundredMetersChoosesKilometer() {
    assertEquals(Unit.KILOMETER, Unit.findBestUnit(1500.0, UnitSystem.METRIC));
  }

  @Test
  void findBestUnit_imperialOneFootChoosesFeet() {
    // 0.3048 m → about 1 foot
    assertEquals(Unit.FEET, Unit.findBestUnit(0.3048, UnitSystem.IMPERIAL));
  }

  // Boundary values: the score heuristic treats [0.1, 1000] as "in range" with
  // both endpoints inclusive (>= 0.1 and <= 1000). Pin the inclusive boundary
  // so a regression that flipped either comparison to strict `>` / `<` (or
  // changed the `+10` out-of-range penalty) would surface as a unit swap.

  @Test
  void findBestUnit_metricAtLowerBoundaryChoosesMillimeter() {
    // 0.0001 m -> 0.1 mm exactly. If the lower bound were exclusive (> 0.1),
    // MILLIMETER would be penalised (+10) and MICROMETER (value=100, score=2)
    // would win instead. Inclusive lower bound is the documented contract.
    assertEquals(Unit.MILLIMETER, Unit.findBestUnit(0.0001, UnitSystem.METRIC));
  }

  @Test
  void findBestUnit_metricAtUpperBoundaryChoosesKilometer() {
    // 1_000_000 m -> 1000 km exactly (upper boundary, in range).
    assertEquals(Unit.KILOMETER, Unit.findBestUnit(1_000_000.0, UnitSystem.METRIC));
  }

  @Test
  void findBestUnit_imperialAtLowerBoundaryChoosesInch() {
    // 0.00254 m -> 0.1 inch exactly. Inclusive lower bound keeps INCH (score=1)
    // ahead of MILLIINCH (value=100, score=2).
    assertEquals(Unit.INCH, Unit.findBestUnit(0.00254, UnitSystem.IMPERIAL));
  }

  @Test
  void findBestUnit_imperialAtUpperBoundaryChoosesMile() {
    // 1_609_344 m -> 1000 miles exactly (upper boundary, in range).
    assertEquals(Unit.MILE, Unit.findBestUnit(1_609_344.0, UnitSystem.IMPERIAL));
  }
}
