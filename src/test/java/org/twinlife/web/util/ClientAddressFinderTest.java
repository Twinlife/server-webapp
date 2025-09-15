/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */
package org.twinlife.web.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.Before;
import org.junit.Test;

public class ClientAddressFinderTest {
    /** List of addresses and IP ranges considered as 'local' in this unit test */
    private static final String LOCAL_IP_LIST = "8.8.8.8 91.203.187.0/24 193.93.124.0/24";

    private static final String XFF_VALID = "2001:db8:cafe::17, 193.93.124.33";
    private static final String XFF_VALID_2 = "8.8.8.8";

    // Some examples of "X-Forwarded-For" values include the IP port as well, so the
    // parser handle them.
    private static final String XFF_VALID_WITH_PORT = " [2001:db8::1a2b:3c4d]:41237, 198.51.100.0:26321 , 91.203.187.11";

    private static final String XFF_INVALID_ADDR = "123.3.9.666 , 91.203.187.11";

    /** Client address finder initialized with the LOCAL_IP_LIST */
    ClientAddressFinder addrFinder;
    
    /** Client address finder initialized with an empty local addresses list (so any address is considered NOT local) */
    ClientAddressFinder addrFinderNoLocalAddr;

    @Before
    public void setUp() throws Exception {
        addrFinder = new ClientAddressFinder(LOCAL_IP_LIST);
        addrFinderNoLocalAddr = new ClientAddressFinder(null);
    }

    @Test
    public void testValidXFF() throws UnknownHostException {
        InetAddress result = addrFinder.getClientAddressFromXFF(XFF_VALID);
        InetAddress expected = InetAddress.getByName("2001:db8:cafe::17");
        result.equals(expected);
        assertEquals(expected, result);
        
        result = addrFinder.getClientAddressFromXFF(XFF_VALID_2);
        assertNull(result);
        
        result = addrFinderNoLocalAddr.getClientAddressFromXFF(XFF_VALID);
        expected = InetAddress.getByName("193.93.124.33");
        result.equals(expected);
        assertEquals(expected, result);
    }

    @Test
    public void testValidXFFWithPort() throws UnknownHostException {
        InetAddress result = addrFinder.getClientAddressFromXFF(XFF_VALID_WITH_PORT);
        InetAddress expected = InetAddress.getByName("198.51.100.0");

        result.equals(expected);
        assertEquals(expected, result);
    }

    @Test
    public void testInvalidXFF() throws UnknownHostException {
        InetAddress result = addrFinder.getClientAddressFromXFF(XFF_INVALID_ADDR);
        assertNull(result);
        
        result = addrFinderNoLocalAddr.getClientAddressFromXFF(XFF_INVALID_ADDR);
        InetAddress expected = InetAddress.getByName("91.203.187.11");
        assertEquals(expected, result);
    }
}
