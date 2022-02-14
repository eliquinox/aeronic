package io.aeronic.transport;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeronic.AeronicWizard;
import io.aeronic.SampleEvents;
import io.aeronic.SimpleEvents;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.awaitility.Awaitility.await;

public abstract class AeronicTransportTestBase
{
    private AeronicWizard aeronic;
    private Aeron aeron;
    private MediaDriver mediaDriver;

    @BeforeEach
    void setUp()
    {
        final MediaDriver.Context mediaDriverCtx = new MediaDriver.Context()
            .dirDeleteOnStart(true)
            .spiesSimulateConnection(true)
            .threadingMode(ThreadingMode.SHARED)
            .sharedIdleStrategy(new BusySpinIdleStrategy())
            .dirDeleteOnShutdown(true);

        mediaDriver = MediaDriver.launchEmbedded(mediaDriverCtx);

        final Aeron.Context aeronCtx = new Aeron.Context()
            .aeronDirectoryName(mediaDriver.aeronDirectoryName());

        aeron = Aeron.connect(aeronCtx);
        aeronic = new AeronicWizard(aeron);
    }

    @AfterEach
    void tearDown()
    {
        aeronic.close();
        aeron.close();
        mediaDriver.close();
    }

    public abstract String getPublicationChannel();

    public abstract String getSubscriptionChannel();

    @Test
    public void oneToOne()
    {
        final SampleEvents publisher = aeronic.createPublisher(SampleEvents.class, getPublicationChannel(), 10);
        final SampleEventsImpl subscriberImpl = new SampleEventsImpl();
        aeronic.registerSubscriber(SampleEvents.class, subscriberImpl, getSubscriptionChannel(), 10);
        aeronic.awaitUntilPubsAndSubsConnect();

        publisher.onEvent(123L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> subscriberImpl.value == 123L);
    }

    @Test
    public void oneToMany()
    {
        final SampleEvents publisher = aeronic.createPublisher(SampleEvents.class, getPublicationChannel(), 10);
        final SampleEventsImpl subscriberImpl1 = new SampleEventsImpl();
        final SampleEventsImpl subscriberImpl2 = new SampleEventsImpl();
        aeronic.registerSubscriber(SampleEvents.class, subscriberImpl1, getSubscriptionChannel(), 10);
        aeronic.registerSubscriber(SampleEvents.class, subscriberImpl2, getSubscriptionChannel(), 10);
        aeronic.awaitUntilPubsAndSubsConnect();

        publisher.onEvent(123L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> subscriberImpl1.value == 123L && subscriberImpl2.value == 123L);
    }

    @Test
    public void oneToManyOfDifferentTopics()
    {
        final SampleEvents sampleEventsPublisher = aeronic.createPublisher(SampleEvents.class, getPublicationChannel(), 10);

        final SampleEventsImpl sampleEventsSubscriber1 = new SampleEventsImpl();
        final SampleEventsImpl sampleEventsSubscriber2 = new SampleEventsImpl();
        aeronic.registerSubscriber(SampleEvents.class, sampleEventsSubscriber1, getSubscriptionChannel(), 10);
        aeronic.registerSubscriber(SampleEvents.class, sampleEventsSubscriber2, getSubscriptionChannel(), 10);
        aeronic.awaitUntilPubsAndSubsConnect();

        sampleEventsPublisher.onEvent(123L);

        final SimpleEvents simpleEventsPublisher = aeronic.createPublisher(SimpleEvents.class, getPublicationChannel(), 11);

        final SimpleEventsImpl simpleEventsSubscriber1 = new SimpleEventsImpl();
        final SimpleEventsImpl simpleEventsSubscriber2 = new SimpleEventsImpl();
        aeronic.registerSubscriber(SimpleEvents.class, simpleEventsSubscriber1, getSubscriptionChannel(), 11);
        aeronic.registerSubscriber(SimpleEvents.class, simpleEventsSubscriber2, getSubscriptionChannel(), 11);
        aeronic.awaitUntilPubsAndSubsConnect();

        simpleEventsPublisher.onEvent(456L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> sampleEventsSubscriber1.value == 123L && sampleEventsSubscriber2.value == 123L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> simpleEventsSubscriber1.value == 456L && simpleEventsSubscriber2.value == 456L);
    }

    @Test
    public void manyToMany()
    {
        final SampleEvents sampleEventsPublisher1 = aeronic.createPublisher(SampleEvents.class, getPublicationChannel(), 10);
        final SampleEvents sampleEventsPublisher2 = aeronic.createPublisher(SampleEvents.class, getPublicationChannel(), 10);

        final SampleEventsImpl sampleEventsSubscriber1 = new SampleEventsImpl();
        final SampleEventsImpl sampleEventsSubscriber2 = new SampleEventsImpl();
        aeronic.registerSubscriber(SampleEvents.class, sampleEventsSubscriber1, getSubscriptionChannel(), 10);
        aeronic.registerSubscriber(SampleEvents.class, sampleEventsSubscriber2, getSubscriptionChannel(), 10);
        aeronic.awaitUntilPubsAndSubsConnect();

        sampleEventsPublisher1.onEvent(123L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> sampleEventsSubscriber1.value == 123L && sampleEventsSubscriber2.value == 123L);

        sampleEventsPublisher2.onEvent(456L);

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> sampleEventsSubscriber1.value == 456L && sampleEventsSubscriber2.value == 456L);
    }

    private static class SampleEventsImpl implements SampleEvents
    {

        private volatile long value;

        @Override
        public void onEvent(final long value)
        {
            this.value = value;
        }
    }

    private static class SimpleEventsImpl implements SimpleEvents
    {

        private volatile long value;

        @Override
        public void onEvent(final long value)
        {
            this.value = value;
        }
    }
}