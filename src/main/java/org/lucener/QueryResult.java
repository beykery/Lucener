package org.lucener;

import java.util.Collections;
import java.util.List;

/**
 * query result
 *
 * @param <T>
 */
public final class QueryResult<T> {

    /**
     * total hits
     */
    private final long total;

    /**
     * hits
     */
    private final List<T> result;

    public QueryResult(long total, List<T> result) {
        this.total = total;
        this.result = result;
    }

    /**
     * empty result
     *
     * @param <T>
     * @return
     */
    public static <T extends DocSerializable<T>> QueryResult<T> empty() {
        return new QueryResult<T>(0, Collections.EMPTY_LIST);
    }

    public long getTotal() {
        return total;
    }

    public List<T> getResult() {
        return result;
    }

    @Override
    public String toString() {
        return "QueryResult{" +
                "total=" + total +
                ", result=" + result +
                '}';
    }
}
