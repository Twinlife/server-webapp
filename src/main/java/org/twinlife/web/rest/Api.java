/*
 *  Copyright (c) 2021-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.rest;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import jakarta.ws.rs.OPTIONS;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.web.rest.models.ErrorBean;

/**
 * A helper function that declares common useful operations for real Api classes.
 */
public class Api {
    static final Logger Log = LogManager.getLogger(Api.class);

    protected void error(@NonNull final AsyncResponse asyncResponse,
                         @NonNull final ErrorCode errorCode,
                         @Nullable final String parameter) {
        Log.error("Return error {}", errorCode);
        ErrorBean error = new ErrorBean();
        error.setParameter(parameter);

        final Response.ResponseBuilder responseBuilder;
        if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
            error.setMessage("item was not found");
            error.setCode(1);
            responseBuilder = Response.status(Response.Status.NOT_FOUND);

        } else if (errorCode == ErrorCode.SERVICE_UNAVAILABLE) {
            error.setMessage("service is not available, try again later");
            error.setCode(100);
            responseBuilder = Response.status(Response.Status.SERVICE_UNAVAILABLE);

        } else if (errorCode == ErrorCode.BAD_REQUEST) {
            error.setMessage("invalid request or parameter");
            error.setCode(2);
            responseBuilder = Response.status(Response.Status.BAD_REQUEST);

        } else if (errorCode == ErrorCode.NO_STORAGE_SPACE) {
            error.setMessage("not enough space on server");
            error.setCode(200);
            responseBuilder = Response.status(Response.Status.REQUEST_ENTITY_TOO_LARGE);

        } else if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            error.setMessage("server is offline, try again later");
            error.setCode(101);
            responseBuilder = Response.status(Response.Status.SERVICE_UNAVAILABLE);

        } else {
            error.setMessage("server internal error, something wrong happened");
            error.setCode(300);
            responseBuilder = Response.status(Response.Status.INTERNAL_SERVER_ERROR);
        }
        asyncResponse.resume(responseBuilder.entity(error).type(MediaType.APPLICATION_JSON).build());
    }

    @OPTIONS
    public Response options() {
        Log.error("CORS request");

        Response.ResponseBuilder result = Response.noContent().header("Access-Control-Allow-Origin", "*");
        result.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD");
        result.header("Access-Control-Allow-Credentials", "true");
        result.header("Access-Control-Allow-Headers", "Origin, X-Requested-With, Content-Type, Accept");
        return result.build();
    }

}
