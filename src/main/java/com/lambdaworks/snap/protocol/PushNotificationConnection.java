// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.snap.protocol;

import com.lambdaworks.snap.PushNotification;
import org.jboss.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;

/**
 * Connection to the APNS notification gateway.
 *
 * @author Will Glozer
 */
public class PushNotificationConnection extends SimpleChannelUpstreamHandler {
    private Logger logger = LoggerFactory.getLogger(getClass());

    private BlockingQueue<PushNotification> queue;
    private Channel channel;
    private boolean closed;

    public PushNotificationConnection(BlockingQueue<PushNotification> queue) {
        this.queue = queue;
    }

    /**
     * Close the connection.
     */
    public synchronized void close() {
        if (!closed && channel != null) {
            ConnectionWatchdog watchdog = channel.getPipeline().get(ConnectionWatchdog.class);
            watchdog.setReconnect(false);
            closed = true;
            channel.close();
        }
    }

    @Override
    public synchronized void channelConnected(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        channel = ctx.getChannel();
        for (PushNotification cmd : queue) {
            channel.write(cmd);
        }
    }

    @Override
    public synchronized void channelClosed(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        if (closed) {
            queue.clear();
            queue = null;
            channel = null;
        }
    }

    public void send(PushNotification notification) {
        try {
            queue.put(notification);
            if (channel != null) {
                channel.write(notification);
            }
        } catch (Exception e) {
            logger.error("Error sending notification", e);
        }
    }


}
