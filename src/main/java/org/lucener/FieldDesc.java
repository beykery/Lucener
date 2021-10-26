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
        this.field = field;
        this.collection = collection;
        this.inner = inner;
        this.stored = stored;
        this.sort = sort;
        this.tokenized = tokenized;
        this.end = end;
        this.index = index;
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

    @Override
    public String toString() {
        return field.getName();
    }
}
