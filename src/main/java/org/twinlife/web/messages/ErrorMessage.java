/*
 *  Copyright (c) 2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.messages;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"msg", "code", "description", "info" })
public class ErrorMessage {

    public static final String NAME = "error";

    public String msg;
    public int code;
    public String description;
    public String info;

    public ErrorMessage() {
        msg = NAME;
    }

    public ErrorMessage(String description) {
        this.msg = NAME;
        this.code = 1;
        this.description = description;
        this.info = null;
    }

    public ErrorMessage(int code, String description) {
        this.msg = NAME;
        this.code = code;
        this.description = description;
        this.info = null;
    }
}
