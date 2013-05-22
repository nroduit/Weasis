/* Copyright (c) 2001-2005, David A. Clunie DBA Pixelmed Publishing. All rights reserved. */

package org.weasis.dicom.codec.utils;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

/**
 * <p>
 * A class for fetching the MAC address of one of the local host network interfaces.
 * </p>
 * 
 * <p>
 * Works on Linux, Solaris, Darwin, Windows.
 * </p>
 * 
 * @author dclunie
 */

public class MACAddress {

    private long macAddressValue;

    /*
     * <p>Return the MAC address of one of the interfaces of this host.</p>
     * 
     * @return the MAC address as a long integer, or 0 if cannot be obtained
     */
    public long getMACAddress() {
        return macAddressValue;
    }

    private static final String regexForMACComponent = "[0-9A-Fa-f][0-9A-Fa-f]?";
    private static final String regexForMACSeparator = "[:-]"; // : on unix, - on windoze
    private static final String regexForMAC = regexForMACComponent + regexForMACSeparator + regexForMACComponent
        + regexForMACSeparator + regexForMACComponent + regexForMACSeparator + regexForMACComponent
        + regexForMACSeparator + regexForMACComponent + regexForMACSeparator + regexForMACComponent;

    // the following pattern of using threads to consume exec output is from
    // "http://www.javaworld.com/javaworld/jw-12-2000/jw-1229-traps.html"

    private class ConsumeStreamLookingForRexEx extends Thread {
        BufferedReader r;
        String regex;
        String value;

