/**
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 * <p>
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.inbound.endpoint.protocol.rabbitmq;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.Recoverable;
import com.rabbitmq.client.ShutdownSignalException;
import org.apache.axis2.transport.rabbitmq.RabbitMQUtils;
import org.apache.axis2.util.GracefulShutdownTimer;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.inbound.endpoint.protocol.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.wso2.carbon.inbound.endpoint.common.Constants.DEFAULT_GRACEFUL_SHUTDOWN_POLL_INTERVAL_MS;

/**
 * The actual tasks that perform message consuming
 */
public class RabbitMQConsumer implements Consumer {

    private static final Log log = LogFactory.getLog(RabbitMQConsumer.class);
    private RabbitMQConnectionFactory rabbitMQConnectionFactory;
    private volatile Map<String, String> rabbitMQProperties = new HashMap<>();
    private RabbitMQInjectHandler injectHandler;
    private Connection connection = null;
    private Channel channel = null;
    private String queueName;
    private long maxDeadLetteredCount;
    private long requeueDelay;
    private boolean autoAck;
    private String inboundName;
    private String consumerTag;
    private long unDeploymentWaitTimeout;

    private volatile boolean isShuttingDown;
    private final AtomicInteger inFlightMessages = new AtomicInteger(0);
    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final ReentrantReadWriteLock.ReadLock readLock = rwLock.readLock();
    private final ReentrantReadWriteLock.WriteLock writeLock = rwLock.writeLock();

    public RabbitMQConsumer(RabbitMQConnectionFactory rabbitMQConnectionFactory, Properties properties,
                            RabbitMQInjectHandler injectHandler) {
        this.rabbitMQConnectionFactory = rabbitMQConnectionFactory;
        this.injectHandler = injectHandler;
        properties.forEach((key, value) -> rabbitMQProperties.put(key.toString(), value.toString()));
        this.unDeploymentWaitTimeout = NumberUtils.toLong(rabbitMQProperties.get(
                RabbitMQConstants.UNDEPLOYMENT_GRACE_TIMEOUT), 0);
    }

    /**
     * Execute by the {@link RabbitMQTask}
     */
    public void execute() {
        try {
            // Reset shutdown state before initialization
            // This is to handle the consumer initialization after a deactivation where graceful shutdown is enabled.
            writeLock.lock();
            try {
                isShuttingDown = false;
            } finally {
                writeLock.unlock();
            }

            initConsumer();
        } catch (IOException | RabbitMQException e) {
            log.error("Error occurred while initializing the consumer.", e);
        }
    }

    /**
     * Register a consumer to the queue
     *
     * @throws IOException
     */
    private void initConsumer() throws IOException, RabbitMQException {
        if (connection == null) {
            connection = rabbitMQConnectionFactory.createConnection();
        }

        channel = connection.createChannel();
        ((Recoverable) this.channel).addRecoveryListener(new RabbitMQRecoveryListener());

        // set the qos value
        int qos = NumberUtils.toInt(rabbitMQProperties.get(RabbitMQConstants.CONSUMER_QOS),
                RabbitMQConstants.DEFAULT_CONSUMER_QOS);
        channel.basicQos(qos);

        // declaring queue, exchange and binding
        queueName = rabbitMQProperties.get(RabbitMQConstants.QUEUE_NAME);
        String exchangeName = rabbitMQProperties.get(RabbitMQConstants.EXCHANGE_NAME);

        try {
            RabbitMQUtils.declareQueue(channel, queueName, rabbitMQProperties);
        } catch (IOException ex) {
            channel = RabbitMQUtils.checkAndIgnoreInEquivalentParamException(connection, ex,
                    org.apache.axis2.transport.rabbitmq.RabbitMQConstants.QUEUE, queueName);
        }
        try {
            RabbitMQUtils.declareExchange(channel, exchangeName, rabbitMQProperties);
        } catch (IOException ex) {
            channel = RabbitMQUtils.checkAndIgnoreInEquivalentParamException(connection, ex,
                    org.apache.axis2.transport.rabbitmq.RabbitMQConstants.EXCHANGE, exchangeName);
        }
        RabbitMQUtils.bindQueueToExchange(channel, queueName, exchangeName, rabbitMQProperties);

        // get max dead-lettered count
        maxDeadLetteredCount =
                NumberUtils.toLong(rabbitMQProperties.get(RabbitMQConstants.MESSAGE_MAX_DEAD_LETTERED_COUNT));

        // get requeue delay
        requeueDelay =
                NumberUtils.toLong(rabbitMQProperties.get(RabbitMQConstants.MESSAGE_REQUEUE_DELAY));

        // get consumer tag if given
        String consumerTag = rabbitMQProperties.get(RabbitMQConstants.CONSUMER_TAG);

        autoAck = BooleanUtils.toBooleanDefaultIfNull(BooleanUtils.toBooleanObject(rabbitMQProperties
                .get(RabbitMQConstants.QUEUE_AUTO_ACK)), true);

        if (StringUtils.isNotEmpty(consumerTag)) {
            this.consumerTag = channel.basicConsume(queueName, autoAck, consumerTag, this);
        } else {
            this.consumerTag = channel.basicConsume(queueName, autoAck, this);
        }
    }

