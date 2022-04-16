package io.aeronic.codec;

public class SimpleImpl implements Encoder
{
    private final int anInt;
    private final byte aByte;
    private final long aLong;

    public SimpleImpl(final int anInt, final byte aByte, final long aLong)
    {
        this.anInt = anInt;
        this.aByte = aByte;
        this.aLong = aLong;
    }

    @Override
    public void encode(final BufferEncoder bufferEncoder)
    {
        bufferEncoder.encode(anInt);
        bufferEncoder.encode(aByte);
        bufferEncoder.encode(aLong);
    }

    @DecodedBy
    public static SimpleImpl decode(final BufferDecoder bufferDecoder)
    {
        final int anInt = bufferDecoder.decodeInt();
        final byte aByte = bufferDecoder.decodeByte();
        final long aLong = bufferDecoder.decodeLong();
        return new SimpleImpl(anInt, aByte, aLong);
    }
}
