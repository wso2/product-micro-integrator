/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.inbound.endpoint.protocol.hl7.core;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.impl.nio.reactor.DefaultListeningIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.IOReactorStatus;
import org.apache.http.nio.reactor.ListenerEndpoint;
import org.apache.http.nio.reactor.ListeningIOReactor;
import org.wso2.carbon.inbound.endpoint.protocol.hl7.util.HL7Configuration;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InboundHL7IOReactor {

    private static final Log log = LogFactory.getLog(InboundHL7IOReactor.class);

    private static ListeningIOReactor reactor;

    private static ConcurrentHashMap<Integer, ListenerEndpoint> endpointMap = new ConcurrentHashMap<Integer, ListenerEndpoint>();

    private static ConcurrentHashMap<Integer, HL7Processor> processorMap = new ConcurrentHashMap<Integer, HL7Processor>();

    private static MultiIOHandler multiIOHandler;

    private static volatile boolean isStarted = false;

    public static void start() throws IOException {

        if (reactor != null && reactor.getStatus().equals(IOReactorStatus.ACTIVE)) {
            return;
        }

        IOReactorConfig config = getDefaultReactorConfig();

        reactor = new DefaultListeningIOReactor(config);

        Thread reactorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    isStarted = true;
                    multiIOHandler = new MultiIOHandler(processorMap);
                    log.info("MLLP Transport IO Reactor Started");
                    reactor.execute(multiIOHandler);
                } catch (IOException e) {
                    isStarted = false;
                    log.error("Error while starting the MLLP Transport IO Reactor.", e);
                }
            }
        });

        reactorThread.start();
    }

    public static void stop() {
        try {
            reactor.shutdown();
            endpointMap.clear();
            isStarted = false;
        } catch (IOException e) {
            log.error("Error while shutting down MLLP Transport IO Reactor. ", e);
        }
    }

    public static void pause() {
        try {
            reactor.pause();
        } catch (IOException e) {
            log.error("Error while pausing MLLP Transport IO Reactor. ", e);
        }
    }

    public static boolean isStarted() {
        return isStarted;
    }

    public static boolean bind(int port, HL7Processor processor) {
        checkReactor();

        if (!isPortAvailable(port)) {
            log.error("A service is already listening on port " + port
                              + ". Please select a different port for this endpoint.");
            return false;
        }

        ListenerEndpoint ep = reactor.listen(getSocketAddress(port));

        try {
            ep.waitFor();
            endpointMap.put(port, ep);
            processorMap.put(port, processor);
            return true;
        } catch (InterruptedException e) {
            log.error("Error while starting a new MLLP Listener on port " + port + ". ", e);
            return false;
        }
    }

    private static boolean isPortAvailable(int port) {
        try {
            ServerSocket ss = new ServerSocket(port);
            ss.close();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean unbind(int port) {
        ListenerEndpoint ep = endpointMap.get(port);

        endpointMap.remove(port);
        processorMap.remove(port);
        multiIOHandler.disconnectSessions(port);

        if (ep == null) {
            return false;
        }

        ep.close();

        return true;
    }

    /**
     * Pauses the listener endpoint associated with the specified port.
     * This closes the endpoint, effectively stopping it from accepting new connections.
     *
     * @param port the port number of the endpoint to pause
     */
    public static void pauseEndpoint(int port) {
        log.info("Pausing HL7 Endpoint Listener for port " + port);
        ListenerEndpoint ep = endpointMap.get(port);
        if (ep != null) {
            ep.close();
        }
    }

    /**
     * In certain cases, reactor is not started prior to a bind() (e.g. if HL7EndpointManager triggers a bind() i.e.
     * via tenant loader and at that time no InboundListeners were initialized). If the reactor is not started, this
     * method will start it.
     */
    protected static void checkReactor() {
        if (reactor == null) {
            try {
                start();
            } catch (IOException e) {
                log.error("Reactor failed to start.");
            }
        }
    }

    public static boolean isEndpointRunning(int port) {
        return endpointMap.get(port) != null;
    }

    private static SocketAddress getSocketAddress(int port) {
        InetSocketAddress isa = new InetSocketAddress(port);
        return isa;
    }

    private static IOReactorConfig getDefaultReactorConfig() {
        IOReactorConfig.Builder builder = IOReactorConfig.custom();

        return builder.setSelectInterval(
                HL7Configuration.getInstance().getIntProperty(MLLPConstants.TCPConstants.SELECT_INTERVAL, 1000))
                .setShutdownGracePeriod(HL7Configuration.getInstance()
                                                .getIntProperty(MLLPConstants.TCPConstants.SHUTDOWN_GRACE_PERIOD, 500))
                .setIoThreadCount(HL7Configuration.getInstance()
                                          .getIntProperty(MLLPConstants.TCPConstants.IO_THREAD_COUNT,
                                                          Runtime.getRuntime().availableProcessors()))
                .setSoTimeout(HL7Configuration.getInstance().getIntProperty(MLLPConstants.TCPConstants.SO_TIMEOUT, 0))
                .setSoKeepAlive(HL7Configuration.getInstance()
                                        .getBooleanProperty(MLLPConstants.TCPConstants.SO_KEEP_ALIVE, true))
                .setTcpNoDelay(HL7Configuration.getInstance()
                                       .getBooleanProperty(MLLPConstants.TCPConstants.TCP_NO_DELAY, true))
                .setConnectTimeout(
                        HL7Configuration.getInstance().getIntProperty(MLLPConstants.TCPConstants.CONNECT_TIMEOUT, 0))
                .setRcvBufSize(HL7Configuration.getInstance().getIntProperty(MLLPConstants.TCPConstants.SO_RCVBUF, 0))
                .setSndBufSize(HL7Configuration.getInstance().getIntProperty(MLLPConstants.TCPConstants.SO_SNDBUF, 0))
                .setInterestOpQueued(false).setSoReuseAddress(true).setSoLinger(-1).build();
    }

}