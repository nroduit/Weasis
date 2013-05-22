/* Copyright (c) 2001-2010, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.utils;

import java.rmi.dgc.VMID;
import java.rmi.server.UID;
import java.util.HashSet;
import java.util.StringTokenizer;

/**
 * <p>
 * A class for generating new UIDs, deterministically or not, including potentially reproducible Study, Series and SOP
 * Instance UIDs.
 * </p>
 * 
 * @author dclunie
 */

public class UIDGenerator {

    private static final String identString =
        "@(#) $Header: /userland/cvs/pixelmed/imgbook/com/pixelmed/dicom/UIDGenerator.java,v 1.9 2010/03/27 20:06:15 dclunie Exp $";

    private static final String UIDGEN_ANY = "0";
    private static final String UIDGEN_INSTANCE_SOP = "1";
    private static final String UIDGEN_INSTANCE_STUDY = "2";
    private static final String UIDGEN_INSTANCE_SERIES = "3";
    private static final String UIDGEN_FRAMEOFREFERENCE = "4";
    private static final String UIDGEN_INSTANCE_DIR = "5";
    private static final String UIDGEN_DIMENSIONORGANIZATION = "6";
    private static final String UIDGEN_CONCATENATION = "7";

    private String stamp;
    private String longStamp;

    private static final String root = VersionAndConstants.uidRoot + "."
        + VersionAndConstants.uidQualifierForThisToolkit + "." + VersionAndConstants.uidQualifierForUIDGenerator;

    private static final int maxStampComponentLength = 64 - root.length() - 1 // root .
        - 3 // . in generated stamp
        - 2 // . UIDGEN_XXX (would need to be three if > 9)
        - 5 - 5 - 5; // . SOP Instance UID study# (4) . series# (4) . instance# (4)

    private static final int maxLongStampComponentLength = 64 - root.length() - 1 // root .
        - 3 // . in generated stamp
        - 2 // . UIDGEN_XXX (would need to be three if > 9)
    ; // no study#, series# or instance#

    private static VMID vmid = new VMID(); // virtual machine ID
    private static long machineAddress = vmid.isUnique() ? (vmid.hashCode() & 0x0ffffffffl) : new MACAddress()
        .getMACAddress();

    // private static long machineAddress = new MACAddress().getMACAddress();

    /**
     * <p>
     * Create a UID generator.
     * </p>
     * 
     * <p>
     * This will use random and installation specific elements to create a unique root.
     * </p>
     * 
     */
    public UIDGenerator() {
        // System.err.println("UIDGenerator(): vmid.isUnique()="+vmid.isUnique());
        // System.err.println("UIDGenerator(): vmid.hashCode()="+vmid.hashCode());
        // System.err.println("UIDGenerator(): machineAddress="+machineAddress);
        newStamp();
    }

