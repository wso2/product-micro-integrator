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

package org.wso2.carbon.inbound.endpoint.protocol.websocket;

import io.netty.buffer.ByteBufInputStream;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.axiom.soap.SOAPFactory;
import org.apache.axiom.util.UIDGenerator;
import org.apache.axis2.AxisFault;
import org.apache.axis2.Constants;
import org.apache.axis2.builder.Builder;
import org.apache.axis2.builder.BuilderUtil;
import org.apache.axis2.builder.SOAPBuilder;
import org.apache.axis2.context.OperationContext;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.description.InOutAxisOperation;
import org.apache.axis2.transport.TransportUtils;
import org.apache.commons.io.input.AutoCloseInputStream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.SynapseException;
import org.apache.synapse.api.ApiConstants;
import org.apache.synapse.api.inbound.InboundApiHandler;
import org.apache.synapse.aspects.flow.statistics.collectors.RuntimeStatisticCollector;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.core.axis2.MessageContextCreatorForAxis2;
import org.apache.synapse.inbound.InboundEndpoint;
import org.apache.synapse.inbound.InboundEndpointConstants;
import org.apache.synapse.mediators.MediatorFaultHandler;
import org.apache.synapse.mediators.base.SequenceMediator;
import org.apache.synapse.transport.customlogsetter.CustomLogSetter;
import org.wso2.carbon.inbound.endpoint.osgi.service.ServiceReferenceHolder;
import org.wso2.carbon.inbound.endpoint.protocol.websocket.management.WebsocketEndpointManager;
import org.wso2.carbon.inbound.endpoint.protocol.websocket.management.WebsocketSubscriberPathManager;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpHeaders.Names.HOST;
import static org.wso2.carbon.inbound.endpoint.common.Constants.SUPER_TENANT_DOMAIN_NAME;
import static org.wso2.carbon.inbound.endpoint.common.Constants.TENANT_DOMAIN;

public class InboundWebsocketSourceHandler extends ChannelInboundHandlerAdapter {

    private static final Log log = LogFactory.getLog(InboundWebsocketSourceHandler.class);

    private InboundWebsocketChannelContext wrappedContext;
    private WebSocketServerHandshaker handshaker;
    private boolean isSSLEnabled;
    private URI subscriberPath;
    private String tenantDomain;
    private int port;
    private boolean dispatchToCustomSequence;
    private InboundWebsocketResponseSender responseSender;
    private static ArrayList<String> contentTypes = new ArrayList<>();
    private static ArrayList<String> otherSubprotocols = new ArrayList<>();
    private int clientBroadcastLevel;
    private String outflowDispatchSequence;
    private String outflowErrorSequence;
    private ChannelPromise handshakeFuture;
    private ArrayList<AbstractSubprotocolHandler> subprotocolHandlers;
    private String defaultContentType;
    private int portOffset;
    private boolean isHealthCheckCall = false;

    private InboundApiHandler inboundApiHandler = new InboundApiHandler();
    private static final AttributeKey<Map<String, Object>> WSO2_PROPERTIES = AttributeKey.valueOf("WSO2_PROPERTIES");

    static {
        contentTypes.add("application/xml");
        contentTypes.add("application/json");
        contentTypes.add("text/xml");
    }

    public InboundWebsocketSourceHandler() throws Exception {
    }

    public void setSubprotocolHandlers(ArrayList<AbstractSubprotocolHandler> subprotocolHandlers) {
        this.subprotocolHandlers = subprotocolHandlers;
        for (AbstractSubprotocolHandler handler : subprotocolHandlers) {
            otherSubprotocols.add(handler.getSubprotocolIdentifier());
        }
    }

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws Exception {
        this.isSSLEnabled = ctx.channel().pipeline().get("ssl") != null;
        this.wrappedContext = new InboundWebsocketChannelContext(ctx);
        this.port = ((InetSocketAddress) ctx.channel().localAddress()).getPort() - portOffset;
        this.responseSender = new InboundWebsocketResponseSender(this);
        WebsocketEndpointManager.getInstance().setSourceHandler(this);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        handshakeFuture = ctx.newPromise();
    }

