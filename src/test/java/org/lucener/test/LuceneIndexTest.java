package org.lucener.test;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lucener.Lucener;
import org.lucener.QueryResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;


public class LuceneIndexTest {

    Lucener<TestEntity> lucener = null;

    @BeforeEach
    void setUp() throws Exception {
        lucener = Lucener.forClass(TestEntity.class);
    }

    @Test
    public void index() throws Exception {
        int size = 1000000;
        for (int i = 0; i < size; i++) {
            TestVo vo = TestVo.builder()
                    .listInt(Arrays.asList(1, 2, 3, 4, 5, 6))
                    .build();
            TestVo tvo = TestVo.builder()
                    .listInt(Arrays.asList(7, 8, 9))
                    .build();
            TestEntity en = TestEntity.builder()
                    .did(String.valueOf(i))
                    .x(i)
                    .y(2)
                    .d(1.0)
                    .dd(1.0)
                    .f(3f)
                    .ff(3f)
                    .z(4L)
                    .zz(4L)
                    .desc("desc")
                    .content("computer price is so high , and i want to go home . what are you thinking about ? what's wrong with you ? 天气也不错。")
                    .testVo(vo)                   // testVo.listInt = [1,2,3,4,5,6]
                    .vos(Arrays.asList(tvo, vo))  // vos.listInt = [1,2,3,4,5,6,7,8,9]
                    .tags(new HashSet<>(Arrays.asList("artwork", "tag" + i, "artwork" + i)))
                    .build();
            lucener.index(en);
        }
        lucener.commit();
    }

    @Test
    public void stats() throws Exception {
        IndexWriter.DocStats stats = lucener.docStats();
        System.out.println(stats.maxDoc);
        System.out.println(stats.numDocs);
    }

    @Test
    public void get() throws Exception {
        String id = "9999";
        TestEntity te = lucener.get(id);
        System.out.println(te);
    }

    @Test
    public void delete() throws Exception {
        String id = "9999";
        lucener.deleteDocuments("did", id);
        lucener.commit();
    }

    @Test
    public void queryByContent() throws Exception {
        String content = "computer";
        String price = "price";
        String desc = "desc";
        QueryResult<TestEntity> ret = lucener.query("content", content, 1, null);
        System.out.println(ret);
    }

    @Test
    public void queryByV() throws Exception {
        int v = 4;
        QueryResult<TestEntity> ret = lucener.query("vos.listInt", v, 1, null);
        System.out.println(ret);
    }

    @Test
    public void merge() throws Exception {
        lucener.forceMerge(5, true);
    }

    @Test
    public void check() throws Exception {
        String path = lucener.indexPath();
        lucener.close();
        Lucener.checkIndex(path, "-exorcise", "-verbose");
    }

    @Test
    public void queryByTag() throws Exception {
        BooleanQuery.Builder builder = Lucener.booleanQuery();
        Query query1 = lucener.buildExactQuery("tags", "artwork1");
        Query query2 = lucener.buildExactQuery("tags", "tag11");
        Lucener.should(builder, query1, query2);
        QueryResult<TestEntity> tes = lucener.query(builder.build(), 10, null);
        System.out.println(tes);

        // pageable test
//        tes = lucener.queryAfter(tes.getResult().get(tes.getResult().size() - 1), "tags", "artwork", 100, null);
//        System.out.println(tes.getResult());
//        System.out.println(tes.getTotal());
//        System.out.println(tes.getResult().size());
//
//        tes = lucener.queryAfter(tes.getResult().get(tes.getResult().size() - 1), "tags", "artwork", 100, null);
//        System.out.println(tes.getResult());
//        System.out.println(tes.getTotal());
//        System.out.println(tes.getResult().size());
    }


    @Test
    public void queryByTextToken() throws Exception {
        Sort sort = new Sort(
                //SortField.FIELD_SCORE,
                new SortedNumericSortField("x", SortField.Type.INT, true)

//                new SortField("", new FieldComparatorSource() {
//
//                    @Override
//                    public FieldComparator<?> newComparator(String fieldname, int numHits, boolean enableSkipping, boolean reversed) {
//                        return new FieldComparator<Object>() {
//                            @Override
//                            public int compare(int slot1, int slot2) {
//                                return 0;
//                            }
//
//                            @Override
//                            public void setTopValue(Object value) {
//
//                            }
//
//                            @Override
//                            public Object value(int slot) {
//                                return null;
//                            }
//
//                            @Override
//                            public LeafFieldComparator getLeafComparator(LeafReaderContext context) throws IOException {
//                                return null;
//                            }
//                        };
//                    }
//                })
        );
        QueryResult<TestEntity> tes = lucener.query("content", "不错", 10, sort);
        System.out.println(tes.getTotal());
        System.out.println(tes.getResult().size());
        System.out.println(tes.getResult());
    }

    @Test
    public void queryByVosListInt() throws Exception {
        for (int i = 0; i < 5; i++) {
            //List<TestEntity> tes = representation.query("testVo.listInt", "8", 10);
            long start = System.currentTimeMillis();
            QueryResult<TestEntity> tes = lucener.query("vos.listInt", 8, 10, null);
            long end = System.currentTimeMillis();
            System.out.println(tes);
            System.out.println(tes.getTotal());
            System.out.println(tes.getResult().size());
            System.out.println(end - start);
        }
    }

    @Test
    public void tokens() throws IOException {
        String text = "A股一倒中韩渔警冲突调查：韩警平均每天扣1艘中国渔船";
        lucener.tokens(text).forEach(System.out::println);
    }
}
