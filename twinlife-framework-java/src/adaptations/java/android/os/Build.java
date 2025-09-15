/*
 * Derived from Build.java
 *  https://android.googlesource.com/platform/frameworks/base.git (00d9027e38e8337b2358a06aa7e14840d6a1dfe9)
 *  base/core/java/android/os/Build.java
 */

/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.os;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;

public class Build {

    public static final String CPU_ABI = getCpuAbi();
    public static final String DEVICE = null;
    public static final String HARDWARE = null;
    public static final String ID = null;
    public static final String MANUFACTURER = null;
    public static final String MODEL = "unknown";
    public static final String PRODUCT = null;
    public static final String BOARD = null;
    public static final String DISPLAY = null;
    public static final String BRAND = "server";
    public static final String BOOTLOADER = "java";
    public static final boolean IS_DEBUGGABLE = false;
    public static final String TYPE = "user";

    public static class VERSION {

        public static final int SDK_INT = 30; // Make sure to be > Jelly bean on Linux
        public static final String RELEASE = null;
        public static final String CODENAME = null;
    }

    public static class VERSION_CODES {

        public static final int O = 26;
        public static final int O_MR1 = 27;
        public static final int P = 28;
    }

    public static String getRadioVersion() {

        return null;
    }

    public static String getOsName() {

        if (osName != null && osVersion != null) {
            return osName + " " + osVersion;
        } else if (osName != null) {
            return osName;
        } else {
            return "unknown";
        }
    }

    @NonNull
    private static String getCpuAbi() {

        String cpu_abi = System.getProperty("os.arch");
        if (cpu_abi == null) {
            cpu_abi = "unknown";
        }
        return cpu_abi;
    }

    private static String osName;
    private static String osVersion;

    static {
        File osRelease = new File("/etc/os-release");
        if (osRelease.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(osRelease))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int pos = line.indexOf('=');
                    if (pos < 0) {
                        continue;
                    }

                    String name = line.substring(0, pos);
                    String value = line.substring(pos + 1).replace('"', ' ').trim();
                    if ("NAME".equals(name)) {
                        osName = value;
                    } else if ("VERSION_ID".equals(name)) {
                        osVersion = value;
                    }
                }
            } catch (Exception exception) {

            }
        }
    }
}
