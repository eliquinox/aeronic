package io.aeronic.system.cluster;

import io.aeron.Aeron;
import io.aeron.ChannelUriStringBuilder;
import io.aeron.cluster.client.AeronCluster;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeronic.AeronicWizard;
import io.aeronic.SampleEvents;
import io.aeronic.SimpleEvents;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
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
        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .dirDeleteOnShutdown(true)
            .spiesSimulateConnection(true)
            .threadingMode(ThreadingMode.SHARED)
            .sharedIdleStrategy(new BusySpinIdleStrategy());

        mediaDriver = MediaDriver.launchEmbedded(mediaDriverCtx);

        final Aeron.Context aeronCtx = new Aeron.Context()
            .aeronDirectoryName(mediaDriver.aeronDirectoryName());

        aeron = Aeron.connect(aeronCtx);
        aeronic = new AeronicWizard(aeron);
        simpleEvents = new SimpleEventsImpl();
        sampleEvents = new SampleEventsImpl();
        clusteredService = new TestClusterNode.Service(simpleEvents, sampleEvents);
        clusterNode = new TestClusterNode(clusteredService, true);
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
    public void clientToCluster()
    {
        final AeronCluster simpleEventsClusterClient = TestClusterClient.connectClientToCluster(SimpleEvents.class.getName(), INGRESS_CHANNEL);
        final AeronCluster sampleEventsClusterClient = TestClusterClient.connectClientToCluster(SampleEvents.class.getName(), INGRESS_CHANNEL);

        final SimpleEvents simpleEventsPublisher = aeronic.createClusterPublisher(SimpleEvents.class, simpleEventsClusterClient);
        final SampleEvents sampleEventsPublisher = aeronic.createClusterPublisher(SampleEvents.class, sampleEventsClusterClient);

        simpleEventsPublisher.onEvent(101L);
        sampleEventsPublisher.onEvent(201L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> simpleEvents.value == 101L && sampleEvents.value == 201L && clusteredService.getMessageCount() == 2);

        simpleEventsClusterClient.close();
        sampleEventsClusterClient.close();
    }

    @Test
    @Disabled
    public void clusterToClient()
    {

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