    @SuppressWarnings("unchecked")
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof FullHttpRequest) {
            if (log.isDebugEnabled()) {
                log.debug("Connection upgrade request received on channel: " + ctx.channel().toString()
                                  + ", in the Thread,ID: " + Thread.currentThread().getName() + ","
                                  + Thread.currentThread().getId());
            }
            handleHandshake(ctx, (FullHttpRequest) msg);
        } else if (msg instanceof WebSocketFrame) {
            if (log.isDebugEnabled()) {
                log.debug("Websocket frame received on channel: " + ctx.channel().toString() + ", in the  Thread: "
                                  + Thread.currentThread().getName() + "," + Thread.currentThread().getId());
            }
            handleWebSocketFrame(ctx, (WebSocketFrame) msg);
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("ChannelInactive triggered with sourceIdentifier: " + ctx.channel().toString() + ", on Thread: "
                              + Thread.currentThread().getName() + "," + Thread.currentThread().getId());
        }
        if (isHealthCheckCall) {
            return;
        }
        String endpointName = WebsocketEndpointManager.getInstance().getEndpointName(port, tenantDomain);
        if (endpointName == null) {
            int portWithOffset = port + portOffset;
            handleException("Endpoint not found for port : " + portWithOffset + "" + " tenant domain : " + tenantDomain);
        }
        WebsocketSubscriberPathManager.getInstance()
                .addChannelContext(endpointName, subscriberPath.getPath(), wrappedContext);
        MessageContext synCtx = getSynapseMessageContext(tenantDomain, ctx);
        InboundEndpoint endpoint = synCtx.getConfiguration().getInboundEndpoint(endpointName);
        synCtx.setProperty(InboundWebsocketConstants.CONNECTION_TERMINATE, new Boolean(true));
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundWebsocketConstants.CONNECTION_TERMINATE, new Boolean(true));
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundWebsocketConstants.CLIENT_ID, ctx.channel().hashCode());
        injectForMediation(synCtx, endpoint);
    }

    private void handleHandshake(ChannelHandlerContext ctx, FullHttpRequest req) throws URISyntaxException, AxisFault {
        try {
            if (log.isDebugEnabled()) {
                WebsocketLogUtil.printHeaders(log, req, ctx);
            }
            // This block is for the health check of the ws ports
            if (!req.headers().contains(HttpHeaders.UPGRADE) && req.uri().equals("/health")) {
                isHealthCheckCall = true;
                FullHttpResponse httpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
                                                                            HttpResponseStatus.OK);
                httpResponse.headers().set("content-length", httpResponse.content().readableBytes());
                ctx.writeAndFlush(httpResponse);
                return;
            }
            WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req),
                                                                                              SubprotocolBuilderUtil.buildSubprotocolString(
                                                                                                      contentTypes,
                                                                                                      otherSubprotocols),
                                                                                              true);
            handshaker = wsFactory.newHandshaker(req);
            if (handshaker == null) {
                WebSocketServerHandshakerFactory.sendUnsupportedWebSocketVersionResponse(ctx.channel());
                if (log.isDebugEnabled()) {
                    WebsocketLogUtil.printSpecificLog(log, ctx, "Unsupported websocket version.");
                }
            } else {
                ChannelFuture future = handshaker.handshake(ctx.channel(), req);
                future.addListener(new ChannelFutureListener() {
                    public void operationComplete(ChannelFuture future) throws Exception {
                        if (future.isSuccess()) {
                            if (log.isDebugEnabled()) {
                                log.debug("Websocket Handshake is completed successfully with sourceIdentifier: "
                                                  + ctx.channel().toString() + ", on Thread: "
                                                  + Thread.currentThread().getName() + ","
                                                  + Thread.currentThread().getId());
                            }
                            handshakeFuture.setSuccess();
                        }
                    }
                });
            }

            List<Map.Entry<String, String>> httpHeaders = req.headers().entries();

            tenantDomain = SUPER_TENANT_DOMAIN_NAME;

            String endpointName = WebsocketEndpointManager.getInstance().getEndpointName(port, tenantDomain);
            if (endpointName == null) {
                int portWithOffset = port + portOffset;
                handleException(
                        "Endpoint not found for port : " + portWithOffset + "" + " tenant domain : " + tenantDomain);
            }

            WebsocketSubscriberPathManager.getInstance().addChannelContext(endpointName, subscriberPath.getPath(),
                                                                           wrappedContext);
            MessageContext synCtx = getSynapseMessageContext(tenantDomain, ctx);
            InboundEndpoint endpoint = synCtx.getConfiguration().getInboundEndpoint(endpointName);
            defaultContentType = endpoint.getParametersMap().get(
                    InboundWebsocketConstants.INBOUND_DEFAULT_CONTENT_TYPE);
            if (endpoint == null) {
                log.error("Cannot find deployed inbound endpoint " + endpointName + "for process request");
                return;
            }

            for (Map.Entry<String, String> entry : httpHeaders) {
                synCtx.setProperty(entry.getKey(), entry.getValue());
                ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty(entry.getKey(), entry.getValue());
            }

            synCtx.setProperty(InboundWebsocketConstants.SOURCE_HANDSHAKE_PRESENT, new Boolean(true));
            ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty(
                    InboundWebsocketConstants.SOURCE_HANDSHAKE_PRESENT, new Boolean(true));
            synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_SOURCE_HANDSHAKE_PRESENT, new Boolean(true));
            ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty(
                    InboundWebsocketConstants.WEBSOCKET_SOURCE_HANDSHAKE_PRESENT, new Boolean(true));
            ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty(InboundWebsocketConstants.CLIENT_ID,
                                                                                ctx.channel().hashCode());
            injectForMediation(synCtx, endpoint);
        } finally {
            //Release WebsocketFrame
            ReferenceCountUtil.release(req);
        }
    }

    private String getWebSocketLocation(FullHttpRequest req) throws URISyntaxException {
        String location = req.headers().get(HOST) + req.getUri();
        subscriberPath = new URI(req.getUri());
        if (isSSLEnabled) {
            return "wss://" + location;
        } else {
            return "ws://" + location;
        }
    }

    private boolean interceptWebsocketMessageFlow(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (handshaker.selectedSubprotocol() == null || subprotocolHandlers == null || (subprotocolHandlers != null
                && subprotocolHandlers.isEmpty())) {
            return false;
        }
        boolean continueFlow = false;
        for (AbstractSubprotocolHandler handler : subprotocolHandlers) {
            if (handshaker.selectedSubprotocol() != null && handshaker.selectedSubprotocol()
                    .contains(handler.getSubprotocolIdentifier())) {
                continueFlow = handler.handle(ctx, frame, subscriberPath.toString());
                break;
            }
        }
        return !continueFlow;
    }

    private void handleWebSocketFrame(ChannelHandlerContext ctx, WebSocketFrame frame) {

        try {
            if (log.isDebugEnabled()) {
                WebsocketLogUtil.printWebSocketFrame(log, frame, ctx, true);
            }

            if (handshakeFuture.isSuccess()) {

                String endpointName = WebsocketEndpointManager.getInstance().getEndpointName(port, tenantDomain);
                MessageContext synCtx = getSynapseMessageContext(tenantDomain, ctx);
                InboundEndpoint endpoint = synCtx.getConfiguration().getInboundEndpoint(endpointName);
                ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                        .setProperty(InboundWebsocketConstants.CLIENT_ID, ctx.channel().hashCode());

                if (endpoint == null) {
                    log.error("Cannot find deployed inbound endpoint " + endpointName + "for process request");
                    return;
                }

                if (interceptWebsocketMessageFlow(ctx, frame)) {
                    return;
                }

                if (frame instanceof CloseWebSocketFrame) {
                    try {
                        handleClientWebsocketChannelTermination(frame);
                        if (log.isDebugEnabled()) {
                            WebsocketLogUtil.printSpecificLog(log, ctx,
                                                              "Websocket channel is terminated successfully.");
                        }
                    } finally {
                        ReferenceCountUtil.release(frame);
                    }
                    return;
                } else if ((frame instanceof BinaryWebSocketFrame) && ((handshaker.selectedSubprotocol() == null) || (
                        handshaker.selectedSubprotocol() != null && !handshaker.selectedSubprotocol()
                                .contains(InboundWebsocketConstants.SYNAPSE_SUBPROTOCOL_PREFIX)))) {
                    String contentType = handshaker.selectedSubprotocol();
                    if (contentType == null && defaultContentType != null) {
                        contentType = defaultContentType;
                    }

                    handleWebsocketBinaryFrame(frame, synCtx);
                    org.apache.axis2.context.MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) synCtx)
                            .getAxis2MessageContext();

                    Builder builder = BuilderUtil.getBuilderFromSelector(contentType, axis2MsgCtx);
                    if (builder != null) {
                        if (InboundWebsocketConstants.BINARY_BUILDER_IMPLEMENTATION
                                .equals(builder.getClass().getName())) {
                            synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_BINARY_FRAME_PRESENT, true);
                        } else {
                            synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_BINARY_FRAME_PRESENT, false);
                        }
                        InputStream in = new AutoCloseInputStream(
                                new ByteBufInputStream((frame.duplicate()).content()));
                        OMElement documentElement = builder.processDocument(in, contentType, axis2MsgCtx);
                        synCtx.setEnvelope(TransportUtils.createSOAPEnvelope(documentElement));
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("BinaryWebSocketFrame being injected to Sequence on channel: "
                                          + ctx.channel().toString() + ", in the Thread,ID: "
                                          + Thread.currentThread().getName() + ","
                                          + Thread.currentThread().getId());
                    }
                    injectForMediation(synCtx, endpoint);
                    return;
                } else if ((frame instanceof TextWebSocketFrame) && ((handshaker.selectedSubprotocol() == null) || (
                        handshaker.selectedSubprotocol() != null && !handshaker.selectedSubprotocol()
                                .contains(InboundWebsocketConstants.SYNAPSE_SUBPROTOCOL_PREFIX)))) {
                    String contentType = handshaker.selectedSubprotocol();
                    if (contentType == null && defaultContentType != null) {
                        contentType = defaultContentType;
                    }
                    handleWebsocketPassthroughTextFrame(frame, synCtx);
                    org.apache.axis2.context.MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) synCtx)
                            .getAxis2MessageContext();

                    Builder builder = BuilderUtil.getBuilderFromSelector(contentType, axis2MsgCtx);
                    if (builder != null) {
                        if (builder != null && InboundWebsocketConstants.TEXT_BUILDER_IMPLEMENTATION
                                .equals(builder.getClass().getName())) {
                            synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_TEXT_FRAME_PRESENT, true);
                        } else {
                            synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_TEXT_FRAME_PRESENT, false);
                        }
                        InputStream in = new AutoCloseInputStream(
                                new ByteArrayInputStream(((TextWebSocketFrame) frame).duplicate().text().getBytes()));
                        OMElement documentElement = builder.processDocument(in, contentType, axis2MsgCtx);
                        synCtx.setEnvelope(TransportUtils.createSOAPEnvelope(documentElement));
                    }
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "TextWebSocketFrame being injected to Sequence on channel: " + ctx.channel().toString()
                                        + ", in the Thread,ID: " + Thread.currentThread().getName() + ","
                                        + Thread.currentThread().getId());
                    }
                    injectForMediation(synCtx, endpoint);
                    return;
                } else if ((frame instanceof TextWebSocketFrame) && handshaker.selectedSubprotocol() != null
                        && handshaker.selectedSubprotocol()
                        .contains(InboundWebsocketConstants.SYNAPSE_SUBPROTOCOL_PREFIX)) {
                    CustomLogSetter.getInstance().setLogAppender(endpoint.getArtifactContainerName());

                    String message = ((TextWebSocketFrame) frame).text();
                    String contentType = SubprotocolBuilderUtil.syanapeSubprotocolToContentType(
                            SubprotocolBuilderUtil.extractSynapseSubprotocol(handshaker.selectedSubprotocol()));

                    org.apache.axis2.context.MessageContext axis2MsgCtx = ((org.apache.synapse.core.axis2.Axis2MessageContext) synCtx)
                            .getAxis2MessageContext();

                    Builder builder = null;
                    if (contentType == null) {
                        log.debug("No content type specified. Using SOAP builder.");
                        builder = new SOAPBuilder();
                    } else {
                        int index = contentType.indexOf(';');
                        String type = index > 0 ? contentType.substring(0, index) : contentType;
                        try {
                            builder = BuilderUtil.getBuilderFromSelector(type, axis2MsgCtx);
                        } catch (AxisFault axisFault) {
                            log.error("Error while creating message builder :: " + axisFault.getMessage());
                        }
                        if (builder == null) {
                            if (log.isDebugEnabled()) {
                                log.debug("No message builder found for type '" + type + "'. Falling back to SOAP.");
                            }
                            builder = new SOAPBuilder();
                        }
                    }

                    OMElement documentElement = null;
                    InputStream in = new AutoCloseInputStream(new ByteArrayInputStream(message.getBytes()));
                    documentElement = builder.processDocument(in, contentType, axis2MsgCtx);
                    synCtx.setEnvelope(TransportUtils.createSOAPEnvelope(documentElement));
                    injectForMediation(synCtx, endpoint);
                } else if (frame instanceof PingWebSocketFrame) {
                    try {
                        if (log.isDebugEnabled()) {
                            log.debug("PingWebSocketFrame received on channel: " + ctx.channel().toString()
                                              + ", in the Thread,ID: " + Thread.currentThread().getName() + ","
                                              + Thread.currentThread().getId());
                        }
                        PongWebSocketFrame pongWebSocketFrame = new PongWebSocketFrame(frame.content().retain());
                        ctx.channel().writeAndFlush(pongWebSocketFrame);
                        if (log.isDebugEnabled()) {
                            WebsocketLogUtil.printWebSocketFrame(log, pongWebSocketFrame, ctx, false);
                        }
                    } finally {
                        ReferenceCountUtil.release(frame);
                    }
                    return;
                }

            } else {
                log.error(
                        "Handshake incomplete at source handler. Failed to inject websocket frames to Synapse engine");
            }
        } catch (Exception e) {
            log.error("Exception occured while injecting websocket frames to the Synapse engine", e);
        }

    }

    public void handleClientWebsocketChannelTermination(WebSocketFrame frame) throws AxisFault {

        handshaker.close(wrappedContext.getChannelHandlerContext().channel(), (CloseWebSocketFrame) frame.retain());
        String endpointName = WebsocketEndpointManager.getInstance().getEndpointName(port, tenantDomain);
        WebsocketSubscriberPathManager.getInstance()
                .removeChannelContext(endpointName, subscriberPath.getPath(), wrappedContext);

    }

    protected void handleWebsocketBinaryFrame(WebSocketFrame frame, MessageContext synCtx) throws AxisFault {
        String endpointName = WebsocketEndpointManager.getInstance().getEndpointName(port, tenantDomain);

        InboundEndpoint endpoint = synCtx.getConfiguration().getInboundEndpoint(endpointName);

        if (endpoint == null) {
            log.error("Cannot find deployed inbound endpoint " + endpointName + "for process request");
            return;
        }

        synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_BINARY_FRAME_PRESENT, new Boolean(true));
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundWebsocketConstants.WEBSOCKET_BINARY_FRAME_PRESENT, new Boolean(true));
        synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_BINARY_FRAME, frame);
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundWebsocketConstants.WEBSOCKET_BINARY_FRAME, frame);

    }

    protected void handleWebsocketPassthroughTextFrame(WebSocketFrame frame, MessageContext synCtx) throws AxisFault {
        String endpointName = WebsocketEndpointManager.getInstance().getEndpointName(port, tenantDomain);

        InboundEndpoint endpoint = synCtx.getConfiguration().getInboundEndpoint(endpointName);

        if (endpoint == null) {
            log.error("Cannot find deployed inbound endpoint " + endpointName + "for process request");
            return;
        }

        synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_TEXT_FRAME_PRESENT, new Boolean(true));
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundWebsocketConstants.WEBSOCKET_TEXT_FRAME_PRESENT, new Boolean(true));
        synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_TEXT_FRAME, frame);
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundWebsocketConstants.WEBSOCKET_TEXT_FRAME, frame);

    }

    public InboundWebsocketChannelContext getChannelHandlerContext() {
        return wrappedContext;
    }

    public URI getSubscriber() {
        return subscriberPath;
    }

    public String getSubscriberPath() {
        return subscriberPath.getPath();
    }

    public int getClientBroadcastLevel() {
        return clientBroadcastLevel;
    }

    public String getDefaultContentType() {
        return defaultContentType;
    }

    public void setOutflowDispatchSequence(String outflowDispatchSequence) {
        this.outflowDispatchSequence = outflowDispatchSequence;
    }

    public void setOutflowErrorSequence(String outflowErrorSequence) {
        this.outflowErrorSequence = outflowErrorSequence;
    }

    public void setClientBroadcastLevel(int clientBroadcastLevel) {
        this.clientBroadcastLevel = clientBroadcastLevel;
    }

    protected void handleException(String msg) {
        log.error(msg);
        throw new SynapseException(msg);
    }

    public int getPort() {
        return port;
    }

    public String getTenantDomain() {
        return tenantDomain;
    }

    private MessageContext getSynapseMessageContext(String tenantDomain, ChannelHandlerContext ctx) throws AxisFault {

        MessageContext synCtx = getSynapseMessageContext(tenantDomain);
        Object prop = ctx.channel().attr(WSO2_PROPERTIES).get();
        if (prop != null) {
            Map<String, Object> properties = (Map<String, Object>) prop;
            for (Map.Entry entry : properties.entrySet()) {
                if (log.isDebugEnabled()) {
                    log.debug("Setting synapse property key: " + " value: " + entry.getValue());
                }
                synCtx.setProperty(entry.getKey().toString(), entry.getValue());
            }
        }
        return synCtx;
    }

    public org.apache.synapse.MessageContext getSynapseMessageContext(String tenantDomain) throws AxisFault {
        MessageContext synCtx = createSynapseMessageContext(tenantDomain);
        synCtx.setProperty(SynapseConstants.IS_INBOUND, true);
        ((Axis2MessageContext) synCtx).getAxis2MessageContext().setProperty(SynapseConstants.IS_INBOUND, true);
        synCtx.setProperty(InboundEndpointConstants.INBOUND_ENDPOINT_RESPONSE_WORKER, responseSender);
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundEndpointConstants.INBOUND_ENDPOINT_RESPONSE_WORKER, responseSender);
        synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_SOURCE_HANDLER_CONTEXT,
                           wrappedContext.getChannelHandlerContext());
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundWebsocketConstants.WEBSOCKET_SOURCE_HANDLER_CONTEXT,
                             wrappedContext.getChannelHandlerContext());
        synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_SOURCE_CHANNEL_IDENTIFIER,
                wrappedContext.getChannelIdentifier());
        ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .setProperty(InboundWebsocketConstants.WEBSOCKET_SOURCE_CHANNEL_IDENTIFIER,
                        wrappedContext.getChannelIdentifier());
        if (outflowDispatchSequence != null) {
            synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_OUTFLOW_DISPATCH_SEQUENCE, outflowDispatchSequence);
            ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                    .setProperty(InboundWebsocketConstants.WEBSOCKET_OUTFLOW_DISPATCH_SEQUENCE,
                                 outflowDispatchSequence);
        }
        if (outflowErrorSequence != null) {
            synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_OUTFLOW_DISPATCH_FAULT_SEQUENCE,
                               outflowErrorSequence);
            ((Axis2MessageContext) synCtx).getAxis2MessageContext()
                    .setProperty(InboundWebsocketConstants.WEBSOCKET_OUTFLOW_DISPATCH_FAULT_SEQUENCE,
                                 outflowErrorSequence);
        }
        synCtx.setProperty(InboundWebsocketConstants.WEBSOCKET_SUBSCRIBER_PATH, subscriberPath.toString());
        return synCtx;
    }

    private static org.apache.synapse.MessageContext createSynapseMessageContext(String tenantDomain) throws AxisFault {
        org.apache.axis2.context.MessageContext axis2MsgCtx = createAxis2MessageContext();
        ServiceContext svcCtx = new ServiceContext();
        OperationContext opCtx = new OperationContext(new InOutAxisOperation(), svcCtx);
        axis2MsgCtx.setServiceContext(svcCtx);
        axis2MsgCtx.setOperationContext(opCtx);

        axis2MsgCtx.setProperty(TENANT_DOMAIN, SUPER_TENANT_DOMAIN_NAME);

        SOAPFactory fac = OMAbstractFactory.getSOAP11Factory();
        SOAPEnvelope envelope = fac.getDefaultEnvelope();
        axis2MsgCtx.setEnvelope(envelope);
        return MessageContextCreatorForAxis2.getSynapseMessageContext(axis2MsgCtx);
    }

    private static org.apache.axis2.context.MessageContext createAxis2MessageContext() {
        org.apache.axis2.context.MessageContext axis2MsgCtx = new org.apache.axis2.context.MessageContext();
        axis2MsgCtx.setMessageID(UIDGenerator.generateURNString());
        axis2MsgCtx.setConfigurationContext(
                ServiceReferenceHolder.getInstance().getConfigurationContextService().getServerConfigContext());
        axis2MsgCtx.setProperty(org.apache.axis2.context.MessageContext.CLIENT_API_NON_BLOCKING, Boolean.TRUE);
        axis2MsgCtx.setServerSide(true);

        return axis2MsgCtx;
    }

    private void injectForMediation(org.apache.synapse.MessageContext synCtx,
                                    InboundEndpoint endpoint) {
        SequenceMediator faultSequence = getFaultSequence(synCtx, endpoint);
        MediatorFaultHandler mediatorFaultHandler = new MediatorFaultHandler(faultSequence);
        synCtx.pushFaultHandler(mediatorFaultHandler);
        if (log.isDebugEnabled()) {
            log.debug("injecting message to sequence : " + endpoint.getInjectingSeq());
        }
        synCtx.setProperty("inbound.endpoint.name", endpoint.getName());
        synCtx.setProperty(ApiConstants.API_CALLER, endpoint.getName());

        if (RuntimeStatisticCollector.isStatisticsEnabled()) {
            populateStatisticsMetadata(synCtx, endpoint);
        }

        boolean isProcessed;
        try {
            org.apache.axis2.context.MessageContext msgCtx = ((Axis2MessageContext)synCtx).getAxis2MessageContext();
            msgCtx.setIncomingTransportName(new URI(handshaker.uri()).getScheme());
            msgCtx.setProperty(Constants.Configuration.TRANSPORT_IN_URL, handshaker.uri());
            isProcessed = inboundApiHandler.process(synCtx);
        } catch (URISyntaxException e) {
            log.error("Invalid URI: " + handshaker.uri());
            throw new SynapseException(e);
        }

        if (!isProcessed) {
            SequenceMediator injectingSequence = null;
            if (endpoint.getInjectingSeq() != null) {
                injectingSequence = (SequenceMediator) synCtx.getSequence(endpoint.getInjectingSeq());
            }
            if (injectingSequence == null) {
                injectingSequence = (SequenceMediator) synCtx.getMainSequence();
            }
            if (dispatchToCustomSequence) {
                String context = (subscriberPath.getPath()).substring(1);
                context = context.replace('/', '-');
                if (synCtx.getConfiguration().getDefinedSequences().containsKey(context))
                    injectingSequence = (SequenceMediator) synCtx.getSequence(context);
            }
            synCtx.getEnvironment().injectMessage(synCtx, injectingSequence);
        }
    }

    private SequenceMediator getFaultSequence(org.apache.synapse.MessageContext synCtx, InboundEndpoint endpoint) {
        SequenceMediator faultSequence = null;
        if (endpoint.getOnErrorSeq() != null) {
            faultSequence = (SequenceMediator) synCtx.getSequence(endpoint.getOnErrorSeq());
        }
        if (faultSequence == null) {
            faultSequence = (SequenceMediator) synCtx.getFaultSequence();
        }
        return faultSequence;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (!handshakeFuture.isDone()) {
            handshakeFuture.setFailure(cause);
        }
        ctx.close();
    }

    public void setDispatchToCustomSequence(boolean dispatchToCustomSequence) {
        this.dispatchToCustomSequence = dispatchToCustomSequence;
    }

    public void setPortOffset(int portOffset) {
        this.portOffset = portOffset;
    }

    private void populateStatisticsMetadata(org.apache.synapse.MessageContext synCtx, InboundEndpoint endpoint) {
        Map<String, Object> statisticsDetails = new HashMap<String, Object>();
        statisticsDetails.put(InboundEndpointConstants.INBOUND_ENDPOINT_PROTOCOL, endpoint.getProtocol());
        statisticsDetails.put(SynapseConstants.INBOUND_PORT, port);
        synCtx.setProperty(SynapseConstants.STATISTICS_METADATA, statisticsDetails);
    }

}
