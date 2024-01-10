package org.lucener.test;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lucener.*;
import org.lucener.util.Mappers;

import java.math.BigInteger;
import java.util.List;
import java.util.Set;

/**
 * for test index
 */
@Index(ikSmart = true, prefix = "a/b/")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TestEntity extends DocSerializable<TestEntity> {
    @DocId
    private String did;
    @IntField(sort = true, stored = true)
    private Integer x;
    @IntField(sort = true, stored = true)
    private int y;
    @LongField(sort = true, stored = true)
    private Long z;
    @LongField(sort = true, stored = true)
    private long zz;
    @FloatField(sort = true, stored = true)
    private float f;
    @FloatField(sort = true, stored = true)
    private Float ff;
    @DoubleField(sort = true, stored = true)
    private Double d;
    @DoubleField(sort = true, stored = true)
    private double dd;
    @StringField(stored = true)
    private String desc;
    @TextField(stored = true)
    private String content;

    @BigIntegerField(stored = true)
    private BigInteger big;

    @StringField(stored = false)
    private Set<String> tags;

    private List<TestVo> vos;
    private TestVo testVo;

    @Override
    public TestEntity deserialize(String s) {
        return Mappers.parseJson(s, new TypeReference<>() {
        });
    }
}
