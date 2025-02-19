package com.reeman.delige.dispatch.mqtt;

public class Topic {

    public static String dispatchTopicPub(String hostname) {
        return "reeman/dispatch/" + hostname ;
    }

    public static String dispatchTopicSub(String hostname) {
        return "reeman/dispatch/#";
    }
}
