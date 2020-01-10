/*******************************************************************************
 * Copyright (c) 2009-2020 Weasis Team and other contributors.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.weasis.dicom.codec;

import static org.junit.Assert.assertEquals;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import org.junit.Test;
import org.weasis.core.api.Messages;

public class TagDTest {

    /**
     * A string of characters of the format YYYYMMDD; where YYYY shall contain year, MM shall contain the month, and DD
     * shall contain the day, interpreted as a date of the Gregorian calendar system.
     * <P>
     * Example:
     * <P>
     * "19930822" would represent August 22, 1993.
     * <P>
     * Note The ACR-NEMA Standard 300 (predecessor to DICOM) supported a string of characters of the format YYYY.MM.DD
     * for this VR. Use of this format is not compliant.
     *
     * @throws Exception
     *
     * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_6.2.html">6.2 Value
     *      Representation (VR)</a>
     */
    @Test
    public void testGetDicomDate() throws Exception {
        LocalDate date1 = TagD.getDicomDate("19930822"); //$NON-NLS-1$
        assertEquals(LocalDate.of(1993, 8, 22), date1);

        date1 = TagD.getDicomDate("1993:08:22"); //$NON-NLS-1$
        assertEquals(LocalDate.of(1993, 8, 22), date1);
    }

    /**
     * A string of characters of the format HHMMSS.FFFFFF; where HH contains hours (range "00" - "23"), MM contains
     * minutes (range "00" - "59"), SS contains seconds (range "00" - "60"), and FFFFFF contains a fractional part of a
     * second as small as 1 millionth of a second (range "000000" - "999999"). A 24-hour clock is used. Midnight shall
     * be represented by only "0000" since "2400" would violate the hour range. The string may be padded with trailing
     * spaces. Leading and embedded spaces are not allowed.
     * <P>
     * One or more of the components MM, SS, or FFFFFF may be unspecified as long as every component to the right of an
     * unspecified component is also unspecified, which indicates that the value is not precise to the precision of
     * those unspecified components.
     * <P>
     * The FFFFFF component, if present, shall contain 1 to 6 digits. If FFFFFF is unspecified the preceding "." shall
     * not be included.
     *
     * <P>
     * Examples: 1. “070907.0705 ” represents a time of 7 hours, 9 minutes and 7.0705 seconds.
     * <P>
     * 2. “1010” represents a time of 10 hours, and 10 minutes.
     * <P>
     * 3. “021 ” is an invalid value.
     * <P>
     * Notes: 1. The ACR-NEMA Standard 300 (predecessor to DICOM) supported a string of characters of the format
     * HH:MM:SS.frac for this VR. Use of this format is not compliant.
     *
     * @throws Exception
     *
     * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_6.2.html">6.2 Value
     *      Representation (VR)</a>
     */
    @Test
    public void testGetDicomTime() throws Exception {
        LocalTime time = TagD.getDicomTime("070907.0705 "); //$NON-NLS-1$
        assertEquals(LocalTime.of(7, 9, 7, 70_500_000), time);

        time = TagD.getDicomTime("10"); //$NON-NLS-1$
        assertEquals(LocalTime.of(10, 0), time);

        time = TagD.getDicomTime("1010"); //$NON-NLS-1$
        assertEquals(LocalTime.of(10, 10), time);

        time = TagD.getDicomTime("021 "); //$NON-NLS-1$
        assertEquals(null, time);

        // Does not support leap second:
        // http://stackoverflow.com/questions/30984599/how-does-the-oracle-java-jvm-know-a-leap-second-is-occurring
        // time = TagUtil.getDicomTime("235960");
        // assertEquals(LocalTime.of(23, 59, 60), time);

        time = TagD.getDicomTime("07:09:07.0705 "); //$NON-NLS-1$
        assertEquals(LocalTime.of(7, 9, 7, 70_500_000), time);
    }

    /**
     * A concatenated date-time character string in the format: YYYYMMDDHHMMSS.FFFFFF&ZZXX
     * <P>
     * The components of this string, from left to right, are YYYY = Year, MM = Month, DD = Day, HH = Hour (range "00" -
     * "23"), MM = Minute (range "00" - "59"), SS = Second (range "00" - "60").
     * <P>
     * FFFFFF = Fractional Second contains a fractional part of a second as small as 1 millionth of a second (range
     * "000000" - "999999").
     * <P>
     * &ZZXX is an optional suffix for offset from Coordinated Universal Time (UTC), where & = "+" or "-", and ZZ =
     * Hours and XX = Minutes of offset.
     * <P>
     * The year, month, and day shall be interpreted as a date of the Gregorian calendar system.
     * <P>
     * A 24-hour clock is used. Midnight shall be represented by only "0000" since "2400" would violate the hour range.
     *
     * @throws Exception
     *
     * @see <a href="http://dicom.nema.org/medical/dicom/current/output/chtml/part05/sect_6.2.html">6.2 Value
     *      Representation (VR)</a>
     */
    @Test
    public void testGetDicomDateTime() throws Exception {

        // Date date = DateUtils.parseDA(null, "1993:08:22");
        // LocalDateTime datetime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        // assertEquals(LocalDate.of(1993, 8, 22), datetime.toLocalDate());
        //
        // DatePrecision precision = new DatePrecision();
        // date = DateUtils.parseTM(null, "0709.0705 ", precision);
        // datetime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
        // assertEquals(LocalTime.of(7, 9, 7, 70_000_000), datetime.toLocalTime());

        // LocalDateTime time = TagD.getDicomDateTime(null, "1953082711");
        // assertEquals(LocalDateTime.of(1953, 8, 27, 11, 0), time);
        //
        // time = TagD.getDicomDateTime(null,"19530827111300");
        // assertEquals(LocalDateTime.of(1953, 8, 27, 11, 13, 0), time);
        //
        // time = TagD.getDicomDateTime(null,"19530827111300.0");
        // assertEquals(LocalDateTime.of(1953, 8, 27, 11, 13, 0), time);
        //
        // time = TagD.getDicomDateTime(null,"19530827111300.000055");
        // assertEquals(LocalDateTime.of(1953, 8, 27, 11, 13, 0, 55_000), time);
        //
        // time = TagD.getDicomDateTime(null,"19530827111300+0700");
        // assertEquals(LocalDateTime.of(1953, 8, 27, 11, 13, 0, 55_000), time);
    }

    @Test
    public void testGetDicomPatientSex() throws Exception {
        String sex = TagD.getDicomPatientSex(null);
        assertEquals("", sex); //$NON-NLS-1$

        sex = TagD.getDicomPatientSex(""); //$NON-NLS-1$
        assertEquals("", sex); //$NON-NLS-1$

        sex = TagD.getDicomPatientSex("F"); //$NON-NLS-1$
        assertEquals(Messages.getString("TagW.female"), sex); //$NON-NLS-1$

        sex = TagD.getDicomPatientSex("M"); //$NON-NLS-1$
        assertEquals(Messages.getString("TagW.Male"), sex); //$NON-NLS-1$

        sex = TagD.getDicomPatientSex("Male"); //$NON-NLS-1$
        assertEquals(Messages.getString("TagW.Male"), sex); //$NON-NLS-1$

        sex = TagD.getDicomPatientSex("O"); //$NON-NLS-1$
        assertEquals(Messages.getString("TagW.other"), sex); //$NON-NLS-1$

        sex = TagD.getDicomPatientSex("U"); //$NON-NLS-1$
        assertEquals(Messages.getString("TagW.other"), sex); //$NON-NLS-1$
    }

    @Test
    public void testGetDicomPersonName() throws Exception {
        String name = TagD.getDicomPersonName(null);
        assertEquals("", name); //$NON-NLS-1$

        name = TagD.getDicomPersonName(" "); //$NON-NLS-1$
        assertEquals("", name); //$NON-NLS-1$

        name = TagD.getDicomPersonName("Delaney^William^M.^Dr^MD"); //$NON-NLS-1$
        assertEquals("Delaney, William M., Dr, MD", name); //$NON-NLS-1$
    }

    @Test
    public void testGetDicomPeriod() throws Exception {
        String period = TagD.getDicomPeriod(null);
        assertEquals("", period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("0"); //$NON-NLS-1$
        assertEquals("", period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("0Z"); //$NON-NLS-1$
        assertEquals("", period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("031Y"); //$NON-NLS-1$
        assertEquals("31 " + ChronoUnit.YEARS.toString(), period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("001Y"); //$NON-NLS-1$
        assertEquals("1 " + ChronoUnit.YEARS.toString(), period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("1Y"); //$NON-NLS-1$
        assertEquals("1 " + ChronoUnit.YEARS.toString(), period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("000Y"); //$NON-NLS-1$
        assertEquals("0 " + ChronoUnit.YEARS.toString(), period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("001M"); //$NON-NLS-1$
        assertEquals("1 " + ChronoUnit.MONTHS.toString(), period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("011W"); //$NON-NLS-1$
        assertEquals("11 " + ChronoUnit.WEEKS.toString(), period); //$NON-NLS-1$

        period = TagD.getDicomPeriod("111D"); //$NON-NLS-1$
        assertEquals("111 " + ChronoUnit.DAYS.toString(), period); //$NON-NLS-1$
    }

}
