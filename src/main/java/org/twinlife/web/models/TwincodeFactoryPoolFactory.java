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
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.RepositoryImportService;
import org.twinlife.twinlife.RepositoryObjectFactory;

import java.util.List;
import java.util.UUID;

/**
 * Factory used by the RepositoryService to create SpaceSettings object.
 */
public class TwincodeFactoryPoolFactory implements RepositoryObjectFactory<TwincodeFactoryPool> {
    public static final TwincodeFactoryPoolFactory INSTANCE = new TwincodeFactoryPoolFactory();

    @Override
    @NonNull
    public TwincodeFactoryPool createObject(@NonNull DatabaseIdentifier identifier, @NonNull UUID uuid,
                                      long creationDate, @Nullable String name, @Nullable String description,
                                      @Nullable List<BaseService.AttributeNameValue> attributes,
                                      long modificationDate) {
        return new TwincodeFactoryPool(identifier, uuid, creationDate, name, description,
                attributes, modificationDate);
    }
    @Override
    @Nullable
    public TwincodeFactoryPool importObject(@NonNull RepositoryImportService upgradeService,
                                            @NonNull DatabaseIdentifier identifier, @NonNull UUID uuid, @Nullable UUID key,
                                            long creationDate, @NonNull List<BaseService.AttributeNameValue> attributes) {
        String name = null, description = null;
        for (BaseService.AttributeNameValue attribute : attributes) {
            switch (attribute.name) {
                case "name":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        name = (String) ((BaseService.AttributeNameStringValue) attribute).value;
                    }
                    break;

                case "description":
                    if (attribute instanceof BaseService.AttributeNameStringValue) {
                        description = (String) attribute.value;
                    }
                    break;
            }
        }

        return new TwincodeFactoryPool(identifier, uuid, creationDate, name, description, attributes, creationDate);
    }

    @Override
    public void loadObject(@NonNull TwincodeFactoryPool object, String name, String description,
                           @Nullable List<BaseService.AttributeNameValue> attributes, long modificationDate) {

        object.update(name, description, attributes, modificationDate);
    }

    @NonNull
    @Override
    public UUID getSchemaId() {

        return TwincodeFactoryPool.SCHEMA_ID;
    }

    @Override
    public int getSchemaVersion() {

        return TwincodeFactoryPool.SCHEMA_VERSION;
    }

    @Override
    public int getTwincodeUsage() {

        return 0;
    }

    @Override
    public boolean isLocal() {

        return false;
    }

    @Override
    public boolean isImmutable() {

        return false;
    }

    @Override
    @Nullable
    public RepositoryObjectFactory<?> getOwnerFactory() {

        return null;
    }

    private TwincodeFactoryPoolFactory() {
    }
}