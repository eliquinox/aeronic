package io.aeronic.cluster;

import io.aeron.cluster.service.ClientSession;
import io.aeronic.net.AbstractSubscriberInvoker;
import org.agrona.DirectBuffer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class AeronicClusteredServiceRegistry
{
    private final Map<Long, AbstractSubscriberInvoker<?>> subscriberBySessionId = new HashMap<>();
    private final Map<String, AbstractSubscriberInvoker<?>> invokerByName = new HashMap<>();
    private final Map<String, ClientSessionPublication<?>> clientSessionPublicationByName = new HashMap<>();

    public void registerEgressPublisher(final ClientSessionPublication<?> clientSessionPublication)
    {
        clientSessionPublicationByName.put(clientSessionPublication.getName(), clientSessionPublication);
    }

    public void registerIngressSubscriberInvoker(final AbstractSubscriberInvoker<?> subscriberInvoker)
    {
        Arrays.stream(subscriberInvoker.getSubscriber().getClass().getInterfaces())
            .map(Class::getCanonicalName)
            .forEach(subscriberInterface -> invokerByName.put(subscriberInterface + "__IngressPublisher", subscriberInvoker));
    }

    public void onSessionOpen(final ClientSession session)
    {
        final byte[] encodedPrincipal = session.encodedPrincipal();
        final String subscriberName = new String(encodedPrincipal);

        if (encodedPrincipal.length != 0)
        {
            final AbstractSubscriberInvoker<?> invoker = invokerByName.get(subscriberName);
            if (invoker != null)
            {
                subscriberBySessionId.put(session.id(), invoker);
            }

            if (subscriberName.endsWith("__EgressSubscriber"))
            {
                clientSessionPublicationByName.get(subscriberName.split("__")[0] + "__EgressPublisher").bindClientSession(session);
            }
        }
    }

    public void onSessionMessage(final ClientSession session, final DirectBuffer buffer, final int offset)
    {
        subscriberBySessionId.get(session.id()).handle(buffer, offset);
    }
}
