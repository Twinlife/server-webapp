/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */
package org.twinlife.web.util;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.UpgradeRequest;

import inet.ipaddr.IPAddress;
import inet.ipaddr.IPAddressString;

/**
 * A ClientAddressFinder is used to retrieve the actual IP address of the end-user device.
 *
 * <p>
 * Implementation note: The code relies on the 'ipaddress' library to perform IP subnet containment checks
 * but the public API still returns {@link java.net.InetAddress} typed value to limit the use of 'ipaddress'
 * library to this class only.
 * </p>
 */
public class ClientAddressFinder {
    static final Logger Log = LogManager.getLogger(ClientAddressFinder.class);

    /** Regexp used to validate / parse an IPv4 address, including a possible IP port */
    private static final Pattern IPv4_REGEXP = Pattern
            .compile("(?<addr>\\d{1,3}+\\.\\d{1,3}+\\.\\d{1,3}+\\.\\d{1,3})(?::(?<port>\\d+))?");
    /** Regexp used to validate / parse an IPv6 address, including a possible IP port */
    private static final Pattern IPv6_REGEXP = Pattern
            .compile("\\[?(?<addr>(?:[a-f0-9:]+:+)+[a-f0-9]+)(?:%.+?)?(?:\\]:(?<port>\\d+))?");

    private final List<IPAddress> localAddresses;

    /**
     * Constructs a new ClientAddressFinder instance
     *
     * @param localAddressesStr Space separated list of IP addresses (individual IP or CIDR range) to consider
     *   as 'local' addresses and ignored during the search of the 'actual' IP address of the end-user device.
     */
    public ClientAddressFinder(String localAddressesStr) {
        if (localAddressesStr == null || localAddressesStr.isBlank()) {
            localAddresses = Collections.emptyList();
        } else {
            final String items[] = localAddressesStr.split("[,\\s]");
            final ArrayList<IPAddress> addresses = new ArrayList<>(items.length);

            for (final String item : items) {
                IPAddressString str = new IPAddressString(item);
                IPAddress addr = str.getAddress();

                if (addr != null) {
                    addresses.add(addr);
                }
            }
            addresses.trimToSize();
            localAddresses = addresses;
        }
        Log.debug("ClientAddressFinder initialized with local IP addresses / ranges: [{}]",
                localAddresses.stream().map(x -> x.toString()).collect(Collectors.joining(", ")));
    }

    /**
     * Try to get the actual IP address of the client device / WEB browser from the WebSocket {@link Session}
     *
     * <p>
     * The process is:
     * <ul>
     *  <li>Return the first IP address in the 'X-Forwarded-For' header (if present in the request) which is not
     *      one of the 'local' addresses configured for the finder.
     *  <li>Return the 'remote' IP address of the session if no address was found in first step
     * </ul>
     * </p>
     *
     * @param session The Jetty WebSocket session instance
     * @return An {@link InetAddress} instance with the client address or <code>null</code> if no address found
     */
    public InetAddress getClientAddressFromSession(Session session) {
        final UpgradeRequest request = session.getUpgradeRequest();

        InetAddress result = getClientAddressFromRequest(request);
        if (result == null) {
            // If the address cannot be retrieved from request headers,
            // use remote address of session.
            try {
                result = ((InetSocketAddress) session.getRemoteSocketAddress()).getAddress();
            } catch (Exception ignored) {

            }
        }
        return result;
    }

    /**
     * Try to get the actual IP address of the client device from the XFF header of given request
     *
     * @param request HTTP Upgrade request
     * @return An {@link IPAddress} instance with the client address or <code>null</code> if no address found
     */
    public InetAddress getClientAddressFromRequest(UpgradeRequest request) {
        final String forwardedForHeader = request.getHeader("X-Forwarded-For");
        return getClientAddressFromXFF(forwardedForHeader);
    }

    /**
     * Try to get the actual IP address of the client device / WEB browser from the request headers
     *
     * @param xForwardedForHeader Value of the X-Forwarded-For header
     * @return An {@link IPAddress} instance with the client address or <code>null</code> if no address found
     */
    public InetAddress getClientAddressFromXFF(String xForwardedForHeader) {
        IPAddress clientAddr = null;

        Log.debug("X-Forwarded-For: {}", xForwardedForHeader);

        if (xForwardedForHeader != null) {
            final List<IPAddress> addresses = parseForwardedFor(xForwardedForHeader);

            // Search for the possible client IP address from the X-Forwarded-For value (list of IP addr)
            // iterating from last to first
            for (int i = addresses.size(); i-- > 0;) {
                final IPAddress a = addresses.get(i);
                if (!isLocal(a)) {
                    clientAddr = a;
                    break;
                }
            }
        }

        if (clientAddr != null) {
            Log.debug("Client IP address from X-Forwarded-For header: {}", clientAddr.toString());
            return clientAddr.toInetAddress();
        }
        return null;
    }

    private boolean isLocal(IPAddress addr) {
        for (final IPAddress localAddr : localAddresses) {
            if (localAddr.contains(addr)) {
                return true;
            }
        }
        return false;
    }

    private static List<IPAddress> parseForwardedFor(String v) {
        if (v == null || v.isBlank()) {
            return Collections.emptyList();
        }

        final String[] items = v.split(",");
        final List<IPAddress> addresses = new ArrayList<>(items.length);

        for (String item : items) {
            item = item.trim();

            final String addr;
            Matcher m = IPv4_REGEXP.matcher(item);

            if (m.matches()) {
                addr = m.group("addr");
            } else {
                m = IPv6_REGEXP.matcher(item);
                if (m.matches()) {
                    addr = m.group("addr");
                } else {
                    addr = null;
                }
            }
            IPAddressString str = new IPAddressString(addr);
            IPAddress ipAddr = str.getAddress();

            if (ipAddr != null) {
                addresses.add(ipAddr);
            } else {
                Log.error("Ignoring invalid address in 'X-Forwarded-For' : {}", item);
            }
        }
        return addresses;
    }
}
