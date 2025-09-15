package org.twinlife.twinme.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * These capabilities are toggleable (ON/OFF)
 */
public enum ToggleableCap {
    PARSED("parsed", 1, false),
    ADMIN("admin", 1 << 1, false),
    DATA("data", 1 << 2),
    AUDIO("audio", 1 << 3),
    VIDEO("video", 1 << 4),
    ACCEPT_AUDIO("accept-audio", 1 << 5),
    ACCEPT_VIDEO("accept-video", 1 << 6),
    VISIBILITY("visibility", 1 << 7),
    OWNER("owner", 1 << 8, false),
    MODERATE("moderate", 1 << 9),
    INVITE("invite", 1 << 10),
    TRANSFER("transfer", 1 << 11, false),
    /**
     * Indicates whether to accept multiple incoming calls from this Originator.
     * Only applicable to call receivers for now.
     */
    GROUP_CALL("group-call", 1 << 12, false);

    /**
     * Name of the capability.
     */
    @NonNull
    public final String label;

    /**
     * Internal representation of the capability.
     * This can change between versions.
     */
    public final long value;

    public final boolean enabledByDefault;

    ToggleableCap(@NonNull String label, long value) {
        this(label, value, true);
    }

    ToggleableCap(@NonNull String label, long value, boolean enabledByDefault) {
        this.label = label;
        this.value = value;
        this.enabledByDefault = enabledByDefault;
    }

    @Nullable
    static ToggleableCap getByLabel(@NonNull String label) {
        for (ToggleableCap capType : ToggleableCap.values()) {
            if (capType.label.equals(label)) {
                return capType;
            }
        }
        return null;
    }
}
