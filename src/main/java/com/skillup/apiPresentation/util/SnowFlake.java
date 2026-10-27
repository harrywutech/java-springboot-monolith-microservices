package com.skillup.apiPresentation.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Java implementation of the Snowflake distributed ID algorithm
 **/
@Service
public class SnowFlake {

    /**
     * Epoch timestamp
     */
    private final static long START_STMP = 1480166465631L;

    /**
     * Bit width of each component
     */
    private final static long SEQUENCE_BIT = 12; //Sequence bit width
    private final static long MACHINE_BIT = 5;   //Machine ID bit width
    private final static long DATACENTER_BIT = 5;//Datacenter ID bit width

    /**
     * Maximum value of each component
     */
    private final static long MAX_DATACENTER_NUM = -1L ^ (-1L << DATACENTER_BIT);
    private final static long MAX_MACHINE_NUM = -1L ^ (-1L << MACHINE_BIT);
    private final static long MAX_SEQUENCE = -1L ^ (-1L << SEQUENCE_BIT);

    /**
     * Left shift for each component
     */
    private final static long MACHINE_LEFT = SEQUENCE_BIT;
    private final static long DATACENTER_LEFT = SEQUENCE_BIT + MACHINE_BIT;
    private final static long TIMESTMP_LEFT = DATACENTER_LEFT + DATACENTER_BIT;

    private long datacenterId;  //Datacenter ID
    private long machineId;     //Machine ID
    private long sequence = 0L; //Sequence
    private long lastStmp = -1L;//Previous timestamp

    public SnowFlake(@Value("${order.dataCenter}") long datacenterId, @Value("${order.machineNum}")long machineId) {
        if (datacenterId > MAX_DATACENTER_NUM || datacenterId < 0) {
            throw new IllegalArgumentException("datacenterId can't be greater than MAX_DATACENTER_NUM or less than 0");
        }
        if (machineId > MAX_MACHINE_NUM || machineId < 0) {
            throw new IllegalArgumentException("machineId can't be greater than MAX_MACHINE_NUM or less than 0");
        }
        this.datacenterId = datacenterId;
        this.machineId = machineId;
    }

    /**
     * Generate the next ID
     *
     * @return
     */
    public synchronized long nextId() {
        long currStmp = getNewstmp();
        if (currStmp < lastStmp) {
            throw new RuntimeException("Clock moved backwards.  Refusing to generate id");
        }

        if (currStmp == lastStmp) {
            //Increment the sequence within the same millisecond
            sequence = (sequence + 1) & MAX_SEQUENCE;
            //The sequence has reached its maximum for this millisecond
            if (sequence == 0L) {
                currStmp = getNextMill();
            }
        } else {
            //Reset the sequence to zero in a new millisecond
            sequence = 0L;
        }

        lastStmp = currStmp;

        return (currStmp - START_STMP) << TIMESTMP_LEFT //Timestamp component
                | datacenterId << DATACENTER_LEFT       //Datacenter component
                | machineId << MACHINE_LEFT             //Machine component
                | sequence;                             //Sequence component
    }

    private long getNextMill() {
        long mill = getNewstmp();
        while (mill <= lastStmp) {
            mill = getNewstmp();
        }
        return mill;
    }

    private long getNewstmp() {
        return System.currentTimeMillis();
    }
}
