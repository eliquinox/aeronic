package io.aeronic.sync;

import io.aeronic.Aeronic;

@Aeronic
public interface SyncEventsResponse
{
    // SyncEvents subscriber will need to publish on this topic
    void onEventResponse(long correlationId, long value);
}
