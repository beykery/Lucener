package org.lucener;

import java.lang.reflect.Field;

/**
 * field description
 */
public class FieldDesc {

    private Field field;
    private boolean collection;
    private Class inner;
    private boolean stored;
    private boolean sort;
    private boolean tokenized;
    private boolean end;
    private boolean index;
    private boolean justSize;

    /**
     * construct
     *
     * @param field
     * @param collection
     * @param inner
     */
    public FieldDesc(Field field, boolean collection, Class inner, boolean stored, boolean sort) {
        this(field, collection, inner, stored, sort, false, false, true);
    }

    /**
     * construct
     *
     * @param field
     * @param collection
     * @param inner
     * @param stored
     * @param sort
     * @param tokenized
     */
    public FieldDesc(Field field, boolean collection, Class inner, boolean stored, boolean sort, boolean tokenized, boolean end, boolean index) {
        this(field, collection, inner, stored, sort, tokenized, end, index, false);
    }

    /**
     * construct
     *
     * @param field
     * @param collection
     * @param inner
     * @param stored
     * @param sort
     * @param tokenized
     * @param end
     * @param index
     * @param justSize
     */
    public FieldDesc(Field field, boolean collection, Class inner, boolean stored, boolean sort, boolean tokenized, boolean end, boolean index, boolean justSize) {
        this.field = field;
        this.collection = collection;
        this.inner = inner;
        this.stored = stored;
        this.sort = sort;
        this.tokenized = tokenized;
        this.end = end;
        this.index = index;
        this.justSize = justSize;
    }

    public boolean isCollection() {
        return collection;
    }

    public Field getField() {
        return field;
    }

    public Class getInner() {
        return inner;
    }

    public boolean isSort() {
        return sort;
    }

    public boolean isStored() {
        return stored;
    }

    public boolean isTokenized() {
        return tokenized;
    }

    public boolean isEnd() {
        return end;
    }

    public boolean isIndex() {
        return index;
    }

    /**
     * just size
     *
     * @return
     */
    public boolean isJustSize() {
        return justSize;
    }

    @Override
    public String toString() {
        return field.getName();
    }
}
