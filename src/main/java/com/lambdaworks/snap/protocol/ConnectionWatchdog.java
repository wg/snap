// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.snap.protocol;

import org.jboss.netty.bootstrap.ClientBootstrap;
import org.jboss.netty.channel.*;
import org.jboss.netty.channel.group.ChannelGroup;
import org.jboss.netty.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * A netty {@link ChannelHandler} responsible for monitoring the channel and
 * reconnecting when the connection is lost.
 *
 * @author Will Glozer
 */
public class ConnectionWatchdog extends SimpleChannelUpstreamHandler implements TimerTask {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private ClientBootstrap bootstrap;
    private Channel channel;
    private ChannelGroup channels;
    private Timer timer;
    private boolean reconnect;
    private int attempts;

    /**
     * Create a new watchdog that adds to new connections to the supplied {@link ChannelGroup}
     * and establishes a new {@link Channel} when disconnected, while reconnect is true.
     *
     * @param bootstrap Configuration for new channels.
     * @param channels  ChannelGroup to add new channels to.
     * @param timer     Timer used for delayed reconnect.
     */
    public ConnectionWatchdog(ClientBootstrap bootstrap, ChannelGroup channels, Timer timer) {
        this.bootstrap = bootstrap;
        this.channels  = channels;
        this.timer     = timer;
        this.reconnect = true;
    }

    public void setReconnect(boolean reconnect) {
        this.reconnect = reconnect;
    }

    @Override
    public synchronized void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channel = ctx.getChannel();
        channels.add(channel);
        attempts = 0;
        logger.info("Connected to {}", channel.getRemoteAddress());
        ctx.sendUpstream(e);
    }

    @Override
    public synchronized void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (reconnect) {
            if (attempts < 8) attempts++;
            int timeout = 2 << attempts;
            timer.newTimeout(this, timeout, TimeUnit.MILLISECONDS);
            logger.info("Disconnected, reconnecting in {}ms", timeout);
        }
        ctx.sendUpstream(e);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e) throws Exception {
        logger.error("Exception caught", e.getCause());
        ctx.getChannel().close();
    }

    /**
     * Reconnect to the remote address that the closed channel was connected to.
     *
     * @param timeout Timer task handle.
     *
     * @throws Exception when reconnection fails.
     */
    @Override
    public void run(Timeout timeout) throws Exception {
        bootstrap.connect(channel.getRemoteAddress());
    }
}