    /**
     * <p>
     * Reinitialize the UID generator with a new stamp using random and installation specific elements to create a
     * unique root.
     * </p>
     * 
     * <p>
     * For example, use between invocations of getNewUID().
     * </p>
     * 
     */
    public void newStamp() {
        long ourMachine = Math.abs(machineAddress); // don't mess with the real machine address; need it unchanged for
                                                    // next time

        String string = new UID().toString(); // e.g. "19c082:fb77ce774a:-8000"
        StringTokenizer st = new StringTokenizer(string, ":");
        int ourUnique = Math.abs(Integer.valueOf(st.nextToken(), 16).intValue());
        long ourTime = Math.abs(Long.valueOf(st.nextToken(), 16).longValue());
        int ourCount = Math.abs(Short.valueOf(st.nextToken(), 16).shortValue() + 0x8000); // why add 0x8000 ? because
                                                                                          // usually starts at -8000,
                                                                                          // which wastes 4 digits

        String machineString = Long.toString(ourMachine);
        String vmString = Integer.toString(ourUnique);
        String timeString = Long.toString(ourTime);
        String countString = Integer.toString(ourCount);

        while (ourUnique > 10000
            && machineString.length() + vmString.length() + timeString.length() + countString.length() > maxLongStampComponentLength) {
            // System.err.println("stamp length > maximum which is "+maxStampComponentLength+" shortening VM specific string");
            ourUnique = ourUnique / 10;
            vmString = Integer.toString(ourUnique);
        }
        while (ourMachine > 0
            && machineString.length() + vmString.length() + timeString.length() + countString.length() > maxLongStampComponentLength) {
            // System.err.println("stamp length > maximum which is "+maxStampComponentLength+" shortening MAC specific string");
            ourMachine = ourMachine / 10;
            machineString = Long.toString(ourMachine);
        }

        longStamp = machineString + "." + vmString + "." + timeString + "." + countString;

        while (ourUnique > 10000
            && machineString.length() + vmString.length() + timeString.length() + countString.length() > maxStampComponentLength) {
            // System.err.println("stamp length > maximum which is "+maxStampComponentLength+" shortening VM specific string");
            ourUnique = ourUnique / 10;
            vmString = Integer.toString(ourUnique);
        }
        while (ourMachine > 0
            && machineString.length() + vmString.length() + timeString.length() + countString.length() > maxStampComponentLength) {
            // System.err.println("stamp length > maximum which is "+maxStampComponentLength+" shortening MAC specific string");
            ourMachine = ourMachine / 10;
            machineString = Long.toString(ourMachine);
        }

        stamp = machineString + "." + vmString + "." + timeString + "." + countString;
    }

    /**
     * <p>
     * Create a UID generator.
     * </p>
     * 
     * <p>
     * This will use the supplied stamp rather than generating a unique root, to create repeatable UIDs.
     * </p>
     * 
     * @param stamp
     *            a String of dotted numeric values in UID form
     */
    public UIDGenerator(String stamp) {
        this.stamp = stamp;
    }

    private String getLimitedLengthNumericPartOfStringOrZeroAsUIDExtension(String string) {
        String addition = ".0";
        if (string != null) {
            try {
                long numericPart = Math.abs(Long.parseLong(string));
                if (numericPart > 9999) {
                    numericPart = numericPart % 10000; // no longer than 4 decimal digits
                }
                addition = "." + Long.toString(numericPart);
            } catch (NumberFormatException e) {
            }
        }
        return addition;
    }

    /**
     * <p>
     * Get a new UID for any purpose.
     * </p>
     * 
     * <p>
     * This will always be the same for this instance of the UIDGenerator, unless newStamp() has been called since the
     * last time.
     * </p>
     * 
     * @return the UID
     * @exception DicomException
     *                if result is too long or otherwise not a valid UID
     */
    public String getNewUID() throws DicomException {
        String uid = root + "." + longStamp + "." + UIDGEN_ANY;
        validateUID(uid);
        return uid;
    }

    /**
     * <p>
     * Get a different new UID for any purpose.
     * </p>
     * 
     * <p>
     * This will never be the same twice, since newStamp() is called.
     * </p>
     * 
     * @return the UID
     * @exception DicomException
     *                if result is too long or otherwise not a valid UID
     */
    public String getAnotherNewUID() throws DicomException {
        newStamp();
        return getNewUID();
    }

    /**
     * <p>
     * Get a Study Instance UID.
     * </p>
     * 
     * <p>
     * This will be the same for this instance of the UIDGenerator and the same parameter values.
     * </p>
     * 
     * <p>
     * Only use this if you really need reproducible UIDs; otherwise use getNewUID().
     * </p>
     * 
     * @param studyID
     *            least significant 4 digits of leading numeric part is used
     * @return the UID
     * @exception DicomException
     *                if result is too long or otherwise not a valid UID
     */
    public String getNewStudyInstanceUID(String studyID) throws DicomException {
        String uid =
            root + "." + stamp + "." + UIDGEN_INSTANCE_STUDY
                + getLimitedLengthNumericPartOfStringOrZeroAsUIDExtension(studyID);
        validateUID(uid);
        return uid;
    }

