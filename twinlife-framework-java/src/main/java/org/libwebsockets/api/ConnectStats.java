/*
 *  Copyright (c) 2021-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.libwebsockets.api;

public class ConnectStats {
    public final long dnsTime;
    public final long tcpConnectTime;
    public final long tlsConnectTime;
    public final long txnResponseTime;
    public final long connectCount;

    public ConnectStats(long dnsTime, long tcpConnectTime, long tlsConnectTime, long txnResponseTime, long connectCount) {
        this.dnsTime = dnsTime;
        this.tcpConnectTime = tcpConnectTime;
        this.tlsConnectTime = tlsConnectTime;
        this.txnResponseTime = txnResponseTime;
        this.connectCount = connectCount;
    }
}
