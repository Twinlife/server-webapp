/*
 *  Copyright (c) 2021-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.rest;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.TwincodeOutbound;
import org.twinlife.twinlife.util.Utils;
import org.twinlife.twinme.models.Capabilities;
import org.twinlife.twinme.models.TwincodeKind;
import org.twinlife.web.ProxyApplication;
import org.twinlife.web.ProxyController;
import org.twinlife.web.rest.models.TwincodeInfoBean;

import java.util.UUID;

/**
 * Root resource (exposed at "myresource" path)
 */
@Path("twincodes")
public class ApiTwincodes extends Api {
    static final Logger Log = LogManager.getLogger(ApiTwincodes.class);

    //
    // Twincode Attributes
    //

    public static final String TWINCODE_ATTRIBUTE_AVATAR_ID = "avatarId";
    private static final String TWINCODE_ATTRIBUTE_CREATED_BY = "created-by";
    private static final String TWINCODE_ATTRIBUTE_INVITED_BY = "invited-by";
    private static final String TWINCODE_ATTRIBUTE_INVITATION_KIND = "invitationKind";
    private static final String TWINCODE_ATTRIBUTE_CHANNEL = "channel";
    private static final String TWINCODE_ATTRIBUTE_PERMISSIONS = "permissions";
    public static final String TWINCODE_ATTRIBUTE_DESCRIPTION = "description";
    private static final String TWINCODE_ATTRIBUTE_ACCOUNT_MIGRATION = "accountMigration";

    private static final String TWINCODE_ATTRIBUTE_TWINCODE_OUTBOUND_ID = "twincodeOutboundId";

    /**
     * Method handling HTTP GET requests. The returned object will be sent
     * to the client as "text/plain" media type.
     *
     * @return String that will be returned as a text/plain response.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("{twincodeId}")
    public void getTwincode(@PathParam("twincodeId") String twincode,
                            @Suspended final AsyncResponse asyncResponse) {

        Log.info("getTwincode with twincode {}", twincode);

        final UUID twincodeId = Utils.toUUID(twincode);
        if (twincodeId == null) {

            Log.error("Twincode {} is not valid", twincode);
            error(asyncResponse, ErrorCode.ITEM_NOT_FOUND, twincode);
            return;
        }

        final ProxyApplication application = ProxyController.getProxyApplication();
        if (application == null) {

            error(asyncResponse, ErrorCode.SERVICE_UNAVAILABLE, twincode);
            return;
        }

        application.getTwincode(twincodeId, (ErrorCode status, TwincodeOutbound result) -> {
            if (status != ErrorCode.SUCCESS || result == null) {
                error(asyncResponse, status, twincode);
            } else {
                final TwincodeInfoBean bean = new TwincodeInfoBean();
                bean.name = result.getName();
                bean.description = result.getDescription();
                UUID avatarId = result.getAvatarId();
                String capabilities = result.getCapabilities();
                Capabilities cap = capabilities == null ? new Capabilities() : new Capabilities(capabilities);
                bean.audio = cap.getKind() == TwincodeKind.CALL_RECEIVER && cap.hasAudio();
                bean.video = cap.getKind() == TwincodeKind.CALL_RECEIVER && cap.hasVideo();
                bean.transfer = cap.getKind() == TwincodeKind.CALL_RECEIVER && cap.hasTransfer();
                if (avatarId != null) {
                    bean.avatarId = avatarId.toString();
                }

                bean.schedule = cap.getSchedule();

                asyncResponse.resume(Response.ok(bean).header("Access-Control-Allow-Origin", "*").build());
            }
        });
    }
}