    /**
     * Called when the consumer is registered by a call to any of the {@link Channel#basicConsume} methods.
     *
     * @param consumerTag the consumer tag associated with the consumer
     */
    @Override
    public void handleConsumeOk(String consumerTag) {
        log.info("Start consuming queue: " + queueName + " with consumer tag: " + consumerTag +
                " for inbound endpoint: " + inboundName);
    }

    /**
     * Called when the consumer is cancelled by a call to {@link Channel#basicCancel}.
     *
     * @param consumerTag the consumer tag associated with the consumer
     */
    @Override
    public void handleCancelOk(String consumerTag) {
        log.info("The consumer with consumer tag: " + consumerTag + " stops listening to new messages.");
    }

    /**
     * Called when the consumer is cancelled for reasons other than by a call to {@link Channel#basicCancel}.
     * For example, the queue has been deleted.
     * See {@link #handleCancelOk} for notification of consumer cancellation due to {@link Channel#basicCancel}.
     *
     * @param consumerTag the consumer tag associated with the consumer
     * @throws IOException
     */
    @Override
    public void handleCancel(String consumerTag) throws IOException {
        log.info("The consumer with consumer tag: " + consumerTag + " unexpectedly stops listening to new messages.");
    }

    /**
     * Called when either the channel or the underlying connection has been shut down.
     *
     * @param consumerTag the consumer tag associated with the consumer
     * @param signal      a {@link ShutdownSignalException} indicating the reason for the shut down
     */
    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException signal) {
        if (signal.isInitiatedByApplication()) {
            log.info("The connection to the messaging server was shut down. Consumer tag: " + consumerTag);

        } else if (signal.getReference() instanceof Channel) {
            int channelNumber = ((Channel) signal.getReference()).getChannelNumber();
            log.info("The consumer on channel number: " + channelNumber + " with consumer tag: " + consumerTag
                    + " was shut down.");

        } else {
            log.info("The consumer with consumer tag: " + consumerTag + " was shut down.");
        }
    }

    /**
     * Called when a basic.recover-ok is received in reply to a basic.recover. All messages received before this is
     * invoked that haven't been ack'ed will be re-delivered. All messages received afterwards won't be.
     *
     * @param consumerTag the consumer tag associated with the consumer
     */
    @Override
    public void handleRecoverOk(String consumerTag) {
        //do nothing since this mean recovery-ok is received.
    }

    /**
     * Called when a basic.deliver is received for this consumer.
     *
     * @param consumerTag the consumer tag associated with the consumer
     * @param envelope    packaging data for the message
     * @param properties  content header data for the message
     * @param body        the message body (opaque, client-specific byte array)
     * @throws IOException if the consumer encounters an I/O error while processing the message
     * @see Envelope
     */
    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body)
            throws IOException {
        readLock.lock();
        try {
            if (isShuttingDown) {
                /*
                 * The server is shutting down. We attempt to reject the message with requeue=true
                 * so that it goes back to the queue for redelivery.
                 *
                 * However, if the channel is already closed or closing due to shutdown,
                 * basicReject may throw an exception (e.g., NullPointerException or AlreadyClosedException).
                 *
                 * This is safe to ignore because:
                 * - The message is still unacked.
                 * - RabbitMQ will automatically requeue it once the consumer connection is closed.
                 */
                try {
                    channel.basicReject(envelope.getDeliveryTag(), true);
                    log.info("The rejected message with message id: " + properties.getMessageId() + " and " +
                            "delivery tag: " + envelope.getDeliveryTag() + " on the queue: " +
                            queueName + " since the consumer is shutting down.");
                } catch (Exception e) {
                    log.debug("Failed to reject message during shutdown (likely due to closed channel).", e);
                }
                return;
            }
            inFlightMessages.incrementAndGet();
        } finally {
            readLock.unlock();
        }

        try {
            AcknowledgementMode acknowledgementMode = injectHandler.onMessage(properties, body, inboundName);
            switch (acknowledgementMode) {
                case REQUEUE_TRUE:
                    try {
                        Thread.sleep(requeueDelay);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    channel.basicReject(envelope.getDeliveryTag(), true);
                    break;
                case REQUEUE_FALSE:
                    List<HashMap<String, Object>> xDeathHeader =
                            (ArrayList<HashMap<String, Object>>) properties.getHeaders().get("x-death");
                    // check if message has been already dead-lettered
                    if (xDeathHeader != null && xDeathHeader.size() > 0 && maxDeadLetteredCount != -1) {
                        Long count = (Long) xDeathHeader.get(0).get("count");
                        if (count <= maxDeadLetteredCount) {
                            channel.basicReject(envelope.getDeliveryTag(), false);
                            log.info("The rejected message with message id: " + properties.getMessageId() + " and " +
                                    "delivery tag: " + envelope.getDeliveryTag() + " on the queue: " +
                                    queueName + " is dead-lettered " + count + " time(s).");
                        } else {
                            // handle the message after exceeding the max dead-lettered count
                            proceedAfterMaxDeadLetteredCount(envelope, properties, body);
                        }
                    } else {
                        // the message might be dead-lettered or discard if an error occurred in the mediation flow
                        channel.basicReject(envelope.getDeliveryTag(), false);
                        log.info("The rejected message with message id: " + properties.getMessageId() + " and " +
                                "delivery tag: " + envelope.getDeliveryTag() + " on the queue: " +
                                queueName + " will discard or dead-lettered.");
                    }
                    break;
                default:
                    if (!autoAck) {
                        channel.basicAck(envelope.getDeliveryTag(), false);
                    }
                    break;
            }
        } finally {
            inFlightMessages.decrementAndGet();
        }
    }

    /**
     * The message will publish to the exchange with routing key or discard
     *
     * @param envelope   packaging data for the message
     * @param properties content header data for the message
     * @param body       the message body
     * @throws IOException
     */
    private void proceedAfterMaxDeadLetteredCount(Envelope envelope, AMQP.BasicProperties properties, byte[] body)
            throws IOException {
        String routingKey =
                rabbitMQProperties.get(RabbitMQConstants.MESSAGE_ERROR_QUEUE_ROUTING_KEY);
        String exchangeName =
                rabbitMQProperties.get(RabbitMQConstants.MESSAGE_ERROR_EXCHANGE_NAME);
        if (StringUtils.isNotEmpty(routingKey) && StringUtils.isNotEmpty(exchangeName)) {
            // publish message to the given exchange with the routing key
            channel.basicPublish(exchangeName, routingKey, properties, body);
            channel.basicAck(envelope.getDeliveryTag(), false);
            log.info("The max dead lettered count exceeded. Hence message with message id: " +
                    properties.getMessageId() + " and delivery tag: " + envelope.getDeliveryTag() +
                    " publish to the exchange: " + exchangeName + " with the routing key: " + routingKey + ".");
        } else if (StringUtils.isNotEmpty(routingKey) && StringUtils.isEmpty(exchangeName)) {
            // publish message to the default exchange with the routing key
            channel.basicPublish("", routingKey, properties, body);
            channel.basicAck(envelope.getDeliveryTag(), false);
            log.info("The max dead lettered count exceeded. Hence message with message id: " +
                    properties.getMessageId() + " and delivery tag: " + envelope.getDeliveryTag() + " publish to the " +
                    "default exchange with the routing key: " + routingKey + ".");
        } else {
            // discard the message
            channel.basicAck(envelope.getDeliveryTag(), false);
            log.info("The max dead lettered count exceeded. " +
                    "No 'rabbitmq.message.error.queue.routing.key' specified for publishing the message. " +
                    "Hence the message with message id: " + properties.getMessageId() + " and delivery tag: " +
                    envelope.getDeliveryTag() + " on the queue: " + queueName + " will discard.");
        }
    }

    /**
     * Return connection back to the pool when undeploying the listener proxy
     */
    public void close() {
        writeLock.lock();
        try {
            if (!isShuttingDown) {
                isShuttingDown = true;
            }
        } finally {
            writeLock.unlock();
        }

        GracefulShutdownTimer gracefulShutdownTimer = GracefulShutdownTimer.getInstance();
        if (gracefulShutdownTimer.isStarted()) {
            // Wait for in-flight messages to be processed before shutting down the file polling consumer
            Utils.waitForGracefulTaskCompletion(gracefulShutdownTimer, inFlightMessages, inboundName,
                    DEFAULT_GRACEFUL_SHUTDOWN_POLL_INTERVAL_MS);
        } else {
            log.info("Awaiting completion of in-flight messages for RabbitMQ Inbound Endpoint: "
                    + inboundName + " before shutdown.");
            long waitUntil = System.currentTimeMillis() + unDeploymentWaitTimeout;
            while (inFlightMessages.get() > 0 && System.currentTimeMillis() < waitUntil) {
                try {
                    Thread.sleep(DEFAULT_GRACEFUL_SHUTDOWN_POLL_INTERVAL_MS); // wait until all in-flight messages are done
                } catch (InterruptedException e) {}
            }
        }

        if (connection != null) {
            try {
                connection.abort();
            } catch (Exception e) {
                log.error("Error occurred while closing connection: " + e.getMessage());
            }
            connection = null;
        }
        channel = null;

        if (inFlightMessages.get() > 0) {
            log.warn("Rabbitmq Inbound Endpoint: " + inboundName + " stopped with "
                    + inFlightMessages.get() + " in-flight messages still being processed");
        } else {
            log.info("Successfully stopped the Rabbitmq Inbound Endpoint: " + inboundName);
        }
    }

    public void stopDeliver() {
        isShuttingDown = true;

        if (channel != null && channel.isOpen() && consumerTag != null) {
            try {
                channel.basicCancel(consumerTag);
                log.info("Successfully cancelled consumer: " + consumerTag);
            } catch (IOException e) {
                log.warn("Failed to cancel consumer cleanly, proceeding to shutdown.", e);
            }
        }
    }

    public String getInboundName() {
        return inboundName;
    }

    public void setInboundName(String inboundName) {
        this.inboundName = inboundName;
    }

    protected void requestShutdown() {
        close();
    }
}
