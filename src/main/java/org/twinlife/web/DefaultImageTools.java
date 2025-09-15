/*
 *  Copyright (c) 2020-2022 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */

package org.twinlife.web;

import android.graphics.Bitmap;
import androidx.annotation.NonNull;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.twinlife.twinlife.ImageTools;
import org.twinlife.twinlife.util.Utils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Image tool operations used by the ImageService to perform some image specific operations.
 *
 * This implementation uses ImageIO as well as the Thumbnailator library which helps in
 * doing a good quality resize operation.
 */
public class DefaultImageTools implements ImageTools {
    static final Logger Log = LogManager.getLogger(DefaultImageTools.class);

    /**
     * Get the image data in the best format suitable for the image (either PNG or JPEG).
     *
     * @param image the image bitmap.
     * @return the image data bytes that we can either save in the database or send to the server.
     */
    @Override
    public byte[] getImageData(@NonNull Bitmap image) {

        return image.getBytes();
    }

    /**
     * Get the image data in the best format after resizing the image if it is bigger than the maxWidth x maxHeight
     * dimension.  It is desirable that this operation removes any Exif sensitive information such as GPS coordinates.
     *
     * @param sourcePath the source image path.
     * @param maxWidth the maximum width.
     * @param maxHeight the maximum height.
     * @return null if there is a problem in reading or resizing or the image data.
     */
    @Override
    public byte[] getFileImageData(@NonNull File sourcePath, int maxWidth, int maxHeight) {

        try {
            BufferedImage bufferedImage = ImageIO.read(sourcePath);
            if (bufferedImage.getWidth() < maxWidth && bufferedImage.getHeight() < maxHeight) {
                return Files.readAllBytes(sourcePath.toPath());
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            Thumbnails.of(sourcePath)
                    .size(maxWidth, maxHeight)
                    .crop(Positions.CENTER)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);
            return outputStream.toByteArray();

        } catch (Throwable exception) {

            Log.error("cannot get image data from {}: {}", sourcePath, exception);
            return null;
        }
    }

    /**
     * Copy and make a clean image before copying it to the server.  The target image must be resized if it
     * is bigger than the maxWidth x maxHeight dimensions.  It is desirable that this operation removes any
     * Exif sensitive information such as GPS coordinates.
     *
     * If the copy fails, we expect some exception to be raised.
     *
     * @param sourcePath the source image path.
     * @param destinationPath the target image path.
     * @param maxWidth the maximum width.
     * @param maxHeight the maximum height.
     * @param allowMove allow to move the source path to the destination if the image fits the max width and height.
     * @return true if the image was scaled and false otherwise.
     */
    @Override
    public boolean copyImage(@NonNull File sourcePath, @NonNull File destinationPath,
                             int maxWidth, int maxHeight, boolean allowMove) throws IOException {

        BufferedImage bufferedImage = ImageIO.read(sourcePath);
        if (bufferedImage.getWidth() < maxWidth && bufferedImage.getHeight() < maxHeight) {
            Utils.copyFile(sourcePath, destinationPath);
            return false;
        }

        File dirPath = destinationPath.getParentFile();
        if (dirPath != null && !dirPath.exists()) {
            dirPath.mkdirs();
        }
        try (FileOutputStream outputStream = new FileOutputStream(destinationPath)) {
            Thumbnails.of(sourcePath)
                    .size(maxWidth, maxHeight)
                    .crop(Positions.CENTER)
                    .outputFormat("jpg")
                    .toOutputStream(outputStream);

        } catch (Throwable ex) {
            Log.error("cannot copy image {}: {}", sourcePath, ex);
            throw ex;
        }
        return true;
    }
 }
