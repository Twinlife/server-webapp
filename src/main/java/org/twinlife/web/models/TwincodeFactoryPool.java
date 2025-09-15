/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.web.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.AttributeNameStringValue;
import org.twinlife.twinlife.BaseService.AttributeNameListValue;
import org.twinlife.twinlife.BaseService.AttributeNameValue;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.twincode.factory.TwincodeFactoryImpl;
import org.twinlife.twinlife.util.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TwincodeFactoryPool implements RepositoryObject {

    public static final UUID SCHEMA_ID = UUID.fromString("6070a005-7646-4944-bfae-e9d9b13cfe39");
    public static final int SCHEMA_VERSION = 1;

    private final DatabaseIdentifier mDatabaseId;
    private final long mCreationDate;
    @NonNull
    private final UUID mId;
    @NonNull
    private final List<TwincodeFactory> mTwincodes;

    TwincodeFactoryPool(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                        long creationDate, @Nullable String name, @Nullable String description,
                        @Nullable List<AttributeNameValue> attributes,
                        long modificationDate) {

        mDatabaseId = identifier;
        mId = uuid;
        mCreationDate = creationDate;
        mTwincodes = new ArrayList<>();
        update(name, description, attributes, modificationDate);
    }

    void update(@Nullable String name, @Nullable String description, @Nullable List<AttributeNameValue> attributes,
                long modificationDate) {

        if (attributes != null) {
            for (AttributeNameValue attribute : attributes) {
                if ("twincodes".equals(attribute.name)) {
                    if (attribute.value instanceof List) {
                        //noinspection unchecked
                        List<AttributeNameValue> list = (List<AttributeNameValue>) attribute.value;
                        for (AttributeNameValue value : list) {
                            TwincodeFactory factory = createFactory((String) value.value);
                            if (factory != null) {
                                mTwincodes.add(factory);
                            }
                        }
                    }
                }
            }
        }
    }

    @NonNull
    @Override
    public DatabaseIdentifier getDatabaseId() {

        return mDatabaseId;
    }

    @Override
    @NonNull
    public UUID getId() {

        return mId;
    }

    @Override
    @NonNull
    public String getName() {

        return "";
    }

    @Override
    @NonNull
    public String getDescription() {

        return "";
    }

    public boolean isValid() {

        return true;
    }

    @Override
    public long getModificationDate() {

        return 0; // Not used
    }

    @NonNull
    public List<BaseService.AttributeNameValue> getAttributes(boolean exportAll) {
        final List<AttributeNameValue> attributes = new ArrayList<>();
        final List<AttributeNameValue> twincodes = new ArrayList<>();
        for (TwincodeFactory factory : getTwincodeFactories()) {
            StringBuilder sb = new StringBuilder();
            sb.append(factory.getId());
            sb.append(".");
            sb.append(factory.getTwincodeInbound().getId());
            sb.append(".");
            sb.append(factory.getTwincodeOutbound().getId());
            sb.append(".");
            sb.append(factory.getTwincodeSwitchId());
            twincodes.add(new AttributeNameStringValue("twincode", sb.toString()));
        }
        attributes.add(new AttributeNameListValue("twincodes", twincodes));

        return attributes;
    }

    public synchronized boolean isSmaller(@NonNull TwincodeFactoryPool pool) {

        return mTwincodes.size() < pool.mTwincodes.size();
    }

    public synchronized List<TwincodeFactory> getTwincodeFactories() {

        return new ArrayList<>(mTwincodes);
    }

    public synchronized void add(@NonNull TwincodeFactory factory) {

        mTwincodes.add(factory);
    }

    @Override
    @NonNull
    public String toString() {

        return "TwincodeFactoryPool: id=" + mId + " count=" + mTwincodes.size();
    }

    @Nullable
    private static TwincodeFactory createFactory(@NonNull String content) {

        final String[] uuids = content.split("\\.");
        if (uuids.length != 4) {
            return null;
        }

        UUID id = Utils.UUIDFromString(uuids[0]);
        UUID twincodeInboundId = Utils.UUIDFromString(uuids[1]);
        UUID twincodeOutboundId = Utils.UUIDFromString(uuids[2]);
        UUID twincodeSwitchId = Utils.UUIDFromString(uuids[3]);
        if (id == null || twincodeOutboundId == null || twincodeInboundId == null || twincodeSwitchId == null) {
            return null;
        }

        return new TwincodeFactoryImpl(id, 0, twincodeInboundId,
                twincodeOutboundId, twincodeSwitchId, new ArrayList<>());
    }
}

