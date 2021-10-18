package org.beykery.lucener;

/**
 * deserialize and serialize for doc
 */
public abstract class DocSerializable<T> {

    /**
     * doc
     */
    public int doc;

    /**
     * score
     */
    public float score;

    /**
     * shard index
     */
    public int shardIndex;

    /**
     * serialize
     *
     * @return
     */
    abstract protected String serialize();

    /**
     * deserialize
     *
     * @return
     */
    abstract protected T deserialize(String s);
}
