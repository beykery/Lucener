package org.beykery.lucener;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.NumericUtils;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;


/**
 * representation for lucene index of type T
 */
public class Lucener<T extends DocSerializable<T>> {
    /**
     * all representation
     */
    private static final Map<Class<? extends DocSerializable>, Lucener> knownIndex;
    /**
     * the directory
     */
    private final Directory directory;
    /**
     * analyzer for faceting
     */
    private final Analyzer analyzer;
    /**
     * writer
     */
    private final IndexWriter indexWriter;
    /**
     * manager for refresh and search
     */
    private final SearcherManager searcherManager;
    /**
     * index file path
     */
    private final String indexPath;
    /**
     * store serialized object data
     */
    private final boolean stored;
    /**
     * id field
     */
    private FieldDesc docId;
    /**
     * all fields index
     */
    private final List<FieldDesc> fields;
    /**
     * sub field as a.b.c
     */
    private final List<List<FieldDesc>> subFields;
    /**
     * all fields that contain fields and sub-fields
     */
    private final Map<String, List<FieldDesc>> allFields;
    /**
     * type
     */
    private final Class<? extends DocSerializable> type;
    /**
     * root path for index directory
     */
    private static final String ROOT = "./.indices/";

    /**
     * init
     */
    static {
        knownIndex = new HashMap<>();
    }

    /**
     * representation for class
     *
     * @param entityClass
     * @return
     */
    public static <T> Lucener forClass(Class<? extends DocSerializable<T>> entityClass) throws Exception {
        if (knownIndex.containsKey(entityClass)) {
            return knownIndex.get(entityClass);
        }
        if (DocSerializable.class.isAssignableFrom(entityClass)) {
            Lucener entityRepresentation = new Lucener(entityClass);
            knownIndex.put(entityClass, entityRepresentation);
            return entityRepresentation;
        } else {
            error(entityClass, "not implement DocSerializable interface");
        }
        return null;
    }

