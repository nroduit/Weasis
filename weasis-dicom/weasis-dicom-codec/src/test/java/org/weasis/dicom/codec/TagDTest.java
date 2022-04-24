/*
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse
 * Public License 2.0 which is available at https://www.eclipse.org/legal/epl-2.0, or the Apache
 * License, Version 2.0 which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
package org.weasis.dicom.codec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.Date;
import org.dcm4che3.data.DatePrecision;
import org.dcm4che3.util.DateUtils;
import org.junit.jupiter.api.Test;
import org.weasis.core.api.Messages;

class TagDTest {

  /**
   * A string of characters of the format YYYYMMDD; where YYYY shall contain year, MM shall contain
   * the month, and DD shall contain the day, interpreted as a date of the Gregorian calendar
   * system.
   *
   * <p>Example:
   *
   * <p>"19930822" would represent August 22, 1993.
   *
   * <p>Note The ACR-NEMA Standard 300 (predecessor to DICOM) supported a string of characters of
   * the format YYYY.MM.DD for this VR. Use of this format is not compliant.
   *
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_6.2.html">6.2
   *     Value Representation (VR)</a>
   */
  @Test
  void testGetDicomDate() {
    LocalDate date1 = TagD.getDicomDate("19930822");
    assertThat(date1).isEqualTo(LocalDate.of(1993, 8, 22));

    date1 = TagD.getDicomDate("1993.08.22");
    assertThat(date1).isEqualTo(LocalDate.of(1993, 8, 22));
  }

  /**
   * A string of characters of the format HHMMSS.FFFFFF; where HH contains hours (range "00" -
   * "23"), MM contains minutes (range "00" - "59"), SS contains seconds (range "00" - "60"), and
   * FFFFFF contains a fractional part of a second as small as 1 millionth of a second (range
   * "000000" - "999999"). A 24-hour clock is used. Midnight shall be represented by only "0000"
   * since "2400" would violate the hour range. The string may be padded with trailing spaces.
   * Leading and embedded spaces are not allowed.
   *
   * <p>One or more of the components MM, SS, or FFFFFF may be unspecified as long as every
   * component to the right of an unspecified component is also unspecified, which indicates that
   * the value is not precise to the precision of those unspecified components.
   *
   * <p>The FFFFFF component, if present, shall contain 1 to 6 digits. If FFFFFF is unspecified the
   * preceding "." shall not be included.
   *
   * <p>Examples: 1. “070907.0705 ” represents a time of 7 hours, 9 minutes and 7.0705 seconds.
   *
   * <p>2. “1010” represents a time of 10 hours, and 10 minutes.
   *
   * <p>3. “021 ” is an invalid value.
   *
   * <p>Notes: 1. The ACR-NEMA Standard 300 (predecessor to DICOM) supported a string of characters
   * of the format HH:MM:SS.frac for this VR. Use of this format is not compliant.
   *
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_6.2.html">6.2
   *     Value Representation (VR)</a>
   */
  @Test
  void testGetDicomTime() {
    LocalTime time = TagD.getDicomTime("070907.0705 ");
    assertThat(time).isEqualTo(LocalTime.of(7, 9, 7, 70_500_000));

    time = TagD.getDicomTime("10");
    assertThat(time).isEqualTo(LocalTime.of(10, 0));

    time = TagD.getDicomTime("1010");
    assertThat(time).isEqualTo(LocalTime.of(10, 10));

    time = TagD.getDicomTime("021 ");
    assertThat(time).isNull();

    // Does not support leap second:
    // http://stackoverflow.com/questions/30984599/how-does-the-oracle-java-jvm-know-a-leap-second-is-occurring
    // time = TagUtil.getDicomTime("235960");
    // assertEquals(LocalTime.of(23, 59, 60), time);

    time = TagD.getDicomTime("07:09:07.0705 ");
    assertThat(time).isEqualTo(LocalTime.of(7, 9, 7, 70_500_000));
  }

  /**
   * A concatenated date-time character string in the format: YYYYMMDDHHMMSS.FFFFFF&ZZXX
   *
   * <p>The components of this string, from left to right, are YYYY = Year, MM = Month, DD = Day, HH
   * = Hour (range "00" - "23"), MM = Minute (range "00" - "59"), SS = Second (range "00" - "60").
   *
   * <p>FFFFFF = Fractional Second contains a fractional part of a second as small as 1 millionth of
   * a second (range "000000" - "999999").
   *
   * <p>&ZZXX is an optional suffix for offset from Coordinated Universal Time (UTC), where & = "+"
   * or "-", and ZZ = Hours and XX = Minutes of offset.
   *
   * <p>The year, month, and day shall be interpreted as a date of the Gregorian calendar system.
   *
   * <p>A 24-hour clock is used. Midnight shall be represented by only "0000" since "2400" would
   * violate the hour range.
   *
   * @see <a
   *     href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_6.2.html">6.2
   *     Value Representation (VR)</a>
   */
  @Test
  void testGetDicomDateTime() {

    Date date = DateUtils.parseDA(null, "1993:08:22");
    LocalDateTime datetime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    assertThat(datetime.toLocalDate()).isEqualTo(LocalDate.of(1993, 8, 22));

    DatePrecision precision = new DatePrecision();
    date = DateUtils.parseTM(null, "070907.07 ", precision);
    datetime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    assertThat(datetime.toLocalTime()).isEqualTo(LocalTime.of(7, 9, 7, 70_000_000));

    TemporalAccessor time = TagD.getDicomDateTime("1953082711");
    assertThat(time).isEqualTo(LocalDateTime.of(1953, 8, 27, 11, 0));

    time = TagD.getDicomDateTime("19530827111300");
    assertThat(time).isEqualTo(LocalDateTime.of(1953, 8, 27, 11, 13, 0));

    time = TagD.getDicomDateTime("19530827111300.0");
    assertThat(time).isEqualTo(LocalDateTime.of(1953, 8, 27, 11, 13, 0));

    time = TagD.getDicomDateTime("19530827111300.005");
    assertThat(time).isEqualTo(LocalDateTime.of(1953, 8, 27, 11, 13, 0, 5_000_000));
  }

  @Test
  void testGetDicomPatientSex() {
    String sex = TagD.getDicomPatientSex(null);
    assertThat(sex).isEmpty();

    sex = TagD.getDicomPatientSex("");
    assertThat(sex).isEmpty();

    sex = TagD.getDicomPatientSex("F"); // NON-NLS
    assertThat(sex).isEqualTo(Messages.getString("TagW.female"));

    sex = TagD.getDicomPatientSex("M"); // NON-NLS
    assertThat(sex).isEqualTo(Messages.getString("TagW.Male"));

    sex = TagD.getDicomPatientSex("Male"); // NON-NLS
    assertThat(sex).isEqualTo(Messages.getString("TagW.Male"));

    sex = TagD.getDicomPatientSex("O"); // NON-NLS
    assertThat(sex).isEqualTo(Messages.getString("TagW.other"));

    sex = TagD.getDicomPatientSex("U"); // NON-NLS
    assertThat(sex).isEqualTo(Messages.getString("TagW.other"));
  }

  @Test
  void testGetDicomPersonName() {
    String name = TagD.getDicomPersonName(null);
    assertThat(name).isEmpty();

    name = TagD.getDicomPersonName(" ");
    assertThat(name).isEmpty();

    name = TagD.getDicomPersonName("Delaney^William^M.^Dr^MD"); // NON-NLS
    assertThat(name).isEqualTo("Delaney, William M., Dr, MD"); // NON-NLS
  }

  @Test
  void testGetDicomPeriod() {
    String period = TagD.getDicomPeriod(null);
    assertThat(period).isEmpty();

    period = TagD.getDicomPeriod("0");
    assertThat(period).isEmpty();

    period = TagD.getDicomPeriod("0Z"); // NON-NLS
    assertThat(period).isEmpty();

    period = TagD.getDicomPeriod("031Y"); // NON-NLS
    assertThat(period).isEqualTo("31 " + ChronoUnit.YEARS);

    period = TagD.getDicomPeriod("001Y"); // NON-NLS
    assertThat(period).isEqualTo("1 " + ChronoUnit.YEARS);

    period = TagD.getDicomPeriod("1Y"); // NON-NLS
    assertThat(period).isEqualTo("1 " + ChronoUnit.YEARS);

    period = TagD.getDicomPeriod("000Y"); // NON-NLS
    assertThat(period).isEqualTo("0 " + ChronoUnit.YEARS);

    period = TagD.getDicomPeriod("001M"); // NON-NLS
    assertThat(period).isEqualTo("1 " + ChronoUnit.MONTHS); // NON-NLS

    period = TagD.getDicomPeriod("011W"); // NON-NLS
    assertThat(period).isEqualTo("11 " + ChronoUnit.WEEKS);

    period = TagD.getDicomPeriod("111D"); // NON-NLS
    assertThat(period).isEqualTo("111 " + ChronoUnit.DAYS);
  }
}
