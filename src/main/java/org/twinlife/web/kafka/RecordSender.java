/*
 *  Copyright (c) 2024 twinlife SA.
 *  SPDX-License-Identifier: AGPL-3.0-only
 *
 *  Contributors:
 *   Laurent Latil (Laurent.Latil@twin.life)
 */

package org.twinlife.web.kafka;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.KafkaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;

/**
 * Asynchronous records sender.
 * <p>
 * The {@link RecordSender#queueEvent(String, Object)} method is used to push a new record on a queue.
 * These records are then published asynchronously (in a dedicated thread) through a kafka publisher, so the calling thread
 * is never blocked.
 * </p>
 * <p>
 * As the record publishing is asynchronous this sender can be configured with a 'close delay' to let the publisher thread and
 * the Kafka client handle the records possibly remaining in the queue.
 * </p>
 *
 * @param <K> Type of the record key
 * @param <R> Type of record
 */
public class RecordSender<K, R> implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(RecordSender.class);

    private final KafkaProducer<K, R> kafkaProducer;

    /** Name of Kafka topic to be supplied with published records */
    private final String topicName;

    /**
     * Queue of records waiting to be sent to kafka
     */
    private final ArrayBlockingQueue<QueuedRecord> recordsQueue;

    /**
     * Count of records added to the send queue (these records may not have been published yet
     * to kafka however)
     */
    private long eventsSentCount;

    /**
     * Count of skipped records. These records have been ignored by the sender (not published through Kafka) because the
     * records queue was full.
     */
    private long eventsSkippedCount;

    /**
     * Kafka publisher instance (singleton)
     */
    private final Publisher publisher;

    /**
     * Ref to the thread used to actually publish records.
     * This thread is (re)created if required.
     */
    private Thread publisherThread;

    /**
     * Set to <code>true</code> when this sender has been closed
     */
    private volatile boolean closed;

    /**
     * Max delay (in milliseconds) to wait during the close of this sender
     */
    private final int closeMaxDelay;

    /**
     * Constructs a new EventSender used to send kafka records without blocking
     * caller.
     *
     * @param kafkaProducer Kafka producer used to publish the records
     * @param topicName     Name of Kafka topic to use to publish records
     * @param queueLength   Records queue length
     * @param closeMaxDelay Max delay (in milliseconds) to wait during the close of
     *                      this sender. Use 0 to disable the closing delay.
     */
    public RecordSender(KafkaProducer<K, R> kafkaProducer, String topicName, int queueLength, int closeMaxDelay) {
        LOG.debug("new event sender instance (queue length: {}, closeDelay: {})", queueLength, closeMaxDelay);

        if (queueLength <= 0) {
            throw new IllegalArgumentException("Sender queue length must be stricly positive");
        }
        if (closeMaxDelay < 0) {
            throw new IllegalArgumentException("Close delay must be positive (use 0 for inifinite delay)");
        }

        this.kafkaProducer = kafkaProducer;
        this.topicName = topicName;
        this.recordsQueue = new ArrayBlockingQueue<>(queueLength);

        if (closeMaxDelay < 0) {
            throw new IllegalArgumentException("Event sender close delay value cannot be negative");
        }
        this.closeMaxDelay = closeMaxDelay;
        
        this.publisher = new Publisher();
    }

    /**
     * Send a new record on the specified topic.
     * <p>
     * The new record will be actually sent only if any space remains in this sender record queue.
     * When the queue capacity is reached (records cannot be sent to Kafka broker and are
     * accumulating in the queue) this new record is simply discarded.
     * </p>
     * 
     * @param record Record data
     */
    public synchronized void queueEvent(R record) {
        LOG.debug("Add record to topic {}\n{}", topicName, record);

        if (closed) {
            LOG.error("Cannot queue new events when sender has been closed");
            return;
        }

        final QueuedRecord qr = new QueuedRecord(topicName, record);
        final boolean eventAdded = recordsQueue.offer(qr);

        if (eventAdded) {
            eventsSentCount++;
        } else {
            eventsSkippedCount++;
            LOG.warn("Kafka record sender queue full: event skipped");
        }

        // (re)create the publishing thread if needed
        createPublisherThreadIfNeeded();
    }

    /**
     * Terminates the events sending processing.
     */
    public synchronized void close() {
        LOG.debug("close");
        doClose();
    }

    /**
     * Perform the actual closing of the event sender
     *
     * @param shutdownInProgress must be set to <code>true</code> when this method
     *                           is invoked from a shutdown hook
     */
    private void doClose() {
        if (closed || publisherThread == null) {
            // Calling close() multiple times must be idempotent
            // If no event have been sent publisher thread is not created as well, so
            // nothing to do.
            return;
        }

        // First set 'closed' to tag that this sender is closing
        closed = true;

        if (recordsQueue.isEmpty()) {
            // If the queue is empty we 'interrupt' the publisher thread (as it is probably blocked
            // on a BlockingQueue.take() )
            publisherThread.interrupt();
        } else if (closeMaxDelay > 0) {
            // Otherwise we let the publisher thread running a little while sending
            // the remaining records
            try {
                publisherThread.join(closeMaxDelay);
            } catch (InterruptedException ignored) {
            }
        }

        int queueSize = recordsQueue.size();
        if (queueSize > 0) {
            LOG.warn("{} events remain in the event queue (not sent)", queueSize);
        }

        // Close the kafka producer now (with a close delay in order to let it publish
        // last records)
        kafkaProducer.close(Duration.ofMillis(closeMaxDelay));
        LOG.debug("kafka producer closed");

        // From now on, kafkaProducer.send() call will return an error (as the producer
        // state is closed), so either the publisher thread has terminated or it will
        // terminate soon.
        try {
            // Wait for the publisher thread termination. Use a short timeout in order
            // not to block if this thread never terminates (for any reason)
            publisherThread.join(100);
        } catch (InterruptedException ignored) {
        }

        // Note: when the event sender is closed by the shutdown hook, the SLF4J logger is already closed (as it uses
        // its own shutdown hook also), so the following info/warn messages are generally not available in logs.
        if (!publisherThread.isAlive()) {
            // Number of records sent through kafka
            final long handledRecordsCount = publisher.getRecordsFailedCount() + publisher.getRecordsPublishedCount();

            LOG.info("{} events put in send queue, {} events skipped & {} records published successfully, {} records failed \n",
                    eventsSentCount, eventsSkippedCount, publisher.getRecordsPublishedCount(), publisher.getRecordsFailedCount());

            if (handledRecordsCount != eventsSentCount) {
                LOG.warn("{} events were not published through kafka", eventsSentCount - handledRecordsCount);
            }
            if (eventsSkippedCount != 0 || publisher.getRecordsFailedCount() != 0) {
                LOG.warn("Some events have been lost ({} events ignored while kafka was unavailable, {} events failed in kafka)",
                        eventsSkippedCount, publisher.getRecordsFailedCount());
            }
        } else {
            LOG.error("Event publisher thread still running (it should have been stopped as event sender is closed)");
        }
    }
    
    /**
     * Create & start a publisher thread.
     * <p>
     * The thread is created/started only in following cases:
     * <ul>
     * <li>No existing thread yet.
     * <li>Existing thread instance not alive (was terminated for whatever reasons).
     * </ul>
     * 
     * It is also defined as daemon in order not to prevent Java VM termination if still running.
     * <p>
     */
    private void createPublisherThreadIfNeeded() {
        if (publisherThread == null || !publisherThread.isAlive()) {
            if (publisherThread == null) {
                LOG.debug("Create and start the initial record sender publishing thread");
            } else {
                LOG.warn("Kafka record sender thread needs not alive anylonger, and need to be restarted");
            }

            publisherThread = new Thread(publisher, "Kafka publisher");
            publisherThread.setDaemon(true);
            publisherThread.start();
        }
    }

    /**
     * Store data on each queued record
     */
    private class QueuedRecord {
        private final String topic;
        private final R record;

        /**
         * Constructs a new queued record.
         *
         * @param topic  Name of record topic
         * @param record Record content
         */
        public QueuedRecord(String topic, R record) {
            this.topic = topic;
            this.record = record;
        }
    }

    /**
     * Record publisher.
     */
    private class Publisher implements Runnable, Callback {
        /**
         * Count of successfully published records
         */
        private long recordsPublishedCount;

        /**
         * Count of failed publications
         */
        private long recordsFailedCount;

        private boolean running = true;

        /**
         * This code, running in a dedicated thread, fetches the new queued records and
         * publish them through kafka producer
         */
        @Override
        public void run() {
            while (running && (!closed || recordsQueue.size() > 0)) {
                try {
                    // Get the next record to publish (blocking if no record available)
                    final QueuedRecord queuedRecord = recordsQueue.take();
                    sendQueuedRecord(queuedRecord);
                } catch (InterruptedException e) {
                    // Our thread is interrupted when event sender is closed
                    if (!closed) {
                        LOG.error("Unexpected interruption of take()", e);
                    }
                }
            }
        }

        /**
         * Send a queued record to the kafka topic
         *
         * @param record Queued record to send through kafka bus
         */
        private void sendQueuedRecord(QueuedRecord record) {
            final ProducerRecord<K, R> producerRecord = new ProducerRecord<K, R>(record.topic, record.record);

            try {
                kafkaProducer.send(producerRecord, this);
            } catch (KafkaException e) {
                LOG.error("Error sending kafka record", e);
            } catch (IllegalStateException e) {
                System.out.println("IllegalStateException ! closed=" + closed);
                // This may happen when a record is sent while producer has been closed.
                // Skip this exception if (and only if) this event sender is terminating
                if (closed) {
                    // Stop the publishing thread now
                    running = false;
                    LOG.info("Record sent while kafka producer is closed. This is OK as this sender is closing as well.");
                } else {
                    throw e;
                }
            }
        }

        /**
         * Record publishing completion handler
         */
        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            if (exception == null) {
                // If not exception reported, we can assume that record was successfully managed
                // by kafka
                recordsPublishedCount++;
            } else {
                LOG.warn("record publishing failed", exception);
                recordsFailedCount++;
            }
        }

        /**
         * Get the number of successful record deliveries
         *
         * @return number of success
         */
        public long getRecordsPublishedCount() {
            return recordsPublishedCount;
        }

        /**
         * Get the number of failed record deliveries
         *
         * @return number of failures
         */
        public long getRecordsFailedCount() {
            return recordsFailedCount;
        }
    }
}
