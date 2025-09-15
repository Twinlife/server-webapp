/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 */

package org.twinlife.web.kafka.records;

/**
 * Interface implemented by all records that have a timestamp value
 */
public interface TimestampedRecord {

	/**
	 * Get this record timestamp
	 * @return timestamp value (milliseconds since epoch)
	 */
	public long getTimestamp();
}
