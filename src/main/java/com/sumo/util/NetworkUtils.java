package com.sumo.util;

/**
 * Utility class for network-related operations.
 * Provides methods for IP subnet calculation and MAC address processing.
 */
public class NetworkUtils {
    
    /**
     * Get /24 subnet from IP address (assuming IPv4).
     * @param ip the IP address
     * @return the /24 subnet or null if invalid
     */
    public static String getSubnet(String ip) {
        if (ip == null || ip.isEmpty() || !ip.contains(".")) {
            return null;
        }
        
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return null;
        }
        
        try {
            // Validate that all parts are valid integers
            for (String part : parts) {
                int value = Integer.parseInt(part);
                if (value < 0 || value > 255) {
                    return null;
                }
            }
            
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    /**
     * Get MAC prefix (first 3 octets) from MAC address.
     * @param mac the MAC address
     * @return the MAC prefix in format "AA:BB:CC" or null if invalid
     */
    public static String getMacPrefix(String mac) {
        if (mac == null) {
            return null;
        }
        
        // Remove non-hex characters (colons, dashes, spaces)
        String cleaned = mac.replaceAll("[^0-9A-Fa-f]", "");
        if (cleaned.length() < 6) {
            return null;
        }
        
        cleaned = cleaned.substring(0, 6).toUpperCase();
        return cleaned.substring(0, 2) + ":" + cleaned.substring(2, 4) + ":" + cleaned.substring(4, 6);
    }

    /**
     * Validate MAC address format.
     * @param mac the MAC address to validate
     * @return true if valid, false otherwise
     */
    public static boolean isValidMac(String mac) {
        if (mac == null || mac.isEmpty()) {
            return false;
        }
        
        // Remove non-hex characters
        String cleaned = mac.replaceAll("[^0-9A-Fa-f]", "");
        return cleaned.length() == 12; // 12 hex characters = 6 octets
    }
    
    /**
     * Normalize MAC address to standard format (AA:BB:CC:DD:EE:FF).
     * @param mac the MAC address to normalize
     * @return normalized MAC address or null if invalid
     */
    public static String normalizeMac(String mac) {
        if (!isValidMac(mac)) {
            return null;
        }
        
        String cleaned = mac.replaceAll("[^0-9A-Fa-f]", "").toUpperCase();
        StringBuilder normalized = new StringBuilder();
        
        for (int i = 0; i < cleaned.length(); i += 2) {
            if (i > 0) {
                normalized.append(":");
            }
            normalized.append(cleaned, i, i + 2);
        }
        
        return normalized.toString();
    }
}
