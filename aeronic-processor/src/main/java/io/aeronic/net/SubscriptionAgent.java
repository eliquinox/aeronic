package io.aeronic.net;

import io.aeron.Subscription;
import io.aeron.logbuffer.Header;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.Agent;

public class SubscriptionAgent<T> implements Agent
{
    private final Subscription subscription;
    protected final AbstractSubscriberInvoker<T> invoker;

    public SubscriptionAgent(final Subscription subscription, final AbstractSubscriberInvoker<T> invoker)
    {
        this.subscription = subscription;
        this.invoker = invoker;
    }

    @Override
    public int doWork()
    {
        return subscription.poll(this::handle, Integer.MAX_VALUE);
    }

    @Override
    public String roleName()
    {
        return invoker.getSubscriber().getClass().getSimpleName();
    }

    public void handle(final DirectBuffer buffer, final int offset, final int length, final Header header)
    {
        invoker.handle(buffer, offset);
    }
}