        ConsumeStreamLookingForRexEx(InputStream i, String regex) {
            r = new BufferedReader(new InputStreamReader(i), 10000);
            this.regex = regex;
            value = null;
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = r.readLine()) != null && (value == null || value.length() == 0)) {
                    // System.err.println("MACAddress.ConsumeStreamLookingForRexEx.run(): read line=\""+line+"\"");
                    if (line.length() > 0 && line.matches(".*" + regex + ".*")) {
                        // System.err.println("MACAddress.ConsumeStreamLookingForRexEx.run(): line matches=\""+line+"\"");
                        StringTokenizer st = new StringTokenizer(line, " ");
                        while (st.hasMoreTokens()) {
                            String test = st.nextToken();
                            if (test.matches(regex)) {
                                value = test;
                                if (value != null && value.length() > 0) {
                                    // System.err.println("MACAddress.ConsumeStreamLookingForRexEx.run(): got=\""+value+"\"");
                                    break; // do not look beyond the first found
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(System.err);
            }
        }

        String getValue() {
            return value;
        }
    }

    // private final String executeCommandLookingForFirstLineContainingAndGetMatchingString(String[] command,String
    // regex) {
    private final String executeCommandLookingForFirstLineContainingAndGetMatchingString(String command, String regex) {
        // System.err.println("MACAddress.executeCommandLookingForFirstLineContaining(): command=\""+command+"\"");
        String value = null;
        try {
            Process p = Runtime.getRuntime().exec(command);
            ConsumeStreamLookingForRexEx outConsumer = new ConsumeStreamLookingForRexEx(p.getInputStream(), regex);
            ConsumeStreamLookingForRexEx errConsumer = new ConsumeStreamLookingForRexEx(p.getErrorStream(), regex);
            outConsumer.start();
            errConsumer.start();
            // System.err.println("MACAddress.executeCommandLookingForFirstLineContaining(): waitFor");
            int exitValue = p.waitFor();
            // System.err.println("MACAddress.executeCommandLookingForFirstLineContaining(): exitVal=\""+exitValue+"\"");
            // don't try to get a value until the output has been completely processed (may be after process finished)
            outConsumer.join();
            errConsumer.join();
            value = outConsumer.getValue();
            if (value == null) {
                value = errConsumer.getValue();
            }
        } catch (Exception e) {
            // ignore exception (e.g. if cannot find command on another platform, such as
            // "java.io.IOException: cmd: not found")
            // e.printStackTrace(System.err);
        }
        return value;
    }

    private static final long extractMACAddressFromHexComponents(String macAddressString) {
        long macAddressValue = 0;
        if (macAddressString != null) {
            StringTokenizer st = new StringTokenizer(macAddressString, regexForMACSeparator);
            while (st.hasMoreTokens()) {
                String hexValue = st.nextToken();
                macAddressValue = (macAddressValue << 8) + (Long.parseLong(hexValue, 16) & 0x000000ff);
            }
        }
        // System.err.println("MACAddress.extractMACAddressFromHexComponents(): MAC address="+macAddressValue+" dec (0x"+Long.toHexString(macAddressValue)+")");
        return macAddressValue;
    }

    private static final long extractMACAddressFromByteArray(byte[] macAddressBytes) {
        long macAddressValue = 0;
        if (macAddressBytes != null) {
            int length = macAddressBytes.length;
            for (int i = 0; i < length; ++i) {
                macAddressValue = (macAddressValue << 8) + (macAddressBytes[i] & 0x000000ff);
            }
        }
        // System.err.println("MACAddress.extractMACAddressFromByteArray(): MAC address="+macAddressValue+" dec (0x"+Long.toHexString(macAddressValue)+")");
        return macAddressValue;
    }

    private static final String getUnqualifiedHostname() {
        String hostname = null;
        try {
            hostname = java.net.InetAddress.getLocalHost().getHostName();
            if (hostname != null && hostname.length() > 0) {
                int period = hostname.indexOf(".");
                if (period != -1) {
                    hostname = hostname.substring(0, period);
                }
            }
        } catch (Exception e) {
            e.printStackTrace(System.err);
        }
        return hostname;
    }

    // See also ...
    // "http://www.artsci.wustl.edu/ASCC/documentation/macaddrss.html"
    // "http://forum.java.sun.com/thread.jspa?threadID=245711&start=0&tstart=0"

    public MACAddress() {
        macAddressValue = getMacAddressFromNetworkInterfaceAPI();
        if (macAddressValue == 0) {
            macAddressValue = getMacAddressFromSystemCommandCall();
        }
    }

    protected static long getMacAddressFromNetworkInterfaceAPI() {
        long macAddressValue = 0;
        boolean foundOne = false;
        try {
            java.util.Enumeration interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            if (interfaces != null) {
                while (!foundOne && interfaces.hasMoreElements()) {
                    java.net.NetworkInterface i = (java.net.NetworkInterface) (interfaces.nextElement());
                    if (i != null) {
                        // System.err.println("MACAddress.getMacAddressFromNetworkInterfaceAPI(): network interface name="+i.getName());
                        Class[] argTypes = {};
                        Object[] argValues = {};
                        java.lang.reflect.Method getHardwareAddress =
                            i.getClass().getDeclaredMethod("getHardwareAddress", argTypes);
                        // System.err.println("MACAddress.getMacAddressFromNetworkInterfaceAPI(): getHardwareAddress method="+getHardwareAddress);
                        byte[] macAddressBytes = (byte[]) (getHardwareAddress.invoke(i, argValues));
                        // System.err.println("MACAddress.getMacAddressFromNetworkInterfaceAPI(): macAddressBytes="+macAddressBytes);
                        if (macAddressBytes != null) {
                            macAddressValue = extractMACAddressFromByteArray(macAddressBytes);
                            // System.err.println("MACAddress.getMacAddressFromNetworkInterfaceAPI(): macAddressValue=0x"+Long.toHexString(macAddressValue));
                            foundOne = true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // e.printStackTrace(System.err);
        }
        return macAddressValue;
    }

    protected long getMacAddressFromSystemCommandCall() {
        long macAddressValue = 0;
        // System.err.println("MACAddress(): regexForMAC=\""+regexForMAC+"\"");
        String macAddressString = null;
        // windows needs ipconfig, as well as the /all option, and a hyphen rather than colon seperator
        // do NOT try Windows 95 command.com instead of cmd.exe - causes dialog and error to popup
        if (macAddressString == null) {
            // System.err.println("MACAddress(): Try Windows NT/2000/XP ipconfig");
            String cmd = "ipconfig /all";
            macAddressString = executeCommandLookingForFirstLineContainingAndGetMatchingString(cmd, regexForMAC);
            // System.err.println("MACAddress(): MAC address from \""+cmd+"\" =\""+macAddressString+"\"");
        }
        if (macAddressString == null) {
            // System.err.println("MACAddress(): Try Windows NT/2000/XP ipconfig via cmd.exe");
            String cmd = "cmd.exe /c ipconfig /all";
            macAddressString = executeCommandLookingForFirstLineContainingAndGetMatchingString(cmd, regexForMAC);
            // System.err.println("MACAddress(): MAC address from \""+cmd+"\" =\""+macAddressString+"\"");
        }
        // a plain ifconfig should work for linux and darwin
        if (macAddressString == null) {
            // System.err.println("MACAddress(): Try ifconfig");
            String cmd = "ifconfig";
            macAddressString = executeCommandLookingForFirstLineContainingAndGetMatchingString(cmd, regexForMAC);
            // System.err.println("MACAddress(): MAC address from \""+cmd+"\" =\""+macAddressString+"\"");
        }
        // System.err.println("MACAddress(): MAC address from ifconfig=\""+macAddressString+"\"");
        // a plain ifconfig will not work for solaris unless one is root, so get it from arp (which does not work for
        // linux or darwin)
        if (macAddressString == null) {
            // System.err.println("MACAddress(): Try arp");
            String hostname = getUnqualifiedHostname();
            // System.err.println("Hostname of local machine: "+hostname);
            String cmd = "arp " + hostname;
            macAddressString = executeCommandLookingForFirstLineContainingAndGetMatchingString(cmd, regexForMAC);
            // System.err.println("MACAddress(): MAC address from \""+cmd+"\" =\""+macAddressString+"\"");
        }
        macAddressValue = extractMACAddressFromHexComponents(macAddressString);
        // System.err.println("MACAddress(): MAC address="+macAddressValue+" dec (0x"+Long.toHexString(macAddressValue)+")");
        return macAddressValue;
    }

    /**
     * <p>
     * Testing.
     * </p>
     * 
     * @param arg
     *            ignored
     */
    public static void main(String arg[]) {
        System.out.println("MAC address = 0x" + Long.toHexString(new MACAddress().getMACAddress()));
    }
}
