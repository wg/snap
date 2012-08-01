package com.lambdaworks.snap;

/**
 * Feedback service listener.
 *
 * @author Will Glozer
 */
public interface FeedbackListener {
    void feedback(byte[] token, long timestamp);
}
