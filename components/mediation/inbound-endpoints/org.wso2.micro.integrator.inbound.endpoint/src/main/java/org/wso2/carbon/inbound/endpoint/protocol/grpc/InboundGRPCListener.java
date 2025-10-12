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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.wso2.carbon.inbound.endpoint.protocol.grpc;

import com.google.protobuf.Empty;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.apache.axis2.util.GracefulShutdownTimer;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.SynapseException;
import org.apache.synapse.core.SynapseEnvironment;
import org.apache.synapse.inbound.InboundProcessorParams;
import org.apache.synapse.inbound.InboundRequestProcessor;
import org.wso2.carbon.inbound.endpoint.protocol.Utils;
import org.wso2.carbon.inbound.endpoint.protocol.grpc.util.EventServiceGrpc;
import org.wso2.carbon.inbound.endpoint.protocol.grpc.util.Event;

import java.io.IOException;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.wso2.carbon.inbound.endpoint.common.Constants.DEFAULT_GRACEFUL_SHUTDOWN_POLL_INTERVAL_MS;
import static org.wso2.carbon.inbound.endpoint.protocol.grpc.InboundGRPCConstants.UNDEPLOYMENT_GRACE_TIMEOUT;

public class InboundGRPCListener implements InboundRequestProcessor {
    private int port;
    private String name;
    private GRPCInjectHandler injectHandler;
    private static final Log log = LogFactory.getLog(InboundGRPCListener.class.getName());
    private Server server;
    private boolean startInPausedMode;
    private final AtomicInteger inFlightMessages = new AtomicInteger(0);
    private PausingInterceptor interceptor;
    private long unDeploymentWaitTimeout = 0;

    public InboundGRPCListener(InboundProcessorParams params) {
        String injectingSeq = params.getInjectingSeq();
        String onErrorSeq = params.getOnErrorSeq();
        SynapseEnvironment synapseEnvironment = params.getSynapseEnvironment();
        String portParam = params.getProperties().getProperty(InboundGRPCConstants.INBOUND_ENDPOINT_PARAMETER_GRPC_PORT);
        try {
            port = Integer.parseInt(portParam);
        } catch (NumberFormatException e) {
            log.warn("Exception occurred when getting " + InboundGRPCConstants.INBOUND_ENDPOINT_PARAMETER_GRPC_PORT +
                    " property. Setting the port as " + InboundGRPCConstants.DEFAULT_INBOUND_ENDPOINT_GRPC_PORT);
            port = InboundGRPCConstants.DEFAULT_INBOUND_ENDPOINT_GRPC_PORT;
        }
        name = params.getName();
        injectHandler = new GRPCInjectHandler(injectingSeq, onErrorSeq, false, synapseEnvironment);
        startInPausedMode = params.startInPausedMode();
        Properties grpcProperties = params.getProperties();
        if (grpcProperties != null) {
            unDeploymentWaitTimeout = NumberUtils.toLong(grpcProperties.getProperty(UNDEPLOYMENT_GRACE_TIMEOUT), 0);
        }
    }

    public void init() {
        try {
            /*
             * The activate/deactivate functionality for the GRPC protocol is not currently implemented
             * for Inbound Endpoints.
             *
             * Therefore, the following check has been added to immediately return if the "suspend"
             * attribute is set to true in the inbound endpoint configuration.
             *
             * Note: This implementation is temporary and should be revisited and improved once
             * the activate/deactivate capability for GRPC listener is implemented.
             */
            if (startInPausedMode) {
                log.info("Inbound endpoint [" + name + "] is currently suspended.");
                return;
            }
            this.start();
        } catch (IOException e) {
            throw new SynapseException("IOException when starting gRPC server: " + e.getMessage(), e);
        }
    }

