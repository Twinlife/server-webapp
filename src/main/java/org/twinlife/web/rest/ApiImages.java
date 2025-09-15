/*
 *  Copyright (c) 2021-2023 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.rest;

import android.graphics.Bitmap;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.web.ProxyApplication;
import org.twinlife.web.ProxyController;

import java.util.UUID;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("images")
public class ApiImages extends Api {
    static final Logger Log = LogManager.getLogger(ApiImages.class);

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Path("{imageId}")
    public void getImage(@PathParam("imageId") String image,
                         @Suspended final AsyncResponse asyncResponse) {

        Log.info("getImage with id {}", image);

        final UUID imageId = Utils.UUIDFromString(image);
        if (imageId == null) {

            Log.error("Image {} is not valid", image);
            error(asyncResponse, ErrorCode.ITEM_NOT_FOUND, image);
            return;
        }

        final ProxyApplication application = ProxyController.getProxyApplication();
        if (application == null) {

            error(asyncResponse, ErrorCode.SERVICE_UNAVAILABLE, image);
            return;
        }

        application.getImage(imageId, (ErrorCode status, Bitmap result) -> {
            if (status != ErrorCode.SUCCESS || result == null) {
                if (status == ErrorCode.TWINLIFE_OFFLINE) {
                    error(asyncResponse, ErrorCode.SERVICE_UNAVAILABLE, image);
                    return;
                }
                error(asyncResponse, ErrorCode.ITEM_NOT_FOUND, image);

            } else {
                byte[] data = result.getBytes();

                asyncResponse.resume(Response.ok(data, "image/jpg").header("Access-Control-Allow-Origin", "*").build());
            }
        });
    }
}
