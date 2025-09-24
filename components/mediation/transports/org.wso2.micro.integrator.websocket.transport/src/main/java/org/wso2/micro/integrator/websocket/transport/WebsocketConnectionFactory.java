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

package org.wso2.micro.integrator.websocket.transport;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshakerFactory;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketVersion;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslHandler;
import org.apache.axiom.om.OMElement;
import org.apache.axis2.AxisFault;
import org.apache.axis2.description.Parameter;
import org.apache.axis2.description.TransportOutDescription;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.inbound.InboundResponseSender;
import org.wso2.micro.integrator.websocket.transport.utils.SSLUtil;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.xml.namespace.QName;

public class WebsocketConnectionFactory {

    private static final Log log = LogFactory.getLog(WebsocketConnectionFactory.class);

    private final TransportOutDescription transportOut;
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketClientHandler>> channelHandlerPool =
            new ConcurrentHashMap<String, ConcurrentHashMap<String, WebSocketClientHandler>>();

    public WebsocketConnectionFactory(TransportOutDescription transportOut) throws AxisFault {
        this.transportOut = transportOut;
        boolean sslEnabled = WebsocketConstants.WSS.equalsIgnoreCase(transportOut.getName());
        if (sslEnabled) {
            Parameter trustParam = transportOut.getParameter(WebsocketConstants.TRUST_STORE_CONFIG_ELEMENT);
            if (trustParam != null) {
                OMElement trustStoreLocationElem = trustParam.getParameterElement().
                        getFirstChildWithName(new QName(WebsocketConstants.TRUST_STORE_LOCATION));
                OMElement trustStorePasswordElem = trustParam.getParameterElement().
                        getFirstChildWithName(new QName(WebsocketConstants.
                                                                TRUST_STORE_PASSWORD));

                if (trustStoreLocationElem == null || trustStorePasswordElem == null) {
                    handleWssTrustStoreParameterError(
                            "Unable to read parameter(s) " + WebsocketConstants.TRUST_STORE_LOCATION + " and/or "
                                    + WebsocketConstants.TRUST_STORE_PASSWORD + " from Transport configurations");
                }
            } else {
                handleWssTrustStoreParameterError(
                        "Unable to read parameter(s) " + WebsocketConstants.TRUST_STORE_LOCATION + " and/or "
                                + WebsocketConstants.TRUST_STORE_PASSWORD + " from Transport configurations");
            }
        }
    }

    public WebSocketClientHandler getChannelHandler(final URI uri, final String sourceIdentifier,
                                                    final boolean handshakePresent, final String dispatchSequence,
                                                    final String dispatchErrorSequence, final String contentType,
                                                    final boolean isConnectionTerminate,
                                                    final Map<String, Object> headers,
                                                    final InboundResponseSender inboundResponseSender,
                                                    final String responseDispatchSequence,
                                                    final String responseErrorSequence) throws InterruptedException {
        WebSocketClientHandler channelHandler = getChannelHandlerFromPool(sourceIdentifier,
                                                                          getClientHandlerIdentifier(uri));
        if (channelHandler == null) {
            synchronized (sourceIdentifier.intern()) {
                channelHandler = getChannelHandlerFromPool(sourceIdentifier, getClientHandlerIdentifier(uri));
                if (channelHandler == null) {
                    if (isConnectionTerminate) {
                        return null;
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("Caching new connection with sourceIdentifier " + sourceIdentifier + " in the Thread,"
                                          + "ID: " + Thread.currentThread().getName() + "," + Thread.currentThread().getId());
                    }
                    channelHandler = cacheNewConnection(uri, sourceIdentifier, dispatchSequence,
                                                        dispatchErrorSequence, contentType, headers, inboundResponseSender,
                                                        responseDispatchSequence, responseErrorSequence);
                }
            }
        }
        channelHandler.handshakeFuture().sync();
        return channelHandler;
    }

    public String getClientHandlerIdentifier(final URI uri) {
        final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
        final int port = uri.getPort();
        final String subscriberPath = uri.getPath();
        return host.concat(String.valueOf(port)).concat(subscriberPath);
    }

