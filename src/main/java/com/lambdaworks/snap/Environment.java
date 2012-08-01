// Copyright (C) 2011 - Will Glozer.  All rights reserved.

package com.lambdaworks.snap;

import java.net.InetSocketAddress;

/**
 * Push notification environments, which consist of {@link #SANDBOX SANDBOX} and
 * {@link #PRODUCTION PRODUCTION}.
 *
 * @author Will Glozer
 */
public enum Environment {
    SANDBOX   ("sandbox.push.apple.com"),
    PRODUCTION("push.apple.com");

    public final InetSocketAddress gateway;
    public final InetSocketAddress feedback;

    Environment(String domain) {
        this.gateway  = new InetSocketAddress("gateway."  + domain, 2195);
        this.feedback = new InetSocketAddress("feedback." + domain, 2196);
    }
}
