/*
 * Derived from Log.java
 *  https://android.googlesource.com/platform/frameworks/base.git (00d9027e38e8337b2358a06aa7e14840d6a1dfe9)
 *  core/java/android/util/Log.java
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

package android.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

public class Log {

    public static final boolean DEBUG = false;
    public static final boolean ERROR = true;
    public static final boolean INFO = true;
    public static final boolean VERBOSE = false;

    private static final Logger logger = LogManager.getLogger(Log.class);

    public static void d(String tag, String msg) {
        logger.debug(tag + ": " + msg);
    }

    public static void e(String tag, String msg) {
        logger.error(tag + ": " + msg);
    }

    public static void i(String tag, String msg) {
        logger.info(tag + ": " + msg);
    }

    public static void w(String tag, String msg) {
        logger.warn(tag + ": " + msg);
    }

    public static void w(String tag, Exception ex) {
        logger.warn(tag, ex);
    }

    public static void v(String tag, String msg) {
        logger.info(tag + ": " + msg);
    }

    public static void d(String tag, String msg, Exception ex) {
        logger.debug(tag + ": " + msg, ex);
    }

    public static void e(String tag, String msg, Exception ex) {
        logger.error(tag + ": " + msg, ex);
    }

    public static void w(String tag, String msg, Exception ex) {
        logger.warn(tag + ": " + msg, ex);
    }

    public static boolean isLoggable(String tag, boolean debug) {

        return debug;
    }
}
