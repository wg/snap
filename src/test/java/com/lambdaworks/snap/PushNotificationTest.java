// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.snap;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jboss.netty.buffer.ChannelBuffer;
import org.jboss.netty.buffer.ChannelBuffers;
import org.junit.Before;
import org.junit.Test;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

public class PushNotificationTest {
    private AtomicLong counter;
    private ObjectMapper mapper;
    private JavaType payloadType;
    private SecureRandom random;

    private ChannelBuffer buffer;
    private PushNotification notification;

    private long id;
    private byte[] token;
    private Date expiry;

    public PushNotificationTest() throws Exception {
        counter     = new AtomicLong();
        mapper      = new ObjectMapper();
        payloadType = mapper.getTypeFactory().constructMapType(HashMap.class, String.class, Object.class);
        random      = SecureRandom.getInstance("SHA1PRNG");
    }

    @Before
    public void setup() {
        buffer = ChannelBuffers.dynamicBuffer();

        id     = counter.incrementAndGet();
        token  = new byte[32];
        random.nextBytes(token);
        expiry = null;

        notification = new PushNotification(id, token);
    }

    @SuppressWarnings("unchecked")
    protected TreeMap<String, Object> verify() throws Exception {
        if (expiry != null) notification.expiry(expiry);
        notification.encode(mapper, buffer);

        buffer.resetReaderIndex();

        assertEquals(1, buffer.readByte());
        assertEquals(id, buffer.readUnsignedInt());
        assertEquals((expiry != null ? expiry.getTime() / 1000 : 0), buffer.readInt());

        assertEquals(token.length, buffer.readShort());
        byte[] token = new byte[this.token.length];
        buffer.readBytes(token);
        assertArrayEquals(this.token, token);

        int len = buffer.readShort();
        assertEquals(len, buffer.readableBytes());

        byte[] bytes = new byte[len];
        buffer.readBytes(bytes);

        Map<String, Object> payload = mapper.readValue(bytes, payloadType);
        Map<String, Object> aps = (Map<String, Object>) payload.get("aps");
        assertNotNull(aps);

        if (aps.get("alert") instanceof Map) {
            Map<String, Object> alert = (Map<String, Object>) aps.get("alert");
            aps.put("alert", new TreeMap<String, Object>(alert));
        }

        return new TreeMap<String, Object>(payload);
    }

    @Test
    public void expiryNotSet() throws Exception {
        verify();
    }

    @Test
    public void expirySet() throws Exception {
        expiry = new Date();
        verify();
    }

    @Test
    public void sound() throws Exception {
        notification.sound("default");
        assertEquals(aps("sound", "default"), verify());
    }

    @Test
    public void badge() throws Exception {
        notification.badge(1);
        assertEquals(aps("badge", 1), verify());
    }

    @Test
    public void minimalAlert() throws Exception {
        notification.alert("msg");
        assertEquals(aps("alert", "msg"), verify());
    }

    @Test
    public void minimalLocalizedAlert() throws Exception {
        notification.alert().body("msg");
        assertEquals(aps("alert", map("body", "msg", "action-loc-key", null)), verify());
    }

    @Test
    public void fullLocalizedAlert() throws Exception {
        PushNotification.Alert alert = notification.alert();
        alert.actionLocKey("action").locKey("key").locArgs("arg").launchImage("file");
        Map<String, Object> alertMap = new TreeMap<String, Object>();
        alertMap.put("action-loc-key", "action");
        alertMap.put("loc-key", "key");
        alertMap.put("loc-args", Arrays.asList("arg"));
        alertMap.put("launch-image", "file");
        assertEquals(aps("alert", alertMap), verify());
    }

    @Test
    public void extra() throws Exception {
        Map<String, Object> extra = notification.extra();
        extra.put("key", "value");
        assertEquals(map("aps", map(), "key", "value"), verify());
    }

    public Map<String, Object> aps(Object... o) {
        return map("aps", map(o));
    }

    public Map<String, Object> map(Object... o) {
        Map<String, Object> map = new TreeMap<String, Object>();
        for (int i = 0; i < o.length; i += 2) {
            map.put((String) o[i], o[i + 1]);
        }
        return map;
    }
}