    public WebSocketClientHandler cacheNewConnection(final URI uri, final String sourceIdentifier,
                                                     String dispatchSequence, String dispatchErrorSequence,
                                                     String contentType, Map<String, Object> headers,
                                                     InboundResponseSender inboundResponseSender,
                                                     String responseDispatchSequence,
                                                     String responseErrorSequence) {
        if (log.isDebugEnabled()) {
            log.debug("Creating a Connection for the specified WS endpoint.");
        }
        final WebSocketClientHandler handler;

        try {

            String scheme = uri.getScheme() == null ? WebsocketConstants.WS : uri.getScheme();
            final String host = uri.getHost() == null ? "127.0.0.1" : uri.getHost();
            final int port;
            switch (scheme) {
            case WebsocketConstants.WS:
                if (uri.getPort() < 0) {
                    // Setting default port for WS connection if port not defined in URI
                    port = WebsocketConstants.WEBSOCKET_DEFAULT_WS_PORT;
                    break;
                }
                port = uri.getPort();
                break;
            case WebsocketConstants.WSS:
                if (uri.getPort() < 0) {
                    // Setting default port for WSS connection if port not defined in URI
                    port = WebsocketConstants.WEBSOCKET_DEFAULT_WSS_PORT;
                    break;
                }
                port = uri.getPort();
                break;
            default:
                // If scheme does not belong to either ws or wss schemes, we return null
                return null;
            }
            final boolean ssl = WebsocketConstants.WSS.equalsIgnoreCase(scheme);
            final SslContext sslCtx;
            if (ssl) {
                Parameter trustParam = transportOut.getParameter(WebsocketConstants.TRUST_STORE_CONFIG_ELEMENT);
                if (trustParam != null) {
                    OMElement trustStoreLocationElem = trustParam.getParameterElement().
                            getFirstChildWithName(new QName(WebsocketConstants.TRUST_STORE_LOCATION));
                    OMElement trustStorePasswordElem = trustParam.getParameterElement().
                            getFirstChildWithName(new QName(WebsocketConstants.
                                                                    TRUST_STORE_PASSWORD));
                    OMElement trustStoreTypeElem = trustParam.getParameterElement().
                            getFirstChildWithName(new QName(WebsocketConstants.
                                    TRUST_STORE_TYPE));

                    final String location = trustStoreLocationElem.getText();
                    final String storePassword = trustStorePasswordElem.getText();
                    final String storetype = trustStoreTypeElem.getText();
                    sslCtx = SslContextBuilder.forClient()
                            .trustManager(SSLUtil.createTrustmanager(location, storePassword, storetype)).build();
                } else {
                    sslCtx = null;
                }
            } else {
                sslCtx = null;
            }

            if (sourceIdentifier.equals(WebsocketConstants.UNIVERSAL_SOURCE_IDENTIFIER)) {
                Parameter dispatchParam = transportOut
                        .getParameter(WebsocketConstants.WEBSOCKET_OUTFLOW_DISPATCH_SEQUENCE);
                if (dispatchParam != null) {
                    dispatchSequence = dispatchParam.getParameterElement().getText();
                }
                Parameter errorParam = transportOut
                        .getParameter(WebsocketConstants.WEBSOCKET_OUTFLOW_DISPATCH_FAULT_SEQUENCE);
                if (errorParam != null) {
                    dispatchErrorSequence = errorParam.getParameterElement().getText();
                }

            }

            DefaultHttpHeaders defaultHttpHeaders = new DefaultHttpHeaders();

            // If there are any custom headers, add them to the defaultHttpHeader.
            if (headers.size() > 0) {
                for (Map.Entry<String, Object> header : headers.entrySet()) {
                    defaultHttpHeaders.add(header.getKey(), header.getValue());
                }
            }

            final EventLoopGroup group = new NioEventLoopGroup();
            handler = new WebSocketClientHandler(WebSocketClientHandshakerFactory
                                                         .newHandshaker(uri, WebSocketVersion.V13, contentType != null ?
                                                                 SubprotocolBuilderUtil
                                                                         .contentTypeToSyanapeSubprotocol(contentType) :
                                                                 null, false, defaultHttpHeaders));
            if (!(WebsocketConstants.UNIVERSAL_SOURCE_IDENTIFIER).equals(sourceIdentifier)) {
                handler.registerWebsocketResponseSender(inboundResponseSender);
                handler.setDispatchSequence(responseDispatchSequence);
                handler.setDispatchErrorSequence(responseErrorSequence);
            }
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class).handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ChannelPipeline p = ch.pipeline();
                    if (sslCtx != null) {
                        SslHandler sslHandler = sslCtx.newHandler(ch.alloc(), host, port);
                        Parameter wsEnableHostnameVerification = transportOut
                                .getParameter(WebsocketConstants.WEBSOCKET_HOSTNAME_VERIFICATION_CONFIG);
                        if (wsEnableHostnameVerification != null
                                && wsEnableHostnameVerification.getValue() != null
                                && !wsEnableHostnameVerification.getValue().toString().isEmpty()
                                && Boolean.parseBoolean(wsEnableHostnameVerification.getValue().toString())) {
                            SSLEngine sslEngine = sslHandler.engine();
                            SSLParameters sslParams = sslEngine.getSSLParameters();
                            sslParams.setEndpointIdentificationAlgorithm("HTTPS");
                            sslEngine.setSSLParameters(sslParams);
                        }
                        p.addLast(sslHandler);
                    }
                    p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192),
                              new WebSocketFrameAggregator(Integer.MAX_VALUE), handler);
                }
            });

            Channel ch = b.connect(uri.getHost(), port).sync().channel();
            ch.closeFuture().addListener(new ChannelFutureListener() {
                @Override
                public void operationComplete(ChannelFuture channelFuture) throws Exception {
                    if (log.isDebugEnabled()) {
                        log.debug("OperationComplete ChannelFuture triggered on sourceIdentifier: " + sourceIdentifier
                                          + ", clientIdentifier: " + getClientHandlerIdentifier(uri)
                                          + ", in the Thread,ID: " + Thread.currentThread().getName() + ","
                                          + Thread.currentThread().getId());
                    }
                    group.shutdownGracefully();
                    removeChannelHandler(sourceIdentifier, getClientHandlerIdentifier(uri));
                }
            });
            handler.setDispatchSequence(dispatchSequence);
            handler.setDispatchErrorSequence(dispatchErrorSequence);
            addChannelHandler(sourceIdentifier, getClientHandlerIdentifier(uri), handler);
            return handler;

        } catch (InterruptedException e) {
            log.error("Interruption occured while connecting to the remote WS endpoint", e);
        } catch (SSLException e) {
            log.error("Error occurred while building the SSL context fo WSS endpoint", e);
        }

        return null;
    }

    private static void handleWssTrustStoreParameterError(String errorMsg) throws AxisFault {
        log.error(errorMsg);
        throw new AxisFault(errorMsg);
    }

    public void addChannelHandler(String sourceIdentifier, String clientIdentifier,
                                  WebSocketClientHandler clientHandler) {
        if (log.isDebugEnabled()) {
            log.debug("Adding channel for on sourceIdentifier: " + sourceIdentifier + ", clientIdentifier: "
                              + clientIdentifier + ", in the Thread,ID: " + Thread.currentThread().getName() + ","
                              + Thread.currentThread().getId());
        }
        ConcurrentHashMap<String, WebSocketClientHandler> handlerMap = channelHandlerPool.get(sourceIdentifier);
        if (handlerMap == null) {
            handlerMap = new ConcurrentHashMap<String, WebSocketClientHandler>();
            handlerMap.put(clientIdentifier, clientHandler);
            channelHandlerPool.put(sourceIdentifier, handlerMap);
        } else {
            handlerMap.put(clientIdentifier, clientHandler);
        }
    }

    public WebSocketClientHandler getChannelHandlerFromPool(String sourceIdentifier, String clientIdentifier) {
        if (log.isDebugEnabled()) {
            log.debug("Fetching channel for on sourceIdentifier: " + sourceIdentifier + ", clientIdentifier: "
                              + clientIdentifier + ", in the Thread,ID: " + Thread.currentThread().getName() + ","
                              + Thread.currentThread().getId());
        }
        ConcurrentHashMap<String, WebSocketClientHandler> handlerMap = channelHandlerPool.get(sourceIdentifier);
        if (handlerMap == null) {
            return null;
        } else {
            return handlerMap.get(clientIdentifier);
        }
    }

    public void removeChannelHandler(String sourceIdentifier, String clientIdentifier) {
        if (log.isDebugEnabled()) {
            log.debug("Removing channel for on sourceIdentifier: " + sourceIdentifier + ", clientIdentifier: "
                              + clientIdentifier + ", in the Thread,ID: " + Thread.currentThread().getName() + ","
                              + Thread.currentThread().getId());
        }
        ConcurrentHashMap<String, WebSocketClientHandler> handlerMap = channelHandlerPool.get(sourceIdentifier);
        handlerMap.remove(clientIdentifier);
        if (handlerMap.isEmpty()) {
            handlerMap.clear();
            channelHandlerPool.remove(sourceIdentifier);
        }
    }

}
