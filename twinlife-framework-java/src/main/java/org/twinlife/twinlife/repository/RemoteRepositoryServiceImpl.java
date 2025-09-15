/*
 *  Copyright (c) 2014-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife.repository;

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.twinlife.twinlife.Connection;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.DatabaseIdentifier;
import org.twinlife.twinlife.Filter;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryObjectFactory;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.Twincode;
import org.twinlife.twinlife.TwincodeInbound;
import org.twinlife.twinlife.TwinlifeImpl;
import org.twinlife.twinlife.datatype.ArrayData;
import org.twinlife.twinlife.util.BinaryErrorPacketIQ;
import org.twinlife.twinlife.util.BinaryPacketIQ;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class RemoteRepositoryServiceImpl extends RepositoryServiceImpl {
    private static final String LOG_TAG = "RemoteRepositoryServ...";
    private static final boolean DEBUG = false;

    private static final UUID CREATE_OBJECT_SCHEMA_ID = UUID.fromString("cc1de051-04c9-49c2-827d-2d8c8545ff41");
    private static final UUID ON_CREATE_OBJECT_SCHEMA_ID = UUID.fromString("fde9aa2f-c0e3-437a-a1d1-0121e72e43bd");
    private static final UUID UPDATE_OBJECT_SCHEMA_ID = UUID.fromString("3bfed52d-0173-4f0d-bfd9-f5d63454ca59");
    private static final UUID ON_UPDATE_OBJECT_SCHEMA_ID = UUID.fromString("0890ec66-0560-4b41-8e65-227119d0b008");
    private static final UUID GET_OBJECT_SCHEMA_ID = UUID.fromString("6dc2169c-1ec8-4c4a-9842-ab26b8484813");
    private static final UUID ON_GET_OBJECT_SCHEMA_ID = UUID.fromString("5fdf06d0-513f-4858-b416-73721f2ce309");
    private static final UUID LIST_OBJECT_SCHEMA_ID = UUID.fromString("7d9baa6c-635e-4bda-b31a-a416322e4eec");
    private static final UUID ON_LIST_OBJECT_SCHEMA_ID = UUID.fromString("76b7a7e2-cd6d-40da-b556-bcbf7eb56da4");
    private static final UUID DELETE_OBJECT_SCHEMA_ID = UUID.fromString("837145fe-2656-41ec-9910-cda6f114ac9a");
    private static final UUID ON_DELETE_OBJECT_SCHEMA_ID = UUID.fromString("64c4f4dd-b7bc-4547-849d-84f5eba047d8");

    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_CREATE_OBJECT_SERIALIZER = CreateObjectIQ.createSerializer(CREATE_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_CREATE_OBJECT_SERIALIZER = OnCreateObjectIQ.createSerializer(ON_CREATE_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_UPDATE_OBJECT_SERIALIZER = UpdateObjectIQ.createSerializer(UPDATE_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_UPDATE_OBJECT_SERIALIZER = OnUpdateObjectIQ.createSerializer(ON_UPDATE_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_GET_OBJECT_SERIALIZER = GetObjectIQ.createSerializer(GET_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_GET_OBJECT_SERIALIZER = OnGetObjectIQ.createSerializer(ON_GET_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_LIST_OBJECT_SERIALIZER = ListObjectIQ.createSerializer(LIST_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_LIST_OBJECT_SERIALIZER = OnListObjectIQ.createSerializer(ON_LIST_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_DELETE_OBJECT_SERIALIZER = GetObjectIQ.createSerializer(DELETE_OBJECT_SCHEMA_ID, 1);
    private static final BinaryPacketIQ.BinaryPacketIQSerializer IQ_ON_DELETE_OBJECT_SERIALIZER = BinaryPacketIQ.createDefaultSerializer(ON_DELETE_OBJECT_SCHEMA_ID, 1);

    @SuppressLint("UseSparseArrays")
    private final HashMap<Long, PendingRequest> mPendingRequests = new HashMap<>();

    private static class PendingRequest {
    }

    private static final class CreateObjectPendingRequest extends PendingRequest {
        @NonNull
        final RepositoryObjectFactoryImpl<?> factory;
        @NonNull
        final List<AttributeNameValue> attributes;
        @Nullable
        final UUID objectKey;
        final boolean immutable;
        @NonNull
        final Consumer<RepositoryObject> complete;

        CreateObjectPendingRequest(@NonNull RepositoryObjectFactoryImpl<?> factory, boolean immutable, @Nullable UUID objectKey,
                                   @NonNull List<AttributeNameValue> attributes,
                                   @NonNull Consumer<RepositoryObject> complete) {
            this.factory = factory;
            this.immutable = immutable;
            this.objectKey = objectKey;
            this.attributes = attributes;
            this.complete = complete;
        }
    }

    private static class GenericGetObjectPendingRequest extends PendingRequest {
        @NonNull
        UUID objectId;
        @NonNull
        final RepositoryObjectFactoryImpl<?> factory;

        GenericGetObjectPendingRequest(@NonNull UUID objectId,
                                       @NonNull RepositoryObjectFactoryImpl<?> factory) {
            this.objectId = objectId;
            this.factory = factory;
        }
    }

    private static final class GetObjectPendingRequest extends GenericGetObjectPendingRequest {
        @NonNull
        final Consumer<RepositoryObject> complete;

        GetObjectPendingRequest(@NonNull UUID objectId,
                                @NonNull RepositoryObjectFactoryImpl<?> factory,
                                @NonNull Consumer<RepositoryObject> complete) {
            super(objectId, factory);
            this.complete = complete;
        }
    }

    private static final class UpdateObjectPendingRequest extends PendingRequest {
        @NonNull
        final RepositoryObject object;
        @NonNull
        final Consumer<RepositoryObject> complete;

        UpdateObjectPendingRequest(@NonNull RepositoryObject object,
                                   @NonNull Consumer<RepositoryObject> complete) {
            this.object = object;
            this.complete = complete;
        }
    }
    private static final class ListObjectPendingRequest extends GenericGetObjectPendingRequest {
        @NonNull
        final Consumer<List<RepositoryObject>> complete;
        @NonNull
        final UUID schemaId;
        @Nullable
        List<UUID> listObjectIds;
        @Nullable
        List<RepositoryObject> list;

        ListObjectPendingRequest(@NonNull Consumer<List<RepositoryObject>> complete,
                                 @NonNull RepositoryObjectFactoryImpl<?> factory,
                                 @NonNull UUID schemaId) {
            super(Twincode.NOT_DEFINED, factory);
            this.complete = complete;
            this.schemaId = schemaId;
            this.listObjectIds = null;
            this.list = null;
        }
    }

    private static final class DeleteObjectPendingRequest extends PendingRequest {
        @NonNull
        final RepositoryObject object;
        @NonNull
        final Consumer<UUID> complete;

        DeleteObjectPendingRequest(@NonNull RepositoryObject object, @NonNull Consumer<UUID> complete) {
            this.object = object;
            this.complete = complete;
        }
    }

    public RemoteRepositoryServiceImpl(@NonNull TwinlifeImpl twinlifeImpl, @NonNull Connection connection,
                                       @NonNull RepositoryObjectFactory<?>[] factories) {

        super(twinlifeImpl, connection, factories);

        // Register the binary IQ handlers for the responses.
        connection.addPacketListener(IQ_ON_CREATE_OBJECT_SERIALIZER, this::onCreateObjectIQ);
        connection.addPacketListener(IQ_ON_GET_OBJECT_SERIALIZER, this::onGetObjectIQ);
        connection.addPacketListener(IQ_ON_UPDATE_OBJECT_SERIALIZER, this::onUpdateObjectIQ);
        connection.addPacketListener(IQ_ON_LIST_OBJECT_SERIALIZER, this::onListObjectIQ);
        connection.addPacketListener(IQ_ON_DELETE_OBJECT_SERIALIZER, this::onDeleteObjectIQ);
    }

    //
    // Override BaseServiceImpl methods
    //

    //
    // Implement RepositoryService interface
    //
    @Override
    public void getObject(@NonNull UUID objectId, @NonNull RepositoryObjectFactory<?> factory,
                          @NonNull Consumer<RepositoryObject> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "getObject: objectId=" + objectId + " factory=" + factory);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final UUID schemaId = factory.getSchemaId();
        final RepositoryObjectFactoryImpl<RepositoryObject> dbFactory = mServiceProvider.getFactory(schemaId);
        if (dbFactory == null) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        final RepositoryObject object = mServiceProvider.loadObject(0, objectId, dbFactory);
        if (object != null) {
            complete.onGet(ErrorCode.SUCCESS, object);
            return;
        }

        if (factory.isLocal()) {
            complete.onGet(ErrorCode.ITEM_NOT_FOUND, null);
            return;
        }

        final long requestId = newRequestId();
        synchronized (this) {
            mPendingRequests.put(requestId, new GetObjectPendingRequest(objectId, dbFactory, complete));
        }

        final GetObjectIQ objectIQ = new GetObjectIQ(IQ_GET_OBJECT_SERIALIZER, requestId, schemaId, objectId);
        sendDataPacket(objectIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void listObjects(@NonNull RepositoryObjectFactory<?> factory,
                            @Nullable Filter<RepositoryObject> filter,
                            @NonNull Consumer<List<RepositoryObject>> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "listObjects: factory=" + factory + " filter=" + filter);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final UUID schemaId = factory.getSchemaId();
        final RepositoryObjectFactoryImpl<RepositoryObject> dbFactory = mServiceProvider.getFactory(schemaId);
        if (dbFactory == null) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        if (ENABLE_DATABASE) {
            List<RepositoryObject> objects = mServiceProvider.listObjects(dbFactory, filter);
            complete.onGet(ErrorCode.SUCCESS, objects);
            return;
        }

        long requestId = newRequestId();
        synchronized (this) {
            mPendingRequests.put(requestId, new ListObjectPendingRequest(complete, dbFactory, schemaId));
        }

        final ListObjectIQ objectIQ = new ListObjectIQ(IQ_LIST_OBJECT_SERIALIZER, requestId, schemaId);
        sendDataPacket(objectIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void updateObject(@NonNull RepositoryObject object,
                             @NonNull Consumer<RepositoryObject> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "updateObject: object=" + object);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        // When the object is local only, update its instance now.
        final DatabaseIdentifier id = object.getDatabaseId();
        if (ENABLE_DATABASE && id.isLocal()) {

            onUpdateObject(object, System.currentTimeMillis(), complete);
            return;
        }

        final TwincodeInbound twincodeInbound = object.getTwincodeInbound();
        final UUID key = twincodeInbound == null ? null : twincodeInbound.getId();
        final String content = serialize(object);
        final long requestId = newRequestId();
        synchronized (this) {
            mPendingRequests.put(requestId, new UpdateObjectPendingRequest(object, complete));
        }

        final UpdateObjectIQ updateObjectIQ = new UpdateObjectIQ(IQ_UPDATE_OBJECT_SERIALIZER, requestId, 0, object.getId(),
                id.getSchemaId(), id.getSchemaVersion(), key, content, null);
        sendDataPacket(updateObjectIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void createObject(@NonNull RepositoryObjectFactory<?> factory, AccessRights accessRights,
                             @NonNull Initializer initializer, @NonNull Consumer<RepositoryObject> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "createObject: accessRights=" + accessRights + " factory=" + factory);
        }

        if (!isServiceOn()) {
            complete.onGet(ErrorCode.SERVICE_UNAVAILABLE, null);
            return;
        }

        final boolean immutable = factory.isImmutable();
        final UUID schemaId = factory.getSchemaId();
        final int schemaVersion = factory.getSchemaVersion();
        final RepositoryObjectFactoryImpl<?> dbFactory = mServiceProvider.getFactory(schemaId);
        if (dbFactory == null) {
            complete.onGet(ErrorCode.BAD_REQUEST, null);
            return;
        }

        if (factory.isLocal()) {
            final RepositoryObject object = mServiceProvider.createObject(UUID.randomUUID(), dbFactory, initializer);
            complete.onGet(object == null ? ErrorCode.NO_STORAGE_SPACE : ErrorCode.SUCCESS, object);
            return;
        }

        int createOptions;
        switch (accessRights) {
            case PUBLIC:
                createOptions = CreateObjectIQ.PUBLIC;
                break;
            case EXCLUSIVE:
                createOptions = CreateObjectIQ.EXCLUSIVE;
                break;
            case PRIVATE:
            default:
                createOptions = CreateObjectIQ.PRIVATE;
                break;
        }

        if (immutable) {
            createOptions |= CreateObjectIQ.IMMUTABLE;
        }

        long now = System.currentTimeMillis();
        DatabaseIdentifier temp = new DatabaseIdentifier(dbFactory, 0);
        RepositoryObject object = factory.createObject(temp, UUID.randomUUID(), now, null, null, null, 0);
        initializer.initialize(object);

        final TwincodeInbound twincodeInbound = object.getTwincodeInbound();
        final UUID key = twincodeInbound == null ? null : twincodeInbound.getId();
        final List<AttributeNameValue> attributes = object.getAttributes(true);
        final String content = serialize(object);
        final long requestId = newRequestId();
        synchronized (this) {
            mPendingRequests.put(requestId, new CreateObjectPendingRequest(dbFactory, immutable, key, attributes, complete));
        }

        final CreateObjectIQ createObjectIQ = new CreateObjectIQ(IQ_CREATE_OBJECT_SERIALIZER, requestId, createOptions,
                schemaId, schemaVersion, key, content, null);
        sendDataPacket(createObjectIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    @Override
    public void deleteObject(@NonNull RepositoryObject object, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "deleteObject: object=" + object);
        }

        if (!isServiceOn()) {

            return;
        }

        final DatabaseIdentifier id = object.getDatabaseId();

        // When the object is local only, delete its instance now.
        if (ENABLE_DATABASE && id.isLocal()) {

            onDeleteObject(object, complete);
            return;
        }

        final long requestId = newRequestId();
        synchronized (this) {
            mPendingRequests.put(requestId, new DeleteObjectPendingRequest(object, complete));
        }

        final GetObjectIQ objectIQ = new GetObjectIQ(IQ_DELETE_OBJECT_SERIALIZER, requestId, id.getSchemaId(), object.getId());
        sendDataPacket(objectIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    //
    // Private Methods
    //

    @NonNull
    private String serialize(@NonNull RepositoryObject object) {
        if (DEBUG) {
            Log.d(LOG_TAG, "serialize: object=" + object);
        }

        List<AttributeNameValue> attributes = object.getAttributes(true);
        ArrayData arrayData = new ArrayData();
        arrayData.addData(attributes);

        StringBuilder stringBuilder = new StringBuilder();
        arrayData.toXml(stringBuilder);

        return stringBuilder.toString();
    }

    private void onGetObjectIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onGetObjectIQ iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (!(request instanceof GenericGetObjectPendingRequest) || !(iq instanceof OnGetObjectIQ)) {
            return;
        }

        final OnGetObjectIQ onGetObjectIQ = (OnGetObjectIQ) iq;
        List<AttributeNameValue> attributes = deserializeContent(onGetObjectIQ.getObjectData());
        if (attributes == null) {
            attributes = new ArrayList<>();
        }

        final GenericGetObjectPendingRequest genericGetRequest = (GenericGetObjectPendingRequest) request;
        final DatabaseIdentifier id = new DatabaseIdentifier(genericGetRequest.factory, requestId);
        final RepositoryObject object = genericGetRequest.factory.getFactory().importObject(genericGetRequest.factory, id,
                genericGetRequest.objectId, onGetObjectIQ.getObjectKey(),
                onGetObjectIQ.getCreationDate(), attributes);
        if (request instanceof ListObjectPendingRequest) {
            final ListObjectPendingRequest listRequest = (ListObjectPendingRequest) request;
            listRequest.list.add(object);
            nextObject(listRequest);
        } else {
            final GetObjectPendingRequest getObjectPendingRequest = (GetObjectPendingRequest) request;
            getObjectPendingRequest.complete.onGet(object == null ? ErrorCode.NO_STORAGE_SPACE : ErrorCode.SUCCESS, object);
        }
    }

    private void onListObjectIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onListObjectIQ iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (!(request instanceof ListObjectPendingRequest) || !(iq instanceof OnListObjectIQ)) {
            return;
        }

        final OnListObjectIQ onListObjectIQ = (OnListObjectIQ) iq;
        final ListObjectPendingRequest listObjectPendingRequest = (ListObjectPendingRequest) request;
        listObjectPendingRequest.listObjectIds = onListObjectIQ.getObjectIds();
        listObjectPendingRequest.list = new ArrayList<>();

        nextObject(listObjectPendingRequest);
    }

    private void nextObject(@NonNull ListObjectPendingRequest listRequest) {
        if (DEBUG) {
            Log.d(LOG_TAG, "nextObject listRequest=" + listRequest);
        }

        if (listRequest.listObjectIds == null || listRequest.listObjectIds.isEmpty()) {
            listRequest.complete.onGet(ErrorCode.SUCCESS, listRequest.list);
            return;
        }

        final long requestId = newRequestId();
        final UUID objectId;
        synchronized (this) {
            mPendingRequests.put(requestId, listRequest);
            objectId = listRequest.listObjectIds.remove(0);
            listRequest.objectId = objectId;
        }

        final GetObjectIQ objectIQ = new GetObjectIQ(IQ_GET_OBJECT_SERIALIZER, requestId,
                listRequest.schemaId, objectId);
        sendDataPacket(objectIQ, DEFAULT_REQUEST_TIMEOUT);
    }

    private void onCreateObjectIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onCreateObjectIQ iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (!(request instanceof CreateObjectPendingRequest) || !(iq instanceof OnCreateObjectIQ)) {
            return;
        }

        final CreateObjectPendingRequest createObjectPendingRequest = (CreateObjectPendingRequest) request;
        final OnCreateObjectIQ onCreateObjectIQ = (OnCreateObjectIQ) iq;
        if (!ENABLE_DATABASE) {
            final DatabaseIdentifier id = new DatabaseIdentifier(createObjectPendingRequest.factory, requestId);
            // final RepositoryObject object = createObjectPendingRequest.factory.getFactory().createObject(ident, onCreateObjectIQ.getObjectId(),
            //        onCreateObjectIQ.getCreationDate(), )
            final RepositoryObject object = createObjectPendingRequest.factory.getFactory().importObject(createObjectPendingRequest.factory, id,
                    onCreateObjectIQ.getObjectId(), createObjectPendingRequest.objectKey,
                    onCreateObjectIQ.getCreationDate(), createObjectPendingRequest.attributes);
            createObjectPendingRequest.complete.onGet(object == null ? ErrorCode.NO_STORAGE_SPACE : ErrorCode.SUCCESS, object);
            return;
        }
        final RepositoryObject object = mServiceProvider.importObject(onCreateObjectIQ.getObjectId(),
                onCreateObjectIQ.getCreationDate(), createObjectPendingRequest.factory,
                createObjectPendingRequest.attributes, createObjectPendingRequest.objectKey);
        createObjectPendingRequest.complete.onGet(object == null ? ErrorCode.NO_STORAGE_SPACE : ErrorCode.SUCCESS, object);
    }

    private void onDeleteObjectIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteObject iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (!(request instanceof DeleteObjectPendingRequest)) {
            return;
        }

        final DeleteObjectPendingRequest deleteObjectPendingRequest = (DeleteObjectPendingRequest) request;
        onDeleteObject(deleteObjectPendingRequest.object, deleteObjectPendingRequest.complete);
    }

    protected void onDeleteObject(@NonNull RepositoryObject object, @NonNull Consumer<UUID> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onDeleteObject object=" + object);
        }

        final UUID id = object.getId();

        if (ENABLE_DATABASE) {
            mServiceProvider.deleteObject(object);
        }
        complete.onGet(ErrorCode.SUCCESS, id);

        for (RepositoryService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onDeleteObject(id));
        }
    }

    protected void onUpdateObject(@NonNull RepositoryObject object, long modificationDate,
                                  @NonNull Consumer<RepositoryObject> complete) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObject object=" + object + " modificationDate=" + modificationDate);
        }

        if (ENABLE_DATABASE) {
            mServiceProvider.updateObject(object, modificationDate);
        }

        complete.onGet(ErrorCode.SUCCESS, object);
        for (RepositoryService.ServiceObserver serviceObserver : getServiceObservers()) {
            mTwinlifeExecutor.execute(() -> serviceObserver.onUpdateObject(object));
        }
    }

    private void onUpdateObjectIQ(@NonNull BinaryPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onUpdateObjectIQ iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        receivedIQ(requestId);

        // Get the pending request or terminate.
        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (!(request instanceof UpdateObjectPendingRequest) || !(iq instanceof OnUpdateObjectIQ)) {
            return;
        }

        final OnUpdateObjectIQ onUpdateObjectIQ = (OnUpdateObjectIQ) iq;
        final UpdateObjectPendingRequest updateObjectPendingRequest = (UpdateObjectPendingRequest) request;
        onUpdateObject(updateObjectPendingRequest.object, onUpdateObjectIQ.getModificationDate(), updateObjectPendingRequest.complete);
    }

    @Override
    protected void onErrorPacket(@NonNull BinaryErrorPacketIQ iq) {
        if (DEBUG) {
            Log.d(LOG_TAG, "onError: iq=" + iq);
        }

        final long requestId = iq.getRequestId();
        final ErrorCode errorCode = iq.getErrorCode();
        receivedIQ(requestId);

        final PendingRequest request;
        synchronized (mPendingRequests) {
            request = mPendingRequests.remove(requestId);
        }
        if (request == null) {
            return;
        }

        if (request instanceof GetObjectPendingRequest) {
            final GetObjectPendingRequest getObjectPendingRequest = (GetObjectPendingRequest) request;
            if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                mServiceProvider.deleteUUID(getObjectPendingRequest.objectId);
            }
            getObjectPendingRequest.complete.onGet(errorCode, null);

        } else if (request instanceof UpdateObjectPendingRequest) {
            final UpdateObjectPendingRequest updateObjectPendingRequest = (UpdateObjectPendingRequest) request;
            if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                mServiceProvider.deleteObject(updateObjectPendingRequest.object);
            }
            updateObjectPendingRequest.complete.onGet(errorCode, null);

        } else if (request instanceof ListObjectPendingRequest) {
            final ListObjectPendingRequest listObjectPendingRequest = (ListObjectPendingRequest) request;
            listObjectPendingRequest.complete.onGet(errorCode, null);

        } else if (request instanceof DeleteObjectPendingRequest) {
            final DeleteObjectPendingRequest deleteObjectPendingRequest = (DeleteObjectPendingRequest) request;
            if (errorCode == ErrorCode.ITEM_NOT_FOUND) {
                mServiceProvider.deleteObject(deleteObjectPendingRequest.object);
            }
            deleteObjectPendingRequest.complete.onGet(errorCode, null);
        }
    }
}
