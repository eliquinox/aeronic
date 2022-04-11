package io.aeronic.system.cluster;

import io.aeron.Aeron;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeronic.AeronicWizard;
import io.aeronic.SampleEvents;
import io.aeronic.SimpleEvents;
import io.aeronic.cluster.AeronicClusteredServiceContainer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

public class ClusterSystemTest
{

    private static final String INGRESS_CHANNEL = new ChannelUriStringBuilder()
        .media("udp")
        .reliable(true)
        .endpoint("localhost:40457")
        .build();

    private AeronicWizard aeronic;
    private Aeron aeron;
    private MediaDriver mediaDriver;
    private TestClusterNode clusterNode;
    private SimpleEventsImpl simpleEvents;
    private SampleEventsImpl sampleEvents;
    private TestClusterNode.Service clusteredService;

    @BeforeEach
    void setUp()
    {
        mediaDriver = MediaDriver.launchEmbedded(new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true)
            .spiesSimulateConnection(true)
            .threadingMode(ThreadingMode.SHARED)
            .sharedIdleStrategy(new BusySpinIdleStrategy()));

        final Aeron.Context aeronCtx = new Aeron.Context()
            .aeronDirectoryName(mediaDriver.aeronDirectoryName());

        aeron = Aeron.connect(aeronCtx);
        aeronic = new AeronicWizard(aeron);

