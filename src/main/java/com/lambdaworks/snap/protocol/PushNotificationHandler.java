// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.snap.protocol;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lambdaworks.snap.PushNotification;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * A netty {@link ChannelHandler} responsible for writing push notifications and
 * reading responses from the server.
 *
 * @author Will Glozer
 */
public class PushNotificationHandler extends SimpleChannelHandler implements ChannelFutureListener {
    private Logger logger = LoggerFactory.getLogger(getClass());

    protected BlockingQueue<PushNotification> queue;
    protected ChannelBuffer buffer;
    protected ObjectMapper mapper;

    /**
     * Initialize a new instance that handles notifications from the supplied queue.
     *
     * @param queue     Command queue.
     * @param mapper    Object mapper for payload.
     */
    public PushNotificationHandler(BlockingQueue<PushNotification> queue, ObjectMapper mapper) {
        this.queue  = queue;
        this.mapper = mapper;
    }

    @Override
    public void channelOpen(ChannelHandlerContext ctx, ChannelStateEvent e) throws Exception {
        buffer = ChannelBuffers.dynamicBuffer(ctx.getChannel().getConfig().getBufferFactory());
    }

    @Override
    public void writeRequested(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        PushNotification pn = (PushNotification) e.getMessage();

        Channel channel = ctx.getChannel();
        ChannelBuffer buf = ChannelBuffers.dynamicBuffer(channel.getConfig().getBufferFactory());
        encode(pn, buf);

        ChannelFuture f = e.getFuture();
        f.addListener(this);
        Channels.write(ctx, f, buf);
    }

    @Override
    public void messageReceived(ChannelHandlerContext ctx, MessageEvent e) throws Exception {
        ChannelBuffer input = (ChannelBuffer) e.getMessage();
        if (!input.readable()) return;

        buffer.discardReadBytes();
        buffer.writeBytes(input);

        decode(ctx, buffer);
    }

    @Override
    public void operationComplete(ChannelFuture future) throws Exception {
        if (future.isSuccess()) {
            queue.poll();
        }
    }

    protected void decode(ChannelHandlerContext ctx, ChannelBuffer buffer) throws InterruptedException {
        while (buffer.readableBytes() >= 6) {
            if (buffer.readByte() == 8) {
                byte status = buffer.readByte();
                long id     = buffer.readUnsignedInt();
                logger.error("Error response for notification id {}, status code {}", id, status);
            }
        }
    }

    protected void encode(PushNotification n, ChannelBuffer buf) {
        try {
            n.encode(mapper, buf);
        } catch (IOException e) {
            logger.error("Failed to encode notification id {}", n.id, e);
        }
    }
}
