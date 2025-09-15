/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.web.messages;

import androidx.annotation.NonNull;
import org.twinlife.twinlife.TransportCandidate;

/**
 * Transport Candidate
 */
public class CandidateInfo {

    public int sdpMLineIndex;
    public String sdpMid;
    public String candidate;
    public boolean removed;

    public CandidateInfo() {
    }

    public CandidateInfo(@NonNull TransportCandidate candidate) {
        this.sdpMLineIndex = candidate.id;
        this.sdpMid = candidate.label;
        this.candidate = candidate.sdp;
        this.removed = candidate.removed;
    }
}