    /**
     * <p>
     * Get a Series Instance UID.
     * </p>
     * 
     * <p>
     * This will be the same for this instance of the UIDGenerator and the same parameter values.
     * </p>
     * 
     * <p>
     * Only use this if you really need reproducible UIDs; otherwise use getNewUID().
     * </p>
     * 
     * @param studyID
     *            least significant 4 digits of leading numeric part is used
     * @param seriesNumber
     *            least significant 4 digits of leading numeric part is used
     * @return the UID
     * @exception DicomException
     *                if result is too long or otherwise not a valid UID
     */
    public String getNewSeriesInstanceUID(String studyID, String seriesNumber) throws DicomException {
        String uid =
            root + "." + stamp + "." + UIDGEN_INSTANCE_SERIES
                + getLimitedLengthNumericPartOfStringOrZeroAsUIDExtension(studyID)
                + getLimitedLengthNumericPartOfStringOrZeroAsUIDExtension(seriesNumber);
        validateUID(uid);
        return uid;
    }

    /**
     * <p>
     * Get a SOP Instance UID.
     * </p>
     * 
     * <p>
     * This will be the same for this instance of the UIDGenerator and the same parameter values.
     * </p>
     * 
     * <p>
     * Only use this if you really need reproducible UIDs; otherwise use getNewUID().
     * </p>
     * 
     * @param studyID
     *            least significant 4 digits of leading numeric part is used
     * @param seriesNumber
     *            least significant 4 digits of leading numeric part is used
     * @param instanceNumber
     *            least significant 4 digits of leading numeric part is used
     * @return the UID
     * @exception DicomException
     *                if result is too long or otherwise not a valid UID
     */
    public String getNewSOPInstanceUID(String studyID, String seriesNumber, String instanceNumber)
        throws DicomException {
        String uid =
            root + "." + stamp + "." + UIDGEN_INSTANCE_SOP
                + getLimitedLengthNumericPartOfStringOrZeroAsUIDExtension(studyID) // max length 5
                + getLimitedLengthNumericPartOfStringOrZeroAsUIDExtension(seriesNumber) // max length 5
                + getLimitedLengthNumericPartOfStringOrZeroAsUIDExtension(instanceNumber); // max length 5
        // System.err.println("uid=\""+uid+"\"");
        validateUID(uid);
        return uid;
    }

    private static final void validateUID(String uid) throws DicomException {
        if (uid.length() > 64) {
            throw new DicomException("Generated UID exceeds 64 characters");
        }
    }

    /**
     * <p>
     * Test generating SOP Instance UIDs.
     * </p>
     * 
     * @param arg
     *            a single numeric argument that is the number of UIDs to generate
     */
    public static final void main(String arg[]) {
        try {
            int count = Integer.parseInt(arg[0]);
            String uids[] = new String[count];
            long startTime = System.currentTimeMillis();
            UIDGenerator generator = new UIDGenerator();
            for (int i = 0; i < count; ++i) {
                // uids[i]=generator.getNewSOPInstanceUID("8893429920299202","87359321","18748397");
                uids[i] = generator.getAnotherNewUID();
                // System.err.println("uids[i]=\""+uids[i]+"\"");
            }
            long endTime = System.currentTimeMillis();
            long totalTime = endTime - startTime;
            double timePerUID = (double) totalTime / count;
            System.err.println("count=" + count + ", total time=" + totalTime + " ms, time per UID=" + timePerUID
                + " ms, uids/ms=" + (1 / timePerUID));
            // System.err.println("uids[0]=\""+uids[0]+"\"");

            // Check are all unique
            boolean success = true;
            HashSet set = new HashSet();
            for (int i = 0; i < count; ++i) {
                if (set.contains(uids[i])) {
                    System.err.println("Error - not unique - \"" + uids[i] + "\"");
                    success = false;
                } else {
                    set.add(uids[i]);
                }
            }
            System.err.println("Uniqueness check " + (success ? "passes" : "fails"));
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
    }
}
