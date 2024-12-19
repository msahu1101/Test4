package com.mgm.payments.processing.service.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.NetworkInterface;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Enumeration;

@Slf4j
@Component
public class SnowFlakeSequenceGenerator {

    private static final int NODE_ID_BITS = 10;
    private static final int SEQUENCE_BITS = 10;

    private static final long MAX_NODE_ID = (long)(Math.pow(2, NODE_ID_BITS) - 1);
    private static final long MAX_SEQUENCE_BITS = (long)(Math.pow(2, SEQUENCE_BITS) - 1);

    // Custom Epoch (Mon, 01 Jan 2024 Midnight UTC = 2024-01-01T00:00:00Z)
    private static final long DEFAULT_CUSTOM_EPOCH =  1704067200000L;

    private volatile long lastTimestamp = -1L;
    private volatile long sequence = 0;

    private final long nodeId;
    private final long customEpoch;
    @Autowired
    public SnowFlakeSequenceGenerator() {
        this.nodeId = createNodeId();
        this.customEpoch = DEFAULT_CUSTOM_EPOCH;
    }
  
    public SnowFlakeSequenceGenerator(long nodeId) {
        this.nodeId = nodeId;
        this.customEpoch = DEFAULT_CUSTOM_EPOCH;
    }

    public synchronized long nextId() {
        long currentTimestamp = Instant.now().toEpochMilli() - customEpoch;

        if (currentTimestamp < lastTimestamp) {
            throw new IllegalStateException("Invalid System Clock!");
        }

        if (currentTimestamp == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE_BITS;
            if (sequence == 0) {
                // Sequence Exhausted, wait till next millisecond.
                currentTimestamp = waitNextMillis(currentTimestamp);
            }
        } else {
            // reset sequence to start with zero for the next millisecond
            sequence = 0;
        }

        lastTimestamp = currentTimestamp;

        return currentTimestamp | (nodeId << SEQUENCE_BITS);
    }

    private long createNodeId() {
        long node;
        try {
            StringBuilder sb = new StringBuilder();
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null) {
                    for(byte macPort: mac) {
                        sb.append(String.format("%02X", macPort));
                    }
                }
            }
            node = sb.toString().hashCode();
        } catch (Exception ex) {
        	node = (new SecureRandom().nextInt());
        }
        node = node & MAX_NODE_ID;
        return node;
    }

    // Block and wait till next millisecond
    private long waitNextMillis(long currentTimestamp) {
        while (currentTimestamp == lastTimestamp) {
            currentTimestamp = Instant.now().toEpochMilli() - customEpoch;
        }
        return currentTimestamp;
    }

}