        simpleEvents = new SimpleEventsImpl();
        sampleEvents = new SampleEventsImpl();
    }

    @AfterEach
    void tearDown()
    {
        aeronic.close();
        aeron.close();
        mediaDriver.close();
        clusterNode.close();
    }

    @Test
    public void clusterIngress()
    {
        clusteredService = new TestClusterNode.Service();

        final AeronicClusteredServiceContainer aeronicClusteredService = AeronicClusteredServiceContainer.configure()
            .clusteredService(clusteredService)
            .registerIngressSubscriber(SimpleEvents.class, simpleEvents)
            .registerIngressSubscriber(SampleEvents.class, sampleEvents)
            .create();

        clusterNode = new TestClusterNode(aeronicClusteredService, true);

        final SimpleEvents simpleEventsPublisher = aeronic.createClusterIngressPublisher(SimpleEvents.class, INGRESS_CHANNEL);
        final SampleEvents sampleEventsPublisher = aeronic.createClusterIngressPublisher(
            SampleEvents.class,
            new AeronCluster.Context()
                .errorHandler(Throwable::printStackTrace)
                .ingressChannel(INGRESS_CHANNEL)
                .aeronDirectoryName(aeron.context().aeronDirectoryName())
        );

        aeronic.awaitUntilPubsAndSubsConnect();

        simpleEventsPublisher.onEvent(101L);
        sampleEventsPublisher.onEvent(201L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> simpleEvents.value == 101L && sampleEvents.value == 201L && clusteredService.getMessageCount() == 2);
    }

    @Test
    public void clusterEgress()
    {
        clusteredService = new TestClusterNode.Service();

        final AeronicClusteredServiceContainer.Configuration configuration = AeronicClusteredServiceContainer.configure()
            .clusteredService(clusteredService)
            .registerEgressPublisher(SimpleEvents.class)
            .registerEgressPublisher(SampleEvents.class);

        final SimpleEvents simpleEventsPublisher = configuration.registry().getPublisherFor(SimpleEvents.class);
        final SampleEvents sampleEventsPublisher = configuration.registry().getPublisherFor(SampleEvents.class);

        final AeronicClusteredServiceContainer aeronicClusteredService = configuration.create();

        clusterNode = new TestClusterNode(aeronicClusteredService, true);

        aeronic.registerClusterEgressSubscriber(SimpleEvents.class, simpleEvents, INGRESS_CHANNEL);
        aeronic.registerClusterEgressSubscriber(SampleEvents.class, sampleEvents, INGRESS_CHANNEL);
        aeronic.start();
        aeronic.awaitUntilPubsAndSubsConnect();
        await().timeout(Duration.ofSeconds(1)).until(aeronicClusteredService::egressConnected);

        simpleEventsPublisher.onEvent(101L);
        sampleEventsPublisher.onEvent(202L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> simpleEvents.value == 101L && sampleEvents.value == 202L);
    }

    @Test
    public void clusterIngressAndEgress()
    {
        final SimpleEventsImpl clusterIngressSimpleEventsImpl = new SimpleEventsImpl();
        final SampleEventsImpl clusterIngressSampleEventsImpl = new SampleEventsImpl();

        clusteredService = new TestClusterNode.Service();

        final AeronicClusteredServiceContainer aeronicClusteredService = AeronicClusteredServiceContainer.configure()
            .clusteredService(clusteredService)
            .registerIngressSubscriber(SimpleEvents.class, clusterIngressSimpleEventsImpl)
            .registerIngressSubscriber(SampleEvents.class, clusterIngressSampleEventsImpl)
            .registerEgressPublisher(SimpleEvents.class)
            .registerEgressPublisher(SampleEvents.class)
            .create();

        final SimpleEvents clusterEgressSimpleEventsPublisher = aeronicClusteredService.getPublisherFor(SimpleEvents.class);
        final SampleEvents clusterEgressSampleEventsPublisher = aeronicClusteredService.getPublisherFor(SampleEvents.class);

        clusterNode = new TestClusterNode(aeronicClusteredService, true);

        final SimpleEvents clusterIngressSimpleEventsPublisher = aeronic.createClusterIngressPublisher(SimpleEvents.class, INGRESS_CHANNEL);
        final SampleEvents clusterIngressSampleEventsPublisher = aeronic.createClusterIngressPublisher(SampleEvents.class, INGRESS_CHANNEL);

        aeronic.registerClusterEgressSubscriber(SimpleEvents.class, simpleEvents, INGRESS_CHANNEL);
        aeronic.registerClusterEgressSubscriber(SampleEvents.class, sampleEvents, INGRESS_CHANNEL);
        aeronic.start();
        aeronic.awaitUntilPubsAndSubsConnect();
        await().timeout(Duration.ofSeconds(1)).until(aeronicClusteredService::egressConnected);

        // cluster -> client
        clusterEgressSimpleEventsPublisher.onEvent(101L);
        clusterEgressSampleEventsPublisher.onEvent(202L);


        // client -> cluster
        clusterIngressSimpleEventsPublisher.onEvent(303L);
        clusterIngressSampleEventsPublisher.onEvent(404L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> simpleEvents.value == 101L &&
                sampleEvents.value == 202L &&
                clusterIngressSimpleEventsImpl.value == 303L &&
                clusterIngressSampleEventsImpl.value == 404L);
    }

    @Test
    public void emptyClusteredServiceContainer()
    {
        clusteredService = new TestClusterNode.Service();

        final AeronicClusteredServiceContainer aeronicClusteredService = AeronicClusteredServiceContainer.configure()
            .clusteredService(clusteredService)
            .create();

        clusterNode = new TestClusterNode(aeronicClusteredService, true);

        final AeronCluster anotherClient = AeronCluster.connect(
            new AeronCluster.Context()
                .ingressChannel(INGRESS_CHANNEL)
                .aeronDirectoryName(aeron.context().aeronDirectoryName())
                .errorHandler(Throwable::printStackTrace));

        final ExpandableArrayBuffer buffer = new ExpandableArrayBuffer();
        buffer.putLong(0, 100);

        anotherClient.offer(buffer, 0, buffer.capacity());

        await().until(() -> clusteredService.getMessageCount() == 1);
    }

    public static class SimpleEventsImpl implements SimpleEvents
    {

        private volatile long value;

        @Override
        public void onEvent(final long value)
        {
            this.value = value;
        }
    }

    public static class SampleEventsImpl implements SampleEvents
    {

        private volatile long value;

        @Override
        public void onEvent(final long value)
        {
            this.value = value;
        }
    }
}
