package io.aeronic.system.multiparam;

import io.aeron.Aeron;
import io.aeron.driver.MediaDriver;
import io.aeron.driver.ThreadingMode;
import io.aeronic.AeronicWizard;
import org.agrona.concurrent.BusySpinIdleStrategy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static io.aeronic.Assertions.assertReflectiveEquals;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

public class AeronicMultiParamTest
{

    private static final String IPC = "aeron:ipc";
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

    @Test
    public void shouldSendAndReceiveOnTopicWithMultipleParams()
    {
        final MultiParamEvents publisher = aeronic.createPublisher(MultiParamEvents.class, IPC, 10);
        final MultiParamEventsImpl subscriberImpl = new MultiParamEventsImpl();
        aeronic.registerSubscriber(MultiParamEvents.class, subscriberImpl, IPC, 10);
        aeronic.start();
        aeronic.awaitUntilPubsAndSubsConnect();

        final long longValue = 2312312341324L;
        final int intValue = 123;
        final float floatValue = 1.21312f;
        final double doubleValue = .03412342;
        final byte byteValue = (byte) 56;
        final char charValue = 'a';
        final boolean booleanValue = true;
        final short shortValue = 123;
        final String stringValue = "stringValue";
        final Composite compositeValue = new Composite(12, Long.MAX_VALUE, true, Byte.MAX_VALUE, 123.123);
        final long[] longArray = { 1L, 2L, 3L, Long.MAX_VALUE, Long.MIN_VALUE };
        final int[] intArray = { 1, 2, 3 };
        final double[] doubleArray = { 1., 2., 3. };
        final float[] floatArray = { 1.f, 2.f, 3.f };
        final byte[] byteArray = { 0x1, 0x2, 0x5 };
        final char[] charArray = { '1', '2', '3' };

        publisher.onEvent(
            longValue,
            intValue,
            floatValue,
            doubleValue,
            byteValue,
            charValue,
            booleanValue,
            shortValue,
            stringValue,
            compositeValue,
            longArray,
            intArray,
            doubleArray,
            floatArray,
            byteArray,
            charArray
        );

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> {
                // wait for last updated value only because of "happens before"
                assertArrayEquals(charArray, subscriberImpl.charArray);
                return true;
            });

        assertEquals(longValue, subscriberImpl.longValue);
        assertEquals(intValue, subscriberImpl.intValue);
        assertEquals(floatValue, subscriberImpl.floatValue);
        assertEquals(doubleValue, subscriberImpl.doubleValue);
        assertEquals(byteValue, subscriberImpl.byteValue);
        assertEquals(charValue, subscriberImpl.charValue);
        assertEquals(booleanValue, subscriberImpl.booleanValue);
        assertEquals(stringValue, subscriberImpl.stringValue);
        assertReflectiveEquals(compositeValue, subscriberImpl.compositeValue);
        assertArrayEquals(longArray, subscriberImpl.longArray);
        assertArrayEquals(intArray, subscriberImpl.intArray);
        assertArrayEquals(floatArray, subscriberImpl.floatArray);
        assertArrayEquals(doubleArray, subscriberImpl.doubleArray);
        assertArrayEquals(byteArray, subscriberImpl.byteArray);

        publisher.onEvent(
            123L,
            intValue,
            floatValue,
            doubleValue,
            byteValue,
            charValue,
            false,
            (short) (shortValue + 1),
            stringValue,
            compositeValue,
            longArray,
            intArray,
            doubleArray,
            floatArray,
            byteArray,
            charArray
        );

        await()
            .timeout(Duration.ofSeconds(1))
            .until(() -> subscriberImpl.shortValue == 124);

        assertEquals(123L, subscriberImpl.longValue);
        assertEquals(intValue, subscriberImpl.intValue);
        assertEquals(floatValue, subscriberImpl.floatValue);
        assertEquals(doubleValue, subscriberImpl.doubleValue);
        assertEquals(byteValue, subscriberImpl.byteValue);
        assertEquals(charValue, subscriberImpl.charValue);
        assertFalse(subscriberImpl.booleanValue);
    }

    private static class MultiParamEventsImpl implements MultiParamEvents
    {
        private volatile long longValue;
        private volatile int intValue;
        private volatile float floatValue;
        private volatile double doubleValue;
        private volatile byte byteValue;
        private volatile char charValue;
        private volatile boolean booleanValue;
        private volatile short shortValue;
        private volatile String stringValue;
        private volatile Composite compositeValue;
        private volatile long[] longArray;
        private volatile int[] intArray;
        private volatile double[] doubleArray;
        private volatile float[] floatArray;
        private volatile byte[] byteArray;
        private volatile char[] charArray;

        @Override
        public void onEvent(
            final long longValue,
            final int intValue,
            final float floatValue,
            final double doubleValue,
            final byte byteValue,
            final char charValue,
            final boolean booleanValue,
            final short shortValue,
            final String stringValue,
            final Composite compositeValue,
            final long[] longArray,
            final int[] intArray,
            final double[] doubleArray,
            final float[] floatArray,
            final byte[] byteArray,
            final char[] charArray
        )
        {
            this.longValue = longValue;
            this.intValue = intValue;
            this.floatValue = floatValue;
            this.doubleValue = doubleValue;
            this.byteValue = byteValue;
            this.charValue = charValue;
            this.booleanValue = booleanValue;
            this.shortValue = shortValue;
            this.stringValue = stringValue;
            this.compositeValue = compositeValue;
            this.longArray = longArray;
            this.intArray = intArray;
            this.doubleArray = doubleArray;
            this.floatArray = floatArray;
            this.byteArray = byteArray;
            this.charArray = charArray;
        }
    }
}
