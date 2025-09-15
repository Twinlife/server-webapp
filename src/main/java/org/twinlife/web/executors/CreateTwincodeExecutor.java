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
import org.twinlife.twinlife.BaseService;
import org.twinlife.twinlife.BaseService.ErrorCode;
import org.twinlife.twinlife.Consumer;
import org.twinlife.twinlife.RepositoryObject;
import org.twinlife.twinlife.RepositoryService;
import org.twinlife.twinlife.TwincodeFactory;
import org.twinlife.twinlife.TwinlifeContext;
import org.twinlife.web.models.TwincodeFactoryPool;

import java.util.ArrayList;
import java.util.List;

/**
 * Create a twincode for the twincode factory pool.
 *
 * - create the twincode with the TwincodeFactoryService,
 * - save the set of 4 twincodes in the TwincodeFactoryPool instance on the server.
 */
public class CreateTwincodeExecutor extends TwinlifeContext.DefaultObserver {
    static final Logger Log = LogManager.getLogger(CreateTwincodeExecutor.class);

    private static final int CREATE_TWINCODE = 1 << 1;
    private static final int CREATE_TWINCODE_DONE = 1 << 2;
    private static final int UPDATE_FACTORY = 1 << 3;
    private static final int UPDATE_FACTORY_DONE = 1 << 4;

    private final TwinlifeContext mTwinlifeContext;
    private final Consumer<TwincodeFactory> mComplete;
    private final TwincodeFactoryPool mFactoryPool;
    private final RepositoryService mRepositoryService;
    private boolean mRestarted;
    private boolean mStopped;
    private int mState;
    private TwincodeFactory mTwincodeFactory;

    public CreateTwincodeExecutor(@NonNull TwinlifeContext twinlifeContext,
                                  @NonNull TwincodeFactoryPool pool,
                                  @NonNull Consumer<TwincodeFactory> complete) {
        Log.info("Create twincode factory for pool {}", pool.getId());

        mRestarted = false;
        mStopped = false;
        mState = 0;
        mTwinlifeContext = twinlifeContext;
        mFactoryPool = pool;
        mComplete = complete;
        mRepositoryService = twinlifeContext.getRepositoryService();
        twinlifeContext.setObserver(this);
    }

    public void onTwinlifeOnline() {
        Log.info("Online to create twincode factory for pool {} state {}",
                mFactoryPool.getId(), mState);

        if (mRestarted) {
            mRestarted = false;

            if ((mState & CREATE_TWINCODE) != 0 && (mState & CREATE_TWINCODE_DONE) == 0) {
                mState &= ~CREATE_TWINCODE;
            }
            if ((mState & UPDATE_FACTORY) != 0 && (mState & UPDATE_FACTORY_DONE) == 0) {
                mState &= ~UPDATE_FACTORY;
            }
        }
        onOperation();
    }

    //
    // Private methods
    //

    protected void onOperation() {
        Log.debug("Operation state={}", mState);

        if (mStopped) {

            return;
        }

        //
        // Step 1: create the public profile twincode.
        //
        if ((mState & CREATE_TWINCODE) == 0) {
            mState |= CREATE_TWINCODE;

            List<BaseService.AttributeNameValue> twincodeFactoryAttributes = new ArrayList<>();
            List<BaseService.AttributeNameValue> twincodeOutboundAttributes = new ArrayList<>();

            long requestId = mTwinlifeContext.newRequestId();
            Log.debug("Request {} to create twincode for pool {}", requestId, mFactoryPool.getId());
            mTwinlifeContext.getTwincodeFactoryService().createTwincode(twincodeFactoryAttributes, null,
                    twincodeOutboundAttributes, null, TwincodeFactoryPool.SCHEMA_ID,
                    this::onCreateTwincodeFactory);
            return;
        }
        if ((mState & CREATE_TWINCODE_DONE) == 0) {
            return;
        }

        //
        // Step 2: save the twincode factory pool object on the server.
        //
        if ((mState & UPDATE_FACTORY) == 0) {
            mState |= UPDATE_FACTORY;

            mRepositoryService.updateObject(mFactoryPool, this::onUpdateObject);
            return;
        }
        if ((mState & UPDATE_FACTORY_DONE) == 0) {
            return;
        }

        //
        // Last Step
        //
        mComplete.onGet(ErrorCode.SUCCESS, mTwincodeFactory);

        stop();
    }

    private void onCreateTwincodeFactory(@NonNull ErrorCode errorCode, @Nullable TwincodeFactory twincodeFactory) {
        Log.debug("Create twincode result {}", errorCode);

        if (errorCode != ErrorCode.SUCCESS || twincodeFactory == null) {

            if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
                mRestarted = true;
                return;
            }

            mComplete.onGet(errorCode, null);
            stop();
            return;
        }

        mState |= CREATE_TWINCODE_DONE;
        mTwincodeFactory = twincodeFactory;
        mFactoryPool.add(twincodeFactory);
        onOperation();
    }

    private void onUpdateObject(@NonNull ErrorCode errorCode, @Nullable RepositoryObject object) {

        if (errorCode == ErrorCode.TWINLIFE_OFFLINE) {
            mRestarted = true;
            return;
        }

        mState |= UPDATE_FACTORY_DONE;
        onOperation();
    }

    protected void stop() {
        Log.debug("Stopping create twincode for pool {}", mFactoryPool.getId());

        mStopped = true;
        mTwinlifeContext.removeObserver(this);
    }
}
