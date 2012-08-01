// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.snap.protocol;

import com.lambdaworks.snap.Environment;
import com.lambdaworks.snap.FeedbackListener;
import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.jboss.netty.handler.ssl.SslHandler;
import org.jboss.netty.util.*;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * Connection to the APNS Feedback Service.
 *
 * @author Will Glozer
 */
public class FeedbackServiceConnection extends SimpleChannelHandler implements TimerTask {
    private Environment environment;
    private ClientBootstrap bootstrap;
    private SSLContext sslContext;
    private ChannelBuffer buffer;
    private List<FeedbackListener> listeners;
    private Timer timer;
    private int interval;
    private TimeUnit unit;
    private Timeout timeout;

    public FeedbackServiceConnection(Environment env, ClientBootstrap bootstrap, SSLContext sslContext, Timer timer) {
        this.environment = env;
        this.bootstrap   = bootstrap;
        this.sslContext  = sslContext;
        this.timer       = timer;
        this.listeners   = new CopyOnWriteArrayList<FeedbackListener>();
        setInterval(10, TimeUnit.MINUTES);
    }

    public void setInterval(int interval, TimeUnit unit) {
        this.interval = interval;
        this.unit     = unit;
        if (timeout != null) timeout.cancel();
        timeout = timer.newTimeout(this, interval, unit);
    }

    public void addListener(FeedbackListener listener) {
        listeners.add(listener);
    }

    public void removeListener(FeedbackListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        timeout = timer.newTimeout(this, interval, unit);
        super.channelClosed(ctx, e);
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        buffer = ChannelBuffers.dynamicBuffer(ctx.getChannel().getConfig().getBufferFactory());
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ChannelBuffer input = (ChannelBuffer) e.getMessage();
        if (!input.readable()) return;

        buffer.discardReadBytes();
        buffer.writeBytes(input);

        decode(buffer);
    }

    protected void decode(ChannelBuffer buffer) {
        while (buffer.readableBytes() >= 6) {
            int time = buffer.readInt();
            int len  = buffer.readShort();

            byte[] token = new byte[len];
            buffer.readBytes(token);

            for (FeedbackListener l : listeners) {
                l.feedback(token, time);
            }
        }
    }

    @Override
    public void run(Timeout timeout) throws Exception {
        if (listeners.size() > 0) {
            SSLEngine engine = sslContext.createSSLEngine();
            engine.setUseClientMode(true);

            ChannelPipeline pipeline = Channels.pipeline(new SslHandler(engine), this);

            Channel c = bootstrap.getFactory().newChannel(pipeline);
            c.connect(environment.feedback);
        }
    }
}
