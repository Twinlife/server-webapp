/*
 *  Copyright (c) 2019-2025 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.twinlife;

import android.util.Log;
import androidx.annotation.NonNull;
import org.twinlife.twinlife.util.Utils;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Store some configuration properties in an encrypted file.
 * The Android implementation uses the KeyStore to store the raw data (`mData`).
 * With the engine Java implementation, the raw data is encrypted with AES/GCM/NoPadding so that
 * it is both encrypted and authenticated.
 */
public class FileSecuredConfigurationImpl implements ConfigurationService.SecuredConfiguration {
    private static final String LOG_TAG = "FileSecuredConfigurationImpl";
    public static final String AES_ALGORITHM = "AES";
    public static final String AES_ALGORITHM_GCM = "AES/GCM/NoPadding";
    public static final Integer IV_LENGTH_ENCRYPT = 12;
    public static final Integer TAG_LENGTH_ENCRYPT = 16;

    @NonNull
    private final File mPath;
    @NonNull
    private final SecretKey mEncryptionKey;
    private byte[] mData;

    FileSecuredConfigurationImpl(@NonNull SecretKey encryptionKey, @NonNull File path) {
        mPath = new File(path.getPath() + ".prv");

        byte[] keyBytes = encryptionKey.getEncoded();
        mEncryptionKey = new SecretKeySpec(keyBytes, AES_ALGORITHM);

        if (mPath.exists()) {
            try (FileInputStream fs = new FileInputStream(mPath)) {
                Cipher cipher = Cipher.getInstance(AES_ALGORITHM_GCM);
                int len = (int) mPath.length();
                if (len < IV_LENGTH_ENCRYPT) {
                    len = IV_LENGTH_ENCRYPT;
                }
                byte[] data = new byte[len - IV_LENGTH_ENCRYPT];
                byte[] iv = new byte[IV_LENGTH_ENCRYPT];
                if (fs.read(iv) != IV_LENGTH_ENCRYPT) {
                    Log.e(LOG_TAG, "Reading " + mPath + " was incomplete");
                }
                if (fs.read(data) != data.length) {
                    Log.e(LOG_TAG, "Reading " + mPath + " was incomplete");
                }
                GCMParameterSpec  gcmSpec = new GCMParameterSpec(TAG_LENGTH_ENCRYPT * 8, iv);
                cipher.init(Cipher.DECRYPT_MODE, mEncryptionKey, gcmSpec);
                mData = cipher.doFinal(data);

            } catch (IOException ex) {
                Log.e(LOG_TAG, "Cannot load " + mPath.getPath() + ": " + ex.getMessage());

            } catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
                Log.e(LOG_TAG, "Cannot find cipher " + AES_ALGORITHM_GCM + ": " + ex.getMessage());

            } catch (InvalidKeyException ex) {
                Log.e(LOG_TAG, "Invalid encryption key for " + AES_ALGORITHM_GCM + ": " + ex.getMessage());

            } catch (InvalidAlgorithmParameterException ex) {
                Log.e(LOG_TAG, "Invalid encryption parameter for " + AES_ALGORITHM_GCM + ": " + ex.getMessage());

            } catch (IllegalBlockSizeException | BadPaddingException ex) {
                Log.e(LOG_TAG, "Decrypt error for " + AES_ALGORITHM_GCM + ": " + ex.getMessage());

            }
        } else {
            File legacyPath = new File(path.getPath() + ".dat");
            if (legacyPath.exists()) {
                Log.i(LOG_TAG, "Migrating legacy secure file " + legacyPath);
                try (FileInputStream fs = new FileInputStream(legacyPath)) {
                    int len = (int) mPath.length();
                    byte[] data = new byte[len];
                    if (fs.read(data) != len) {
                        Log.e(LOG_TAG, "Reading " + legacyPath + " was incomplete");
                    }
                    mData = data;
                } catch (IOException ex) {
                    Log.e(LOG_TAG, "Cannot load " + legacyPath + ": " + ex.getMessage());
                }
                if (save()) {
                    Utils.deleteFile(LOG_TAG, legacyPath);
                }
            }
        }
    }

    @Override
    public String getName() {

        return mPath.getName();
    }

    @Override
    public byte[] getData() {

        return mData;
    }

    @Override
    public void setData(byte[] raw) {

        mData = raw;
    }

    boolean save() {

        if (mData != null) {
            try (OutputStream outputStream = Files.newOutputStream(mPath.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                byte[] iv = new byte[IV_LENGTH_ENCRYPT];
                SecureRandom secureRandom = new SecureRandom();
                secureRandom.nextBytes(iv);
                outputStream.write(iv);

                Cipher cipher = Cipher.getInstance(AES_ALGORITHM_GCM);
                GCMParameterSpec  gcmSpec = new GCMParameterSpec(TAG_LENGTH_ENCRYPT * 8, iv);
                cipher.init(Cipher.ENCRYPT_MODE, mEncryptionKey, gcmSpec);
                byte[] data = cipher.doFinal(mData);
                outputStream.write(data);
                return true;

            } catch (IOException ex) {
                Log.e(LOG_TAG, "Cannot save " + mPath.getPath() + ": " + ex.getMessage());

            } catch (NoSuchAlgorithmException | NoSuchPaddingException ex) {
                Log.e(LOG_TAG, "Cannot find cipher " + AES_ALGORITHM_GCM + ": " + ex.getMessage());

            } catch (InvalidKeyException ex) {
                Log.e(LOG_TAG, "Invalid encryption key for " + AES_ALGORITHM_GCM + ": " + ex.getMessage());

            } catch (InvalidAlgorithmParameterException ex) {
                Log.e(LOG_TAG, "Invalid encryption parameter for " + AES_ALGORITHM_GCM + ": " + ex.getMessage());

            } catch (IllegalBlockSizeException | BadPaddingException ex) {
                Log.e(LOG_TAG, "Decrypt error for " + AES_ALGORITHM_GCM + ": " + ex.getMessage());

            }
        }
        return false;
    }
}
