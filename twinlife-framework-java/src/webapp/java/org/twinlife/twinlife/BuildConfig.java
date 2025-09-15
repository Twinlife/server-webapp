/*
 *  Copyright (c) 2019-2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Christian Jacquemot (Christian.Jacquemot@twinlife-systems.com)
 *   Stephane Carrez (Stephane.Carrez@twin.life)
 */
package org.twinlife.twinlife;

public final class BuildConfig {
  public static final boolean DEBUG = true;
  public static final boolean ENABLE_EVENT_MONITOR = true;
  public static final boolean ENABLE_POWER_MANAGEMENT_REPORT = false;
  public static final boolean ENABLE_INFO_LOG = true;
  public static final boolean ENABLE_CHECKS = false;
  public static final boolean ENABLE_DATABASE = false;
  public static final boolean ENABLE_DUMP = false;
  public static final long SECRET_RENEW_DELAY = 24L * 86400L * 1000L;
  public static final String VERSION_NAME = "2.2.1";
  public static final String API_KEY = "no-key";
}
