// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.snap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambdaworks.snap.protocol.*;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.*;
import org.jboss.netty.channel.socket.ClientSocketChannelFactory;
import org.jboss.netty.channel.socket.nio.NioClientSocketChannelFactory;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.HashedWheelTimer;
import org.jboss.netty.util.Timer;

import javax.net.ssl.*;
import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Client for Apple's Push Notification Service. This client maintains a persistent connection
 * to the notification gateway, and also periodically connects to the feedback service.
 *
 * @author Will Glozer
 */
public class PushNotificationClient {
    private ClientBootstrap bootstrap;
    private SSLContext sslContext;
    private ChannelGroup channels;
    private PushNotificationConnection connection;
    private FeedbackServiceConnection feedback;
    private AtomicLong counter;
    private ObjectMapper mapper;

    /**
     * Create a new client that connects to the specified {@link Environment environment}.
     *
     * @param env       Push notification environment.
     * @param keystore  Keystore containing client private key and certificate.
     * @param passwd    Keystore password.
     */
    public PushNotificationClient(Environment env, KeyStore keystore, char[] passwd) throws GeneralSecurityException {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(keystore, passwd);

        sslContext = SSLContext.getInstance("TLS");
        sslContext.init(kmf.getKeyManagers(), loadTrustManagers(), null);

        Timer timer = new HashedWheelTimer();

        ExecutorService connectors = Executors.newFixedThreadPool(1);
        ExecutorService workers    = Executors.newCachedThreadPool();
        ClientSocketChannelFactory factory = new NioClientSocketChannelFactory(connectors, workers);

        bootstrap = new ClientBootstrap(factory);
        channels  = new DefaultChannelGroup();
        counter   = new AtomicLong(0);
        mapper    = new ObjectMapper();

        final BlockingQueue<PushNotification> queue = new LinkedBlockingQueue<PushNotification>();
        final ConnectionWatchdog watchdog = new ConnectionWatchdog(bootstrap, channels, timer);
        final PushNotificationHandler handler = new PushNotificationHandler(queue, mapper);
        connection = new PushNotificationConnection(queue);

        bootstrap.setPipelineFactory(new ChannelPipelineFactory() {
            @Override
            public ChannelPipeline getPipeline() throws Exception {
                SSLEngine engine = sslContext.createSSLEngine();
                engine.setUseClientMode(true);
                return Channels.pipeline(watchdog, new SslHandler(engine), handler, connection);
            }
        });

        feedback = new FeedbackServiceConnection(env, bootstrap, sslContext, timer);

        bootstrap.connect(env.gateway);
    }

    /**
     * Set the feedback polling interval.
     *
     * @param interval  Feedback polling interval.
     * @param unit      Unit of time for the interval.
     */
    public void setFeedbackInterval(int interval, TimeUnit unit) {
        feedback.setInterval(interval, unit);
    }

    /**
     * Add a feedback listener.
     *
     * @param listener  Listener.
     */
    public void addFeedbackListener(FeedbackListener listener) {
        feedback.addListener(listener);
    }

    /**
     * Remove a feedback listener.
     *
     * @param listener  Listener.
     */
    public void removeFeedbackListener(FeedbackListener listener) {
        feedback.removeListener(listener);
    }

    /**
     * Create a new push notification. The notification will be assigned a monotonically increasing
     * unsigned int id that begins at zero and wraps around at 2^32-1.
     *
     * @param token Target device token.
     *
     * @return A new notification instance.
     */
    public PushNotification create(byte[] token) {
        return new PushNotification(counter.incrementAndGet(), token);
    }

    /**
     * Send a push notification.
     *
     * @param notification  Push notification.
     */
    public void send(PushNotification notification) {
        connection.send(notification);
    }

    /**
     * Shutdown this client and close all open connections. The client should be
     * discarded after calling shutdown.
     */
    public void shutdown() {
        for (Channel c : channels) {
            ChannelPipeline pipeline = c.getPipeline();
            PushNotificationConnection connection = pipeline.get(PushNotificationConnection.class);
            connection.close();
        }
        ChannelGroupFuture future = channels.close();
        future.awaitUninterruptibly();
        bootstrap.releaseExternalResources();
    }

    /**
     * Create an array of {@link TrustManager}s that only trust certificates issued
     * by the APNS CA, Entrust.
     *
     * @return The trust managers.
     * @throws GeneralSecurityException when the trust managers cannot be loaded.
     */
    private TrustManager[] loadTrustManagers() throws GeneralSecurityException {
        try {
            InputStream is = getClass().getResourceAsStream("/entrust.keystore");
            try {
                KeyStore keystore = KeyStore.getInstance("JKS");
                keystore.load(is, "changeit".toCharArray());

                String alg = TrustManagerFactory.getDefaultAlgorithm();
                TrustManagerFactory tmf = TrustManagerFactory.getInstance(alg);
                tmf.init(keystore);

                return tmf.getTrustManagers();
            } finally {
                is.close();
            }
        } catch (IOException e) {
            throw new GeneralSecurityException("Error loading CA keystore", e);
        }
    }
}

