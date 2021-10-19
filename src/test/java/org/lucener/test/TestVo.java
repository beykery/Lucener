package org.lucener.test;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lucener.IntField;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestVo {
    @IntField(sort = true, stored = true)
    private List<Integer> listInt;
}
