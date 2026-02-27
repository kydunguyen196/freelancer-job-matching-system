package com.skillbridge.common.events;

public final class EventTopics {

    public static final String EXCHANGE_NAME = "skillbridge.events";
    public static final String PROPOSAL_CREATED_ROUTING_KEY = "proposal.created";
    public static final String PROPOSAL_ACCEPTED_ROUTING_KEY = "proposal.accepted";
    public static final String MILESTONE_COMPLETED_ROUTING_KEY = "milestone.completed";

    private EventTopics() {
    }
}
