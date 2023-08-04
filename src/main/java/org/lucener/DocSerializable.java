package org.lucener;

import org.lucener.util.Mappers;

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
    protected String serialize() {
        return Mappers.json(this);
    }

    /**
     * deserialize
     *
     * @return
     */
    protected abstract T deserialize(String s);
}
