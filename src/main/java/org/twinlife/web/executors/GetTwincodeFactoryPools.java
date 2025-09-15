/*
 *  Copyright (c) 2023-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.web.executors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.web.models.TwincodeFactoryPool;
import org.twinlife.web.models.TwincodeFactoryPoolFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Get the TwincodeFactoryPool objects that have been created.
 *
 * - we get the list of objects from the server since we don't have a database,
 * - if the list is empty, we create a TwincodeFactoryPool instance and populate it with twincodes.
 */
public class GetTwincodeFactoryPools extends TwinlifeContext.DefaultObserver {
    static final Logger Log = LogManager.getLogger(GetTwincodeFactoryPools.class);

    private static final int LIST_TWINCODE_FACTORY_POOL = 1;
    private static final int LIST_TWINCODE_FACTORY_POOL_DONE = 1 << 1;
    private static final int CREATE_TWINCODE_FACTORY_POOL = 1 << 4;
    private static final int CREATE_TWINCODE_FACTORY_POOL_DONE = 1 << 5;
    private static final int CREATE_TWINCODE = 1 << 6;
    private static final int CREATE_TWINCODE_DONE = 1 << 7;

    private final TwinlifeContext mTwinlifeContext;
    private final RepositoryService mRepositoryService;
    private final List<TwincodeFactoryPool> mTwincodePools = new ArrayList<>();
    private final Consumer<List<TwincodeFactoryPool>> mConsumer;
    private boolean mRestarted;
    private boolean mStopped;
    private int mState;
    private TwincodeFactoryPool mFactoryPool;
    private int mCreateTwincode;

    public GetTwincodeFactoryPools(@NonNull TwinlifeContext twinlifeContext,
                                   @NonNull Consumer<List<TwincodeFactoryPool>> consumer) {
        Log.info("Get twincode factory pools");

        mRestarted = false;
        mStopped = false;
        mState = 0;
        mTwinlifeContext = twinlifeContext;
        mConsumer = consumer;
        mCreateTwincode = 0;
        mRepositoryService = twinlifeContext.getRepositoryService();
        mTwinlifeContext.setObserver(this);
    }

    //
    // Private methods
    //

    @Override
    public void onTwinlifeOnline() {
        Log.debug("Online to get the twincode factory pools state={}", mState);

        if (mRestarted) {
            mRestarted = false;

            if ((mState & LIST_TWINCODE_FACTORY_POOL) != 0 && (mState & LIST_TWINCODE_FACTORY_POOL_DONE) == 0) {
                mState &= ~LIST_TWINCODE_FACTORY_POOL;
            }

            if ((mState & CREATE_TWINCODE_FACTORY_POOL) != 0 && (mState & CREATE_TWINCODE_FACTORY_POOL_DONE) == 0) {
                mState &= ~CREATE_TWINCODE_FACTORY_POOL;
            }
        }

        onOperation();
    }

    protected void onOperation() {
        Log.debug("Operation state={}", mState);

        if (mStopped || !mTwinlifeContext.isConnected()) {

            return;
        }

        //
        // Step 1: get the twincode factory pool object ids.
        //
        if ((mState & LIST_TWINCODE_FACTORY_POOL) == 0) {
            mState |= LIST_TWINCODE_FACTORY_POOL;

            long requestId = mTwinlifeContext.newRequestId();
            Log.debug("Request {} to list factory pool objects", requestId);
            mRepositoryService.listObjects(TwincodeFactoryPoolFactory.INSTANCE, null, this::onListObjects);
            return;
        }
        if ((mState & LIST_TWINCODE_FACTORY_POOL_DONE) == 0) {
            return;
        }

        //
        // Step 3: create a twincode factory pool object if the list is empty.
        //
        if (mTwincodePools.isEmpty()) {
            if ((mState & CREATE_TWINCODE_FACTORY_POOL) == 0) {
                mState |= CREATE_TWINCODE_FACTORY_POOL;

                mRepositoryService.createObject(TwincodeFactoryPoolFactory.INSTANCE,
                        RepositoryService.AccessRights.PRIVATE, (RepositoryObject object) -> {

                        }, this::onCreateObject);
                return;
            }
            if ((mState & CREATE_TWINCODE_FACTORY_POOL_DONE) == 0) {
                return;
            }
        }

        //
        // Step 4: populate the created pool factory object with several twincodes.
        //
        if (mFactoryPool != null && mCreateTwincode > 0) {
            if ((mState & CREATE_TWINCODE) == 0) {
                mState |= CREATE_TWINCODE;

                new CreateTwincodeExecutor(mTwinlifeContext, mFactoryPool,
                        (ErrorCode errorCode, TwincodeFactory twincodeFactory) -> {
                    if (mCreateTwincode > 0) {
                        mCreateTwincode--;
                        mState &= ~CREATE_TWINCODE;
                    } else {
                        mState |= CREATE_TWINCODE_DONE;
                    }
                    onOperation();
                });
                return;
            }
            if ((mState & CREATE_TWINCODE_DONE) == 0) {
                return;
            }
        }

        //
        // Last Step
        //

        mConsumer.onGet(ErrorCode.SUCCESS, mTwincodePools);

        stop();
    }

    private void onListObjects(@NonNull ErrorCode errorCode, @Nullable List<RepositoryObject> list) {
        Log.debug("Get factory pool object errorCode={} count={}",
                errorCode, list == null ? 0 : list.size());

        // Wait for reconnection
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }

        mState |= LIST_TWINCODE_FACTORY_POOL_DONE;
        if (list != null) {
            for (RepositoryObject factory : list) {
                TwincodeFactoryPool factoryPool = (TwincodeFactoryPool) factory;
                mTwincodePools.add(factoryPool);
                Log.debug("Twincode factory {}", factoryPool);
                if (factoryPool.getTwincodeFactories().size() < 10) {
                    mFactoryPool = factoryPool;
                    mCreateTwincode = 10;
                }
            }
        }
        onOperation();
    }

    private void onCreateObject(ErrorCode errorCode, @Nullable RepositoryObject object) {
        Log.debug("Create factory pool {} errorCode {}", object, errorCode);

        // Wait for reconnection
        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;

            return;
        }

        if (errorCode == ErrorCode.SUCCESS && object instanceof TwincodeFactoryPool) {
            mFactoryPool = (TwincodeFactoryPool) object;
            mTwincodePools.add((TwincodeFactoryPool) object);
            // Populate with 10 twincodes.
            mCreateTwincode = 10;
        }

        mState |= CREATE_TWINCODE_FACTORY_POOL_DONE;
        onOperation();
    }

    protected void stop() {

        mStopped = true;
        mTwinlifeContext.removeObserver(this);
    }
}
