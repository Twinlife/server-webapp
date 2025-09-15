/*
 *  Copyright (c) 2021-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.web.rest.models;

import org.twinlife.twinme.models.schedule.Schedule;

public class TwincodeInfoBean {

    public String name;
    public String description;
    public String avatarId;
    public boolean audio;
    public boolean video;
    public boolean transfer;



    public Schedule schedule;

    public String getName() {

        return name;
    }

    public void setName(String name) {

        this.name = name;
    }

    public String getDescription() {

        return description;
    }

    public void setDescription(String description) {

        this.description = description;
    }

    public String getAvatarId() {

        return avatarId;
    }

    public void setAvatarId(String avatar) {

        this.avatarId = avatar;
    }
}