    /**
     * init class for representation
     *
     * @param entityClass
     * @throws IOException
     */
    private <T> Lucener(Class<? extends DocSerializable<T>> entityClass) throws Exception {
        type = entityClass;
        verifyIndexAnnotation(entityClass);
        Index ian = entityClass.getAnnotation(Index.class);
        String dir = ian.value();
        if (dir.isEmpty()) {
            dir = ROOT + replaceAll(entityClass.getName(), '.', '/') + "/";
        }
        Path path = Paths.get(dir);
        File file = path.toFile();
        if (!file.exists()) {
            boolean ret = file.mkdirs();
            if (!ret) {
                error(entityClass, "can not create file : " + dir);
            }
        }
        indexPath = file.getAbsolutePath();
        stored = ian.stored();
        directory = FSDirectory.open(path);
        analyzer = ian.analyzer() == null ? new IKAnalyzer(true) : (ian.analyzer() == IKAnalyzer.class ? new IKAnalyzer(true) : ian.analyzer().newInstance());
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
        indexWriter = new IndexWriter(directory, indexWriterConfig);
        searcherManager = new SearcherManager(indexWriter, false, false, new SearcherFactory());
        fields = new ArrayList<>();
        subFields = new ArrayList<>();
        List<Field> allFiled = new ArrayList<>();
        allField(entityClass, allFiled);
        // annotated field
        for (Field f : allFiled) {
            // doc id
            if (docId == null && f.isAnnotationPresent(DocId.class)) {
                boolean fit = fitDocId(f);
                if (fit) {
                    DocId di = f.getAnnotation(DocId.class);
                    f.setAccessible(true);
                    docId = new FieldDesc(f, false, String.class, false, false, false, true);
                } else {
                    error(entityClass, "DocId not fit");
                }
            } else if (f.isAnnotationPresent(IntField.class)) {
                boolean fit = fitIntField(f);
                if (fit) {
                    IntField an = f.getAnnotation(IntField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Integer.class, an.stored(), an.sort(), false, true));
                } else {
                    error(entityClass, "IntField not fit");
                }
            } else if (f.isAnnotationPresent(LongField.class)) {
                boolean fit = fitLongField(f);
                if (fit) {
                    LongField an = f.getAnnotation(LongField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Long.class, an.stored(), an.sort(), false, true));
                } else {
                    error(entityClass, "LongField not fit");
                }
            } else if (f.isAnnotationPresent(FloatField.class)) {
                boolean fit = fitFloatField(f);
                if (fit) {
                    FloatField an = f.getAnnotation(FloatField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Float.class, an.stored(), an.sort(), false, true));
                } else {
                    error(entityClass, "FloatField not fit");
                }
            } else if (f.isAnnotationPresent(DoubleField.class)) {
                boolean fit = fitDoubleField(f);
                if (fit) {
                    DoubleField an = f.getAnnotation(DoubleField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Double.class, an.stored(), an.sort(), false, true));
                } else {
                    error(entityClass, "DoubleField not fit");
                }
            } else if (f.isAnnotationPresent(StringField.class)) {
                boolean fit = fitStringField(f);
                if (fit) {
                    StringField an = f.getAnnotation(StringField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), String.class, an.stored(), false, false, true));
                } else {
                    error(entityClass, "StringField not fit");
                }
            } else if (f.isAnnotationPresent(TextField.class)) {
                boolean fit = fitTextField(f);
                if (fit) {
                    TextField an = f.getAnnotation(TextField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), String.class, an.stored(), false, true, true));
                } else {
                    error(entityClass, "TextField not fit");
                }
            }
            // other field as a.b.c
            else if (!isPrimitive(f)) {
                List<List<FieldDesc>> subFields = new ArrayList<>();
                subFields(new ArrayList<>(), f, subFields);
                this.subFields.addAll(subFields);
            }
        }
        // id exist ?
        if (docId == null) {
            error(entityClass, "DocId not exist");
        }
        // all fields
        allFields = new HashMap<>();
        fields.forEach(item -> allFields.put(item.getField().getName(), Collections.singletonList(item)));
        subFields.stream().filter(item -> !item.isEmpty()).forEach(item -> {
            String name = item.stream().map(f -> f.getField().getName()).collect(Collectors.joining("."));
            allFields.put(name, item);
        });
    }

    /**
     * find fields such as a.b.c
     *
     * @param context
     * @param f
     * @return
     */
    public static void subFields(List<FieldDesc> context, Field f, List<List<FieldDesc>> subFields) {
        f.setAccessible(true);
        boolean collection = isCollection(f);
        Class<?> c = getInnerType(f);
        context.add(new FieldDesc(f, collection, c, false, false, false, false));
        List<Field> all = new ArrayList<>();
        allField(c, all);
        for (Field item : all) {
            if (item.isAnnotationPresent(IntField.class)) {
                boolean fit = fitIntField(item);
                if (fit) {
                    IntField an = item.getAnnotation(IntField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Integer.class, an.stored(), an.sort(), false, true));
                    subFields.add(ret);
                } else {
                    error(c, "IntField not fit");
                }
            } else if (item.isAnnotationPresent(LongField.class)) {
                boolean fit = fitLongField(item);
                if (fit) {
                    LongField an = item.getAnnotation(LongField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Long.class, an.stored(), an.sort(), false, true));
                    subFields.add(ret);
                } else {
                    error(c, "LongField not fit");
                }
            } else if (item.isAnnotationPresent(FloatField.class)) {
                boolean fit = fitFloatField(item);
                if (fit) {
                    FloatField an = item.getAnnotation(FloatField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Float.class, an.stored(), an.sort(), false, true));
                    subFields.add(ret);
                } else {
                    error(c, "FloatField not fit");
                }
            } else if (item.isAnnotationPresent(DoubleField.class)) {
                boolean fit = fitDoubleField(item);
                if (fit) {
                    DoubleField an = item.getAnnotation(DoubleField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Double.class, an.stored(), an.sort(), false, true));
                    subFields.add(ret);
                } else {
                    error(c, "DoubleField not fit");
                }
            } else if (item.isAnnotationPresent(StringField.class)) {
                boolean fit = fitStringField(item);
                if (fit) {
                    StringField an = item.getAnnotation(StringField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), String.class, an.stored(), false, false, true));
                    subFields.add(ret);
                } else {
                    error(c, "StringField not fit");
                }
            } else if (item.isAnnotationPresent(TextField.class)) {
                boolean fit = fitTextField(item);
                if (fit) {
                    TextField an = item.getAnnotation(TextField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), String.class, an.stored(), false, true, true));
                    subFields.add(ret);
                } else {
                    error(c, "TextField not fit");
                }
            }
            // other field as a.b.c
            else if (!isPrimitive(item)) {
                subFields(context, item, subFields);
            }
        }
    }

    /**
     * primitive or primitive element of collection
     *
     * @param f field
     * @return
     */
    public static boolean isPrimitive(Field f) {
        Class<?> rt = getInnerType(f);
        // not just rt.isPrimitive()
        return Objects.equals(rt, int.class)
                || Objects.equals(rt, long.class)
                || Objects.equals(rt, float.class)
                || Objects.equals(rt, double.class)
                || Objects.equals(rt, Integer.class)
                || Objects.equals(rt, Long.class)
                || Objects.equals(rt, Float.class)
                || Objects.equals(rt, Double.class)
                || Objects.equals(rt, String.class)
                || rt.isPrimitive();
    }

    /**
     * inner type
     *
     * @param f
     * @return
     */
    public static Class<?> getInnerType(Field f) {
        Class<?> rt;
        if (Collection.class.isAssignableFrom(f.getType())) {
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            rt = (Class<?>) pt.getActualTypeArguments()[0];
        } else {
            rt = f.getType();
        }
        return rt;
    }

    /**
     * with DocId field is ok?
     *
     * @param f
     * @return
     */
    private static boolean fitDocId(Field f) {
        return f.getType() == String.class;
    }

    /**
     * with int field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitIntField(Field f) {
        if (Collection.class.isAssignableFrom(f.getType())) {
            if (!(f.getType() == Set.class || f.getType() == List.class || f.getType() == Collection.class)) {
                return false;
            }
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            Class<?> rt = (Class<?>) pt.getActualTypeArguments()[0];
            return rt == int.class || rt == Integer.class;
        } else {
            return f.getType() == int.class || f.getType() == Integer.class;
        }
    }

    /**
     * with long field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitLongField(Field f) {
        if (Collection.class.isAssignableFrom(f.getType())) {
            if (!(f.getType() == Set.class || f.getType() == List.class || f.getType() == Collection.class)) {
                return false;
            }
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            Class<?> rt = (Class<?>) pt.getActualTypeArguments()[0];
            return rt == long.class || rt == Long.class;
        } else {
            return f.getType() == long.class || f.getType() == Long.class;
        }
    }

    /**
     * with float field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitFloatField(Field f) {
        if (Collection.class.isAssignableFrom(f.getType())) {
            if (!(f.getType() == Set.class || f.getType() == List.class || f.getType() == Collection.class)) {
                return false;
            }
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            Class<?> rt = (Class<?>) pt.getActualTypeArguments()[0];
            return rt == float.class || rt == Float.class;
        } else {
            return f.getType() == float.class || f.getType() == Float.class;
        }
    }

    /**
     * with double field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitDoubleField(Field f) {
        if (Collection.class.isAssignableFrom(f.getType())) {
            if (!(f.getType() == Set.class || f.getType() == List.class || f.getType() == Collection.class)) {
                return false;
            }
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            Class<?> rt = (Class<?>) pt.getActualTypeArguments()[0];
            return rt == double.class || rt == Double.class;
        } else {
            return f.getType() == double.class || f.getType() == Double.class;
        }
    }

    /**
     * with string field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitStringField(Field f) {
        if (Collection.class.isAssignableFrom(f.getType())) {
            if (!(f.getType() == Set.class || f.getType() == List.class || f.getType() == Collection.class)) {
                return false;
            }
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            Class<?> rt = (Class<?>) pt.getActualTypeArguments()[0];
            return rt == String.class;
        } else {
            return f.getType() == String.class;
        }
    }

    /**
     * with text field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitTextField(Field f) {
        return fitStringField(f);
    }

    /**
     * collection ?
     *
     * @param f
     * @return
     */
    private static boolean isCollection(Field f) {
        return isCollection(f.getType());
    }

    /**
     * collection ?
     *
     * @param c
     * @return
     */
    private static boolean isCollection(Class c) {
        return Collection.class.isAssignableFrom(c);
    }

    /**
     * replace char
     *
     * @param s
     * @param c
     * @param r
     * @return
     */
    public static String replaceAll(String s, char c, char r) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char temp = s.charAt(i);
            if (temp == c) {
                temp = r;
            }
            sb.append(temp);
        }
        return sb.toString();
    }

    /**
     * all field
     *
     * @param claz
     */
    public static void allField(Class<? extends Object> claz, List<Field> all) {
        Field[] fs = claz.getDeclaredFields();
        all.addAll(Arrays.asList(fs));
        // the super fields
        Class sclaz = claz.getSuperclass();
        if (sclaz != null) {
            allField(sclaz, all);
        }
    }


    public static void verifyIndexAnnotation(Class<? extends Object> entityClass) {
        if (entityClass.getAnnotation(Index.class) == null) {
            error(entityClass, "missing @Index annotation");
        }
    }

    public static void error(Class<? extends Object> entityClass, String msg) {
        throw new RuntimeException(entityClass.getCanonicalName() + ": " + msg);
    }

    /**
     * update object by term
     * <p>
     * first delete by term and then add documents
     *
     * @param obs
     */
    public void index(T... obs) throws Exception {
        if (obs != null && obs.length > 0) {
            for (T ob : obs) {
                if (ob != null && ob.getClass() == type) {
                    // doc id
                    String did = value(docId, ob).toString();
                    if (did == null || did.isEmpty()) {
                        throw new NullPointerException("doc id is null ");
                    }
                    Document doc = new Document();
                    for (List<FieldDesc> fs : allFields.values()) {
                        FieldDesc f = fs.get(fs.size() - 1);  // the end
                        Object v = value(fs, ob);
                        if (v != null) {
                            Class inner = f.getInner();
                            String name = name(fs);
                            // collection
                            if (f.isCollection()) {
                                if (inner == int.class || inner == Integer.class) {
                                    Collection<Integer> c = (Collection<Integer>) v;
                                    c.forEach(i -> {
                                        doc.add(new IntPoint(name, i));
                                        if (f.isStored()) {
                                            doc.add(new StoredField(name, i));
                                        }
                                        if (f.isSort()) {
                                            doc.add(new SortedNumericDocValuesField(name, i));
                                        }
                                    });
                                } else if (inner == long.class || inner == Long.class) {
                                    Collection<Long> c = (Collection<Long>) v;
                                    c.forEach(i -> {
                                        doc.add(new LongPoint(name, i));
                                        if (f.isStored()) {
                                            doc.add(new StoredField(name, i));
                                        }
                                        if (f.isSort()) {
                                            doc.add(new SortedNumericDocValuesField(name, i));
                                        }
                                    });
                                } else if (inner == float.class || inner == Float.class) {
                                    Collection<Float> c = (Collection<Float>) v;
                                    c.forEach(i -> {
                                        doc.add(new FloatPoint(name, i));
                                        if (f.isStored()) {
                                            doc.add(new StoredField(name, i));
                                        }
                                        if (f.isSort()) {
                                            doc.add(new SortedNumericDocValuesField(name, NumericUtils.floatToSortableInt(i)));
                                        }
                                    });
                                } else if (inner == double.class || inner == Double.class) {
                                    Collection<Double> c = (Collection<Double>) v;
                                    c.forEach(i -> {
                                        doc.add(new DoublePoint(name, i));
                                        if (f.isStored()) {
                                            doc.add(new StoredField(name, i));
                                        }
                                        if (f.isSort()) {
                                            doc.add(new SortedNumericDocValuesField(name, NumericUtils.floatToSortableInt(i.floatValue())));
                                        }
                                    });
                                } else if (inner == String.class) {
                                    Collection<String> c = (Collection<String>) v;
                                    c.forEach(i -> {
                                        if (f.isTokenized()) {
                                            doc.add(new org.apache.lucene.document.TextField(name, i, f.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
                                        } else {
                                            doc.add(new org.apache.lucene.document.StringField(name, i, f.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
                                        }
                                    });
                                }
                            }
                            // not collection
                            else {
                                if (inner == int.class || inner == Integer.class) {
                                    Integer i = (Integer) v;
                                    doc.add(new IntPoint(name, i));
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, i));
                                    }
                                    if (f.isSort()) {
                                        doc.add(new SortedNumericDocValuesField(name, i));
                                    }
                                } else if (inner == long.class || inner == Long.class) {
                                    Long i = (Long) v;
                                    doc.add(new LongPoint(name, i));
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, i));
                                    }
                                    if (f.isSort()) {
                                        doc.add(new SortedNumericDocValuesField(name, i));
                                    }
                                } else if (inner == float.class || inner == Float.class) {
                                    Float i = (Float) v;
                                    doc.add(new FloatPoint(name, i));
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, i));
                                    }
                                    if (f.isSort()) {
                                        int sortedNumber = NumericUtils.floatToSortableInt(i);
                                        doc.add(new SortedNumericDocValuesField(name, sortedNumber));
                                    }
                                } else if (inner == double.class || inner == Double.class) {
                                    Double i = (Double) v;
                                    doc.add(new DoublePoint(name, i));
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, i));
                                    }
                                    if (f.isSort()) {
                                        int sortedNumber = NumericUtils.floatToSortableInt(i.floatValue());
                                        doc.add(new SortedNumericDocValuesField(name, sortedNumber));
                                    }
                                } else if (inner == String.class) {
                                    String i = (String) v;
                                    if (f.isTokenized()) {
                                        doc.add(new org.apache.lucene.document.TextField(name, i, f.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
                                    } else {
                                        doc.add(new org.apache.lucene.document.StringField(name, i, f.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
                                    }
                                }
                            }
                        }
                    }
                    // _doc section just store
                    if (stored) {
                        doc.add(new StoredField("_doc", ob.serialize()));
                    }
                    doc.add(new org.apache.lucene.document.StringField(docId.getField().getName(), did, org.apache.lucene.document.Field.Store.NO));
                    Term term = new Term(docId.getField().getName(), did);
                    indexWriter.updateDocument(term, doc);
                } else {
                    if (ob == null) {
                        throw new NullPointerException("object to index is null");
                    } else {
                        error(ob.getClass(), "class not fit");
                    }
                }
            }
        }
    }

    /**
     * name for the field
     *
     * @param fs
     * @return
     */
    private static String name(List<FieldDesc> fs) {
        String name = fs.stream().map(f -> f.getField().getName()).collect(Collectors.joining("."));
        return name;
    }

    /**
     * @param fd
     * @param root
     * @return
     * @throws IllegalAccessException
     */
    private static Object value(FieldDesc fd, Object root) throws IllegalAccessException {
        Object value = fd.getField().get(root);
        return value;
    }

    /**
     * get value
     *
     * @param fs
     * @param root
     * @return
     */
    private static Object value(List<FieldDesc> fs, Object root) throws IllegalAccessException {
        FieldDesc fd = fs.get(0);
        Object value = fd.getField().get(root);
        if (isEmpty(value)) {
            return null;
        } else {
            if (fd.isEnd()) {
                return value;
            }
            // not end
            else {
                // collection
                if (fd.isCollection()) {
                    Collection ret = new HashSet();
                    Collection c = (Collection) value;
                    for (Object item : c) {
                        List<FieldDesc> fds = fs.subList(1, fs.size());
                        Object temp = value(fds, item);
                        if (isCollection(temp.getClass())) {
                            Collection tc = (Collection) temp;
                            ret.addAll(tc);
                        } else {
                            ret.add(temp);
                        }
                    }
                    return ret;
                }
                // not collection
                else {
                    return value(fs.subList(1, fs.size()), value);
                }
            }
        }
    }

    /**
     * is empty
     *
     * @param v
     * @return
     */
    public static boolean isEmpty(Object v) {
        if (v == null) {
            return true;
        } else if (isCollection(v.getClass())) {
            Collection c = (Collection) v;
            return c.isEmpty();
        }
        return false;
    }

    /**
     * commit
     *
     * @return
     * @throws IOException
     */
    public long commit() throws IOException {
        return indexWriter.commit();
    }

    /**
     * close writer and directory
     *
     * @throws IOException
     */
    public void close() throws IOException {
        indexWriter.close();
        directory.close();
    }

    /**
     * get by id
     *
     * @param id
     * @return
     */
    public T get(String id) throws Exception {
        IndexSearcher indexSearcher = searcherManager.acquire();
        BooleanQuery.Builder builder = new BooleanQuery.Builder();
        FieldDesc fd = docId;
        builder.add(new TermQuery(new Term(fd.getField().getName(), id)), BooleanClause.Occur.MUST);
        TopDocs topDocs = indexSearcher.searchAfter(null, builder.build(), 1);
        ScoreDoc[] hits = topDocs.scoreDocs;
        if (hits != null && hits.length > 0) {
            T dsi = (T) this.type.newInstance();  // ? not very ok . . .
            for (ScoreDoc sc : hits) {
                int did = sc.doc;
                Document doc = indexSearcher.doc(did);
                String docString = doc.get("_doc");
                if (docString != null) {
                    T d = dsi.deserialize(docString);
                    d.doc = did;
                    d.score = sc.score;
                    d.shardIndex = sc.shardIndex;
                    return d;
                }
            }
        }
        return null;
    }

    /**
     * query
     *
     * @param n
     * @param sort
     * @return
     * @throws Exception
     */
    public QueryResult<T> query(Query query, int n, Sort sort) throws Exception {
        return queryAfter(null, query, n, sort);
    }

    /**
     * query by field value
     *
     * @param field simpy field or complex field as a.b.c
     * @param v     value for query
     * @param n     top n
     * @return
     */
    public QueryResult<T> query(String field, Object v, int n, Sort sort) throws Exception {
        return queryAfter((ScoreDoc) null, field, v, n, sort);
    }

    /**
     * for pageable query
     *
     * @param after
     * @param field
     * @param v
     * @param n
     * @return
     * @throws Exception
     */
    public QueryResult<T> queryAfter(T after, String field, Object v, int n, Sort sort) throws Exception {
        return queryAfter(after != null ? new ScoreDoc(after.doc, after.score, after.shardIndex) : null, field, v, n, sort);
    }

    /**
     * for pageable query
     *
     * @param after
     * @param field
     * @param v
     * @param n
     * @return
     * @throws Exception
     */
    public QueryResult<T> queryAfter(ScoreDoc after, String field, Object v, int n, Sort sort) throws Exception {
        QueryResult<T> queryResult = null;
        List<FieldDesc> list = allFields.get(field);
        if (!list.isEmpty()) {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            FieldDesc fd = list.get(list.size() - 1);
            Class type = fd.getInner();
            if (type == int.class || type == Integer.class) {
                builder.add(IntPoint.newExactQuery(field, (Integer) v), BooleanClause.Occur.MUST);
            } else if (type == long.class || type == Long.class) {
                builder.add(LongPoint.newExactQuery(field, (Long) v), BooleanClause.Occur.MUST);
            } else if (type == float.class || type == Float.class) {
                builder.add(FloatPoint.newExactQuery(field, (Float) v), BooleanClause.Occur.MUST);
            } else if (type == double.class || type == Double.class) {
                builder.add(DoublePoint.newExactQuery(field, (Double) v), BooleanClause.Occur.MUST);
            } else if (type == String.class) {
                builder.add(new TermQuery(new Term(field, (String) v)), BooleanClause.Occur.MUST);
            }
            if (sort != null && after != null) {
                after = (after instanceof FieldDoc) ? after : new FieldDoc(after.doc, after.score);
            }
            Query query = builder.build();
            queryResult = queryAfter(after, query, n, sort);
        }
        return queryResult == null ? QueryResult.empty() : queryResult;
    }

    /**
     * query after
     *
     * @param after
     * @param query
     * @param n
     * @param sort
     * @return
     * @throws Exception
     */
    public QueryResult<T> queryAfter(ScoreDoc after, Query query, int n, Sort sort) throws Exception {
        IndexSearcher indexSearcher = null;
        long total;
        List<T> ret = new ArrayList<>(n);
        try {
            indexSearcher = searcherManager.acquire();
            TopDocs topDocs = sort == null ? indexSearcher.searchAfter(after, query, n) : indexSearcher.searchAfter(after, query, n, sort);
            total = topDocs.totalHits.value;
            ScoreDoc[] hits = topDocs.scoreDocs;
            if (hits != null && hits.length > 0) {
                T dsi = (T) this.type.newInstance();  // ? not very ok . . .
                for (ScoreDoc sc : hits) {
                    int did = sc.doc;
                    Document doc = indexSearcher.doc(did);
                    String docString = doc.get("_doc");
                    if (docString != null) {
                        T d = dsi.deserialize(docString);
                        d.doc = did;
                        d.score = sc.score;
                        d.shardIndex = sc.shardIndex;
                        ret.add(d);
                    }
                }
            }
        } catch (Exception ex) {
            throw ex;
        } finally {
            if (indexSearcher != null) {
                searcherManager.release(indexSearcher);
            }
        }
        return new QueryResult<>(total, ret);
    }

    /**
     * doc stats
     *
     * @return
     */
    public IndexWriter.DocStats docStats() {
        return indexWriter.getDocStats();
    }

    /**
     * Command-line interface to check and exorcise corrupt segments from an index.
     * Run it like this:
     * java -ea:org.apache.lucene... org.apache.lucene.index.CheckIndex pathToIndex [-exorcise] [-verbose] [-segment X] [-segment Y]
     * <p>
     * -exorcise: actually write a new segments_N file, removing any problematic segments. *LOSES DATA*
     * -segment X: only check the specified segment(s). This can be specified multiple times, to check more than one segment, eg -segment _2 -segment _a. You can't use this with the -exorcise option.
     * WARNING: -exorcise should only be used on an emergency basis as it will cause documents (perhaps many) to be permanently removed from the index. Always make a backup copy of your index before running this! Do not run this tool on an index that is actively being written to. You have been warned!
     * Run without -exorcise, this tool will open the index, report version information and report any exceptions it hits and what action it would take if -exorcise were specified. With -exorcise, this tool will remove any segments that have issues and write a new segments_N file. This means all documents contained in the affected segments will be removed.
     * This tool exits with exit code 1 if the index cannot be opened or has any corruption, else 0.
     *
     * @param args
     * @throws IOException
     * @throws InterruptedException
     */
    public static void checkIndex(String... args) throws IOException, InterruptedException {
        CheckIndex.main(args);
    }

    /**
     * force merge deletes
     *
     * @param doWait
     * @throws IOException
     */
    public void forceMergeDeletes(boolean doWait) throws IOException {
        indexWriter.forceMergeDeletes(doWait);
    }

    /**
     * force merge index to max segments
     *
     * @param maxNumSegments
     * @param doWait
     * @throws IOException
     */
    public void forceMerge(int maxNumSegments, boolean doWait) throws IOException {
        indexWriter.forceMerge(maxNumSegments, doWait);
    }

    /**
     * refresh
     * <p>
     * for NRT(near real time) search , this method should be called periodically .
     *
     * @return
     */
    public boolean maybeRefresh() throws IOException {
        return searcherManager.maybeRefresh();
    }

    /**
     * search manager
     *
     * @return
     */
    public SearcherManager searcherManager() {
        return searcherManager;
    }

    /**
     * index writer
     *
     * @return
     */
    public IndexWriter indexWriter() {
        return indexWriter;
    }

    /**
     * directory
     *
     * @return
     */
    public Directory directory() {
        return directory;
    }

    /**
     * analyzer for token
     *
     * @return
     */
    public Analyzer analyzer() {
        return analyzer;
    }

    /**
     * path for index
     *
     * @return
     */
    public String indexPath() {
        return indexPath;
    }
}