    public void destroy() {
        try {
            GracefulShutdownTimer gracefulShutdownTimer = GracefulShutdownTimer.getInstance();
            if (gracefulShutdownTimer.isStarted()) {
                log.info("Waiting for " + inFlightMessages.get() + " in-flight messages to be processed before " +
                        "shutting down gRPC listener for inbound endpoint: " + name);
                Utils.waitForGracefulTaskCompletion(gracefulShutdownTimer, inFlightMessages, name,
                        DEFAULT_GRACEFUL_SHUTDOWN_POLL_INTERVAL_MS);
            } else {
                long waitUntil = System.currentTimeMillis() + unDeploymentWaitTimeout;
                while (inFlightMessages.get() > 0 && System.currentTimeMillis() < waitUntil) {
                    try {
                        Thread.sleep(DEFAULT_GRACEFUL_SHUTDOWN_POLL_INTERVAL_MS); // wait until all in-flight messages are done
                    } catch (InterruptedException e) {}
                }
            }
            this.stopServer();
            if (inFlightMessages.get() > 0) {
                log.warn("gRPC Inbound Endpoint: " + name + " stopped with "
                        + inFlightMessages.get() + " in-flight messages still being processed");
            } else {
                log.info("Successfully stopped the gRPC Inbound Endpoint: " + name);
            }
        } catch (InterruptedException e) {
            throw new SynapseException("Failed to stop gRPC server: " +e.getMessage());
        }
    }

    @Override
    public void pause() {
        interceptor.pause();
    }

    @Override
    public boolean activate() {

        return false;
    }

    @Override
    public boolean deactivate() {

        return false;
    }

    @Override
    public boolean isDeactivated() {
        if (Objects.isNull(server)) {
            return true;
        }
        return server.isTerminated();
    }

    public void start() throws IOException {
        if (server != null) {
            throw new IllegalStateException("gRPC Listener Server already started");
        }
        interceptor = new PausingInterceptor();
        server = ServerBuilder.forPort(port).addService(new EventServiceGrpc.EventServiceImplBase() {
            @Override
            public void process(Event request, StreamObserver<Event> responseObserver) {
                inFlightMessages.incrementAndGet();
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Event received for gRPC Listener process method");
                    }
                    injectHandler.invokeProcess(request, responseObserver);
                } finally {
                    inFlightMessages.decrementAndGet();
                }

            }

            @Override
            public void consume(Event request, StreamObserver<Empty> responseObserver) {
                inFlightMessages.incrementAndGet();
                try {
                    if (log.isDebugEnabled()) {
                        log.debug("Event received for gRPC Listener consume method");
                    }
                    injectHandler.invokeConsume(request, responseObserver);
                    responseObserver.onNext(Empty.getDefaultInstance());
                    responseObserver.onCompleted();
                } finally {
                    inFlightMessages.decrementAndGet();
                }

            }
        }).intercept(interceptor).build();
        server.start();
        log.info("gRPC Listener Server started on port: " + port);
    }

    public void stopServer() throws InterruptedException {
        Server s = server;
        if (s == null) {
            throw new IllegalStateException("gRPC Listener Server is already stopped");
        }
        server = null;
        s.shutdown();
        if (s.awaitTermination(1, TimeUnit.SECONDS)) {
            log.debug("gRPC Listener Server stopped");
            return;
        }
        s.shutdownNow();
        if (s.awaitTermination(1, TimeUnit.SECONDS)) {
            return;
        }
        throw new RuntimeException("Unable to shutdown gRPC Listener Server");
    }

    public static class PausingInterceptor implements ServerInterceptor {
        private final AtomicBoolean paused = new AtomicBoolean(false);

        public void pause() { paused.set(true); }
        public void resume() { paused.set(false); }

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {

            if (paused.get()) {
                call.close(Status.UNAVAILABLE.withDescription("Server temporarily paused"), new Metadata());
                return new ServerCall.Listener<ReqT>() {}; // reject new requests
            }
            return next.startCall(call, headers);
        }
    }
}
