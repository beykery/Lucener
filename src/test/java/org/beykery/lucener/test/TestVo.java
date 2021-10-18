package org.beykery.lucener.test;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.beykery.lucener.IntField;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TestVo {
    @IntField(sort = true, stored = true)
    private List<Integer> listInt;
}
