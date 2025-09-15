/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 */
package org.twinlife.web.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class TimestampUtil {
	public static final long SECONDS_TO_MILLIS = 1000;
	public static final long MINUTE_TO_MILLIS = 60 * SECONDS_TO_MILLIS;
	public static final long HOUR_TO_MILLIS = 60 * MINUTE_TO_MILLIS;
	public static final long DAY_TO_MILLIS = 24 * HOUR_TO_MILLIS;

	private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSx")
	        .withZone(ZoneId.of("UTC"));

	private TimestampUtil() {
	}

	/**
	 * Format a given timestamp value as a string representation.
	 *
	 * @param milliTimestamp
	 *            milliseconds from the epoch of 1970-01-01T00:00:00Z.
	 * @return String representation of the timestamp
	 */
	public static String formatTimestamp(long milliTimestamp) {
		return formatInstant(Instant.ofEpochMilli(milliTimestamp));
	}

	/**
	 * Format a given instant value as a string representation.
	 *
	 * @param instant
	 *            milliseconds from the epoch of 1970-01-01T00:00:00Z.
	 * @return String representation of the timestamp
	 */
	private static String formatInstant(Instant instant) {
		return TIMESTAMP_FORMATTER.format(instant);
	}

	/**
	 * Get an instant point from a date & time or timestamp value.
	 * <p>
	 * The expected argument is a string like:
	 * <ul>
	 * <li>"2017-06-07 04:27:16.595000+00"
	 * <li>"2017-06-07 04:27:16+00" (that is without the seconds fractional part)
	 * <li>"1501605654915" (an integer value representing the number of millis since
	 * epoch)
	 * </ul>
	 *
	 * @param timestampString
	 *            date & time or timestamp value
	 * @return Instant value, or <code>null</code> if the argument is not valid
	 */
	public static Instant instantFromTimestamp(String timestampString) {
		if (timestampString == null) {
			return null;
		}

		// Try first as a formatted date & time
		Instant instant = parseDateAndTime(timestampString);

		if (instant == null) {
			// Otherwise, parse it as a date
			instant = parseDate(timestampString);
		}

		if (instant == null) {
			// Otherwise, parse it as a long timestamp value
			instant = instantFromEpochMilli(timestampString);
		}
		return instant;
	}

	// Parse strings like 2017-06-07 04:27:16+00
	private static Instant parseDateAndTime(String arg) {
		final DateTimeFormatter dateAndTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]x");
		final ZonedDateTime datetime;

		try {
			datetime = ZonedDateTime.parse(arg, dateAndTimeFormatter);
		} catch (DateTimeParseException e) {
			return null;
		}
		return datetime.toInstant();
	}

	// Parse strings like 2017-06-07
	private static Instant parseDate(String arg) {
		final DateTimeFormatter dateAndTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
		final LocalDate date;

		try {
			date = LocalDate.parse(arg, dateAndTimeFormatter);
		} catch (DateTimeParseException e) {
			return null;
		}
		return date.atStartOfDay(ZoneOffset.UTC).toInstant();
	}

	/**
	 * Create an Instant from an Epoch value (milliseconds from 1970-01-01) as
	 * string.
	 *
	 * @param epochMilliAsString
	 * @return An Instant instance, or <code>null</code> if it cannot be created
	 *         (because format of epochMilliAsString parameter is invalid)
	 */
	private static Instant instantFromEpochMilli(String epochMilliAsString) {
		final long timestamp;
		try {
			timestamp = Long.parseLong(epochMilliAsString);
		} catch (NumberFormatException e) {
			return null;
		}
		return Instant.ofEpochMilli(timestamp);
	}
}
