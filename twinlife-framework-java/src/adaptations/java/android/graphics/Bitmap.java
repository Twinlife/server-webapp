/*
 * Derived from Bitmap.java
 *  https://android.googlesource.com/platform/frameworks/base.git (00d9027e38e8337b2358a06aa7e14840d6a1dfe9)
 *  graphics/java/android/graphics/Bitmap.java
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

import java.io.IOException;
import java.io.OutputStream;

public class Bitmap {

    byte[] mBytes;
    CompressFormat mFormat;

    public Bitmap(byte[] bytes) {

        mBytes = bytes;
        mFormat = null;
    }

    public Bitmap(byte[] bytes, CompressFormat format) {

        mBytes = bytes;
        mFormat = format;
    }

    public enum CompressFormat {
        PNG,
        JPEG
    }

    public byte[] getBytes() {

        return mBytes;
    }

    public int getWidth() {
        return 0;
    }

    public int getHeight() {
        return 0;
    }

    public void compress(CompressFormat format, int quality, OutputStream stream) {
        try {
            stream.write(mBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Bitmap createScaledBitmap(Bitmap src, int dstWidth, int dstHeight, boolean filter) {

        return src;
    }
}
