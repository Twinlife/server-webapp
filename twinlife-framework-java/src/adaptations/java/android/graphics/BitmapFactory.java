/*
 * Derived from BitmapFactory.java
 *  https://android.googlesource.com/platform/frameworks/base.git (00d9027e38e8337b2358a06aa7e14840d6a1dfe9)
 *  graphics/java/android/graphics/BitmapFactory.java
 */

/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.graphics;

import android.util.Log;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public class BitmapFactory {

    public static class Options {
        public boolean inJustDecodeBounds;
        public int outWidth;
        public int outHeight;
        public int inSampleSize;
    }

    public static Bitmap decodeByteArray(byte[] bytes, int offset, int length) {

        return new Bitmap(bytes, null);
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) {

        if (opts.inJustDecodeBounds) {
            return null;
        }
        return new Bitmap(data, null);
    }

    public static Bitmap decodeFile(String pathName, Options opts) {

        try {
            int pos = pathName.lastIndexOf('.');
            String extension;
            if (pos > 0) {
                extension = pathName.substring(pos + 1);
            } else {
                extension = "jpg";
            }
            BufferedImage bufferedImage = ImageIO.read(new File(pathName));
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, extension, outputStream);

            if (opts != null) {
                opts.outWidth = bufferedImage.getWidth();
                opts.outHeight = bufferedImage.getHeight();
            }
            if ("png".equals(extension)) {
                return new Bitmap(outputStream.toByteArray(), Bitmap.CompressFormat.PNG);
            } else {
                return new Bitmap(outputStream.toByteArray(), Bitmap.CompressFormat.JPEG);
            }
        } catch (IOException exception) {

            Log.e("Bitmap", "Exception: " + exception);
            exception.printStackTrace();
            return null;
        }
    }
}
