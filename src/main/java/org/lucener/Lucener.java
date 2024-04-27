package org.lucener;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.document.*;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.sandbox.document.BigIntegerPoint;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.NumericUtils;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.math.BigInteger;
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
    private static final Map<Class<? extends DocSerializable>, Lucener<? extends DocSerializable>> knownIndex;
    /**
     * the directory
     */
    private final Directory directory;
    /**
     * analyzer for faceting
     */
    private final Analyzer defaultAnalyzer;
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
    private static final String ROOT_DEFAULT = "./.indices/";

    /*
      init
     */
    static {
        knownIndex = new HashMap<>();
    }

    /**
     * lucener for class
     *
     * @param entityClass
     * @param <U>
     * @return
     * @throws Exception
     */
    public synchronized static <U> Lucener forClass(Class<? extends DocSerializable<U>> entityClass) throws Exception {
        return forClass(entityClass, null);
    }

    /**
     * lucener for class
     *
     * @param entityClass
     * @return
     */
    public synchronized static <V> Lucener forClass(Class<? extends DocSerializable<V>> entityClass, String root) throws Exception {
        if (knownIndex.containsKey(entityClass)) {
            return knownIndex.get(entityClass);
        }
        if (DocSerializable.class.isAssignableFrom(entityClass)) {
            Lucener entityRepresentation = new Lucener<>(entityClass, root);
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
     * @param entityClass entity class
     * @param root        root path
     * @throws IOException
     */
    private <U> Lucener(Class<? extends DocSerializable<U>> entityClass, String root) throws Exception {
        type = entityClass;
        verifyIndexAnnotation(entityClass);
        Index ian = entityClass.getAnnotation(Index.class);
        /**
         * persist to disk or not
         */
        boolean persistence = ian.persistence();
        root = root == null ? ian.value() : root;
        if (root.isEmpty()) {
            root = ROOT_DEFAULT;
        }
        String dir = (root.endsWith("/") ? root : root + "/") + ian.prefix() + replaceAll(entityClass.getName(), '.', '/') + "/";
        Path path = Paths.get(dir);
        File file = path.toFile();
        indexPath = file.getAbsolutePath();
        if (persistence && !file.exists()) {
            boolean ret = file.mkdirs();
            if (!ret) {
                error(entityClass, "can not create file : " + dir);
            }
        }
        stored = ian.stored();
        directory = persistence ? new MMapDirectory(path) : new ByteBuffersDirectory();
        defaultAnalyzer = ian.analyzer() == null ? new IKAnalyzer(ian.ikSmart()) : (ian.analyzer() == IKAnalyzer.class ? new IKAnalyzer(ian.ikSmart()) : ian.analyzer().getDeclaredConstructor().newInstance());
        /**
         * all fields index
         */
        List<FieldDesc> fields = new ArrayList<>();
        /**
         * sub-field as a.b.c
         */
        List<List<FieldDesc>> subFieldsAll = new ArrayList<>();
        List<Field> allFiled = new ArrayList<>();
        allField(entityClass, allFiled);
        // annotated field
        for (Field f : allFiled) {
            // size field
            if (isCollection(f)) {
                if (f.isAnnotationPresent(SizeField.class)) {
                    boolean fit = fitSizeField(f);
                    if (fit) {
                        SizeField an = f.getAnnotation(SizeField.class);
                        f.setAccessible(true);
                        fields.add(new FieldDesc(f, true, Integer.class, an.stored(), an.sort(), false, true, an.index(), true));
                    } else {
                        error(entityClass, "SizeField not fit");
                    }
                }
            }
            // doc id
            if (docId == null && f.isAnnotationPresent(DocId.class)) {
                boolean fit = fitDocId(f);
                if (fit) {
                    DocId di = f.getAnnotation(DocId.class);
                    f.setAccessible(true);
                    docId = new FieldDesc(f, false, String.class, di.stored(), false, false, true, true);
                } else {
                    error(entityClass, "DocId not fit");
                }
            } else if (f.isAnnotationPresent(IntField.class)) {
                boolean fit = fitIntField(f);
                if (fit) {
                    IntField an = f.getAnnotation(IntField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Integer.class, an.stored(), an.sort(), false, true, an.index()));
                } else {
                    error(entityClass, "IntField not fit");
                }
            } else if (f.isAnnotationPresent(LongField.class)) {
                boolean fit = fitLongField(f);
                if (fit) {
                    LongField an = f.getAnnotation(LongField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Long.class, an.stored(), an.sort(), false, true, an.index()));
                } else {
                    error(entityClass, "LongField not fit");
                }
            } else if (f.isAnnotationPresent(BigIntegerField.class)) {
                boolean fit = fitBigIntegerField(f);
                if (fit) {
                    BigIntegerField an = f.getAnnotation(BigIntegerField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), BigInteger.class, an.stored(), false, false, true, an.index()));
                } else {
                    error(entityClass, "BigIntegerField not fit");
                }
            } else if (f.isAnnotationPresent(FloatField.class)) {
                boolean fit = fitFloatField(f);
                if (fit) {
                    FloatField an = f.getAnnotation(FloatField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Float.class, an.stored(), an.sort(), false, true, an.index()));
                } else {
                    error(entityClass, "FloatField not fit");
                }
            } else if (f.isAnnotationPresent(DoubleField.class)) {
                boolean fit = fitDoubleField(f);
                if (fit) {
                    DoubleField an = f.getAnnotation(DoubleField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Double.class, an.stored(), an.sort(), false, true, an.index()));
                } else {
                    error(entityClass, "DoubleField not fit");
                }
            } else if (f.isAnnotationPresent(BooleanField.class)) {
                boolean fit = fitBooleanField(f);
                if (fit) {
                    BooleanField an = f.getAnnotation(BooleanField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), Boolean.class, an.stored(), false, false, true, true));
                } else {
                    error(entityClass, "BooleanField not fit");
                }
            } else if (f.isAnnotationPresent(StringField.class)) {
                boolean fit = fitStringField(f);
                if (fit) {
                    StringField an = f.getAnnotation(StringField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), String.class, an.stored(), false, false, true, true));
                } else {
                    error(entityClass, "StringField not fit");
                }
            } else if (f.isAnnotationPresent(TextField.class)) {
                boolean fit = fitTextField(f);
                if (fit) {
                    TextField an = f.getAnnotation(TextField.class);
                    f.setAccessible(true);
                    fields.add(new FieldDesc(f, isCollection(f), String.class, an.stored(), false, true, true, true));
                } else {
                    error(entityClass, "TextField not fit");
                }
            }
            // other field as a.b.c
            else if (!isPrimitive(f)) {
                List<List<FieldDesc>> subFields = new ArrayList<>();
                subFields(new ArrayList<>(), f, subFields);
                subFieldsAll.addAll(subFields);
            }
        }
        // id exist ?
        if (docId == null) {
            error(entityClass, "DocId not exist");
        }
        // all fields
        Map<String, Analyzer> fieldAnalyzers = new HashMap<>();
        allFields = new HashMap<>();
        // fields
        for (FieldDesc field : fields) {
            String name = field.isJustSize() ? field.getField().getName() + ".size" : field.getField().getName();
            allFields.put(name, Collections.singletonList(field));
            if (field.isTokenized()) {
                TextField tf = field.getField().getAnnotation(TextField.class);
                Analyzer fieldAnalyzer = tf.analyzer() == null ? new IKAnalyzer(tf.ikSmart()) : (tf.analyzer() == IKAnalyzer.class ? new IKAnalyzer(tf.ikSmart()) : tf.analyzer().getDeclaredConstructor().newInstance());
                fieldAnalyzers.put(name, fieldAnalyzer);
            }
        }
        // sub-fields
        for (List<FieldDesc> item : subFieldsAll) {
            if (!item.isEmpty()) {
                String name = item.stream().map(f -> f.getField().getName()).collect(Collectors.joining("."));
                FieldDesc field = item.get(item.size() - 1);
                if (field.isJustSize()) {
                    name = name + ".size";
                }
                allFields.put(name, item);
                if (field.isTokenized()) {
                    TextField tf = field.getField().getAnnotation(TextField.class);
                    Analyzer fieldAnalyzer = tf.analyzer() == null ? new IKAnalyzer(tf.ikSmart()) : (tf.analyzer() == IKAnalyzer.class ? new IKAnalyzer(tf.ikSmart()) : tf.analyzer().getDeclaredConstructor().newInstance());
                    fieldAnalyzers.put(name, fieldAnalyzer);
                }
            }
        }
        // analyzer
        PerFieldAnalyzerWrapper wrapper = new PerFieldAnalyzerWrapper(defaultAnalyzer, fieldAnalyzers);
        IndexWriterConfig indexWriterConfig = new IndexWriterConfig(wrapper);
        indexWriter = new IndexWriter(directory, indexWriterConfig);
        searcherManager = new SearcherManager(indexWriter, true, true, new SearcherFactory());
    }

    /**
     * find fields such as a.b.c
     *
     * @param context
     * @param f
     * @return
     */
    public static void subFields(List<FieldDesc> context, Field f, List<List<FieldDesc>> result) {
        f.setAccessible(true);
        boolean collection = isCollection(f);
        Class<?> c = getInnerType(f);
        context.add(new FieldDesc(f, collection, c, false, false, false, false, false));
        List<Field> all = new ArrayList<>();
        allField(c, all);
        for (Field item : all) {
            // size field
            if (isCollection(item)) {
                if (item.isAnnotationPresent(SizeField.class)) {
                    boolean fit = fitSizeField(item);
                    if (fit) {
                        SizeField an = item.getAnnotation(SizeField.class);
                        item.setAccessible(true);
                        List<FieldDesc> ret = new ArrayList<>(context);
                        ret.add(new FieldDesc(item, true, Integer.class, an.stored(), an.sort(), false, true, an.index(), true));
                        result.add(ret);
                    } else {
                        error(c, "SizeField not fit");
                    }
                }
            }
            if (item.isAnnotationPresent(IntField.class)) {
                boolean fit = fitIntField(item);
                if (fit) {
                    IntField an = item.getAnnotation(IntField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Integer.class, an.stored(), an.sort(), false, true, an.index()));
                    result.add(ret);
                } else {
                    error(c, "IntField not fit");
                }
            } else if (item.isAnnotationPresent(LongField.class)) {
                boolean fit = fitLongField(item);
                if (fit) {
                    LongField an = item.getAnnotation(LongField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Long.class, an.stored(), an.sort(), false, true, an.index()));
                    result.add(ret);
                } else {
                    error(c, "LongField not fit");
                }
            } else if (item.isAnnotationPresent(BigIntegerField.class)) {
                boolean fit = fitBigIntegerField(item);
                if (fit) {
                    BigIntegerField an = item.getAnnotation(BigIntegerField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), BigInteger.class, an.stored(), false, false, true, an.index()));
                    result.add(ret);
                } else {
                    error(c, "BigIntegerField not fit");
                }
            } else if (item.isAnnotationPresent(FloatField.class)) {
                boolean fit = fitFloatField(item);
                if (fit) {
                    FloatField an = item.getAnnotation(FloatField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Float.class, an.stored(), an.sort(), false, true, an.index()));
                    result.add(ret);
                } else {
                    error(c, "FloatField not fit");
                }
            } else if (item.isAnnotationPresent(DoubleField.class)) {
                boolean fit = fitDoubleField(item);
                if (fit) {
                    DoubleField an = item.getAnnotation(DoubleField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Double.class, an.stored(), an.sort(), false, true, an.index()));
                    result.add(ret);
                } else {
                    error(c, "DoubleField not fit");
                }
            } else if (item.isAnnotationPresent(BooleanField.class)) {
                boolean fit = fitBooleanField(item);
                if (fit) {
                    BooleanField an = item.getAnnotation(BooleanField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), Boolean.class, an.stored(), false, false, true, true));
                    result.add(ret);
                } else {
                    error(c, "BooleanField not fit");
                }
            } else if (item.isAnnotationPresent(StringField.class)) {
                boolean fit = fitStringField(item);
                if (fit) {
                    StringField an = item.getAnnotation(StringField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), String.class, an.stored(), false, false, true, true));
                    result.add(ret);
                } else {
                    error(c, "StringField not fit");
                }
            } else if (item.isAnnotationPresent(TextField.class)) {
                boolean fit = fitTextField(item);
                if (fit) {
                    TextField an = item.getAnnotation(TextField.class);
                    item.setAccessible(true);
                    List<FieldDesc> ret = new ArrayList<>(context);
                    ret.add(new FieldDesc(item, isCollection(item), String.class, an.stored(), false, true, true, true));
                    result.add(ret);
                } else {
                    error(c, "TextField not fit");
                }
            }
            // other field as a.b.c
            else if (!isPrimitive(item)) {
                subFields(context, item, result);
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
                || Objects.equals(rt, boolean.class)
                || Objects.equals(rt, Integer.class)
                || Objects.equals(rt, Long.class)
                || Objects.equals(rt, Float.class)
                || Objects.equals(rt, Double.class)
                || Objects.equals(rt, Boolean.class)
                || Objects.equals(rt, String.class)
                || Objects.equals(rt, BigInteger.class)
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
     * with BigInteger field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitBigIntegerField(Field f) {
        if (Collection.class.isAssignableFrom(f.getType())) {
            if (!(f.getType() == Set.class || f.getType() == List.class || f.getType() == Collection.class)) {
                return false;
            }
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            Class<?> rt = (Class<?>) pt.getActualTypeArguments()[0];
            return rt == BigInteger.class;
        } else {
            return f.getType() == BigInteger.class;
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
     * with size field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitSizeField(Field f) {
        if (Collection.class.isAssignableFrom(f.getType())) {
            return f.getType() == Set.class || f.getType() == List.class || f.getType() == Collection.class;
        }
        return false;
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
     * with boolean field is ok ?
     *
     * @param f
     * @return
     */
    public static boolean fitBooleanField(Field f) {
        if (Collection.class.isAssignableFrom(f.getType())) {
            if (!(f.getType() == Set.class || f.getType() == List.class || f.getType() == Collection.class)) {
                return false;
            }
            ParameterizedType pt = (ParameterizedType) f.getGenericType();
            Class<?> rt = (Class<?>) pt.getActualTypeArguments()[0];
            return rt == boolean.class || rt == Boolean.class;
        } else {
            return f.getType() == boolean.class || f.getType() == Boolean.class;
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
    private static boolean isCollection(Class<?> c) {
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
    public static void allField(Class<?> claz, List<Field> all) {
        Field[] fs = claz.getDeclaredFields();
        all.addAll(Arrays.asList(fs));
        // the super fields
        Class sclaz = claz.getSuperclass();
        if (sclaz != null) {
            allField(sclaz, all);
        }
    }


    public static void verifyIndexAnnotation(Class<?> entityClass) {
        if (entityClass.getAnnotation(Index.class) == null) {
            error(entityClass, "missing @Index annotation");
        }
    }

    public static void error(Class<?> entityClass, String msg) {
        throw new RuntimeException(entityClass.getCanonicalName() + ": " + msg);
    }

    /**
     * update object by term
     * <p>
     * first delete by term and then add documents
     *
     * @param obs
     */
    @SafeVarargs
    public final void index(T... obs) throws Exception {
        if (obs != null) {
            for (T ob : obs) {
                if (ob != null && ob.getClass() == type) {
                    // doc id
                    String did = value(docId, ob).toString();
                    if (did == null || did.isEmpty()) {
                        throw new NullPointerException("doc id is null ");
                    }
                    final Document doc = new Document();
                    for (List<FieldDesc> fs : allFields.values()) {
                        final FieldDesc f = fs.get(fs.size() - 1);  // the end
                        if (f.isJustSize()) {
                            String name = name(fs);
                            name = name + ".size";
                            final int size = size(fs, ob);
                            if (f.isIndex()) {
                                doc.add(new IntPoint(name, size));
                            }
                            if (f.isStored()) {
                                doc.add(new StoredField(name, size));
                            }
                            if (f.isSort()) {
                                doc.add(new SortedNumericDocValuesField(name, size));
                            }
                            continue;
                        }
                        final Object v = value(fs, ob);
                        if (v != null) {
                            Class<?> inner = f.getInner();
                            String name = name(fs);
                            // collection
                            if (Collection.class.isAssignableFrom(v.getClass())) {
                                if (inner == int.class || inner == Integer.class) {
                                    Collection<Integer> c = (Collection<Integer>) v;
                                    c.forEach(i -> {
                                        if (i != null) {
                                            if (f.isIndex()) {
                                                doc.add(new IntPoint(name, i));
                                            }
                                            if (f.isStored()) {
                                                doc.add(new StoredField(name, i));
                                            }
                                            if (f.isSort()) {
                                                doc.add(new SortedNumericDocValuesField(name, i));
                                            }
                                        }
                                    });
                                } else if (inner == long.class || inner == Long.class) {
                                    Collection<Long> c = (Collection<Long>) v;
                                    c.forEach(i -> {
                                        if (i != null) {
                                            if (f.isIndex()) {
                                                doc.add(new LongPoint(name, i));
                                            }
                                            if (f.isStored()) {
                                                doc.add(new StoredField(name, i));
                                            }
                                            if (f.isSort()) {
                                                doc.add(new SortedNumericDocValuesField(name, i));
                                            }
                                        }
                                    });
                                } else if (inner == BigInteger.class) {
                                    Collection<BigInteger> c = (Collection<BigInteger>) v;
                                    c.forEach(i -> {
                                        if (i != null) {
                                            if (f.isIndex()) {
                                                doc.add(new BigIntegerPoint(name, i));
                                            }
                                            if (f.isStored()) {
                                                doc.add(new StoredField(name, i.toString()));
                                            }
                                        }
                                    });
                                } else if (inner == float.class || inner == Float.class) {
                                    Collection<Float> c = (Collection<Float>) v;
                                    c.forEach(i -> {
                                        if (i != null) {
                                            if (f.isIndex()) {
                                                doc.add(new FloatPoint(name, i));
                                            }
                                            if (f.isStored()) {
                                                doc.add(new StoredField(name, i));
                                            }
                                            if (f.isSort()) {
                                                doc.add(new SortedNumericDocValuesField(name, NumericUtils.floatToSortableInt(i)));
                                            }
                                        }
                                    });
                                } else if (inner == double.class || inner == Double.class) {
                                    Collection<Double> c = (Collection<Double>) v;
                                    c.forEach(i -> {
                                        if (i != null) {
                                            if (f.isIndex()) {
                                                doc.add(new DoublePoint(name, i));
                                            }
                                            if (f.isStored()) {
                                                doc.add(new StoredField(name, i));
                                            }
                                            if (f.isSort()) {
                                                doc.add(new SortedNumericDocValuesField(name, NumericUtils.doubleToSortableLong(i)));
                                            }
                                        }
                                    });
                                } else if (inner == Boolean.class) {
                                    Collection<Boolean> c = (Collection<Boolean>) v;
                                    c.forEach(i -> {
                                        if (i != null) {
                                            doc.add(new org.apache.lucene.document.StringField(name, i ? "true" : "false", f.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
                                        }
                                    });
                                } else if (inner == String.class) {
                                    Collection<String> c = (Collection<String>) v;
                                    c.forEach(i -> {
                                        if (i != null && !i.trim().isEmpty()) {
                                            if (f.isTokenized()) {
                                                doc.add(new org.apache.lucene.document.TextField(name, i, f.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
                                            } else {
                                                doc.add(new org.apache.lucene.document.StringField(name, i, f.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
                                            }
                                        }
                                    });
                                }
                            }
                            // not collection
                            else {
                                if (inner == int.class || inner == Integer.class) {
                                    Integer i = (Integer) v;
                                    if (f.isIndex()) {
                                        doc.add(new IntPoint(name, i));
                                    }
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, i));
                                    }
                                    if (f.isSort()) {
                                        doc.add(new SortedNumericDocValuesField(name, i));
                                    }
                                } else if (inner == long.class || inner == Long.class) {
                                    Long i = (Long) v;
                                    if (f.isIndex()) {
                                        doc.add(new LongPoint(name, i));
                                    }
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, i));
                                    }
                                    if (f.isSort()) {
                                        doc.add(new SortedNumericDocValuesField(name, i));
                                    }
                                } else if (inner == BigInteger.class) {
                                    BigInteger i = (BigInteger) v;
                                    if (f.isIndex()) {
                                        doc.add(new BigIntegerPoint(name, i));
                                    }
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, String.valueOf(i)));
                                    }
                                } else if (inner == float.class || inner == Float.class) {
                                    Float i = (Float) v;
                                    if (f.isIndex()) {
                                        doc.add(new FloatPoint(name, i));
                                    }
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, i));
                                    }
                                    if (f.isSort()) {
                                        int sortedNumber = NumericUtils.floatToSortableInt(i);
                                        doc.add(new SortedNumericDocValuesField(name, sortedNumber));
                                    }
                                } else if (inner == double.class || inner == Double.class) {
                                    Double i = (Double) v;
                                    if (f.isIndex()) {
                                        doc.add(new DoublePoint(name, i));
                                    }
                                    if (f.isStored()) {
                                        doc.add(new StoredField(name, i));
                                    }
                                    if (f.isSort()) {
                                        long sortedNumber = NumericUtils.doubleToSortableLong(i);
                                        doc.add(new SortedNumericDocValuesField(name, sortedNumber));
                                    }
                                } else if (inner == boolean.class || inner == Boolean.class) {
                                    Boolean i = (Boolean) v;
                                    doc.add(new org.apache.lucene.document.StringField(name, i ? "true" : "false", f.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
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
                    doc.add(new org.apache.lucene.document.StringField(docId.getField().getName(), did, docId.isStored() ? org.apache.lucene.document.Field.Store.YES : org.apache.lucene.document.Field.Store.NO));
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
     * value for sortField
     *
     * @param root
     * @param sf
     * @return
     */
    private Object value(T root, SortField sf) throws IllegalAccessException {
        List<FieldDesc> descs = allFields.get(sf.getField());
        return value(descs, root);
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
     * @param fs
     * @param root
     * @return
     * @throws IllegalAccessException
     */
    private static int size(List<FieldDesc> fs, Object root) throws IllegalAccessException {
        Collection c = (Collection) value(fs, root);
        return c == null ? 0 : c.size();
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
                    Collection<Object> ret = new HashSet<>();
                    Collection<?> c = (Collection<?>) value;
                    for (Object item : c) {
                        List<FieldDesc> fds = fs.subList(1, fs.size());
                        Object temp = value(fds, item);
                        if (temp != null) {
                            if (isCollection(temp.getClass())) {
                                Collection tc = (Collection) temp;
                                ret.addAll(tc);
                            } else {
                                ret.add(temp);
                            }
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
            Collection<?> c = (Collection<?>) v;
            return c.isEmpty();
        }
        return false;
    }

    /**
     * commit
     *
     * @return
     */
    public long commit() throws IOException {
        return indexWriter.commit();
    }

    /**
     * close writer and directory
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
        try {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            FieldDesc fd = docId;
            builder.add(new TermQuery(new Term(fd.getField().getName(), id)), BooleanClause.Occur.FILTER);
            TopDocs topDocs = indexSearcher.searchAfter(null, builder.build(), 1);
            ScoreDoc[] hits = topDocs.scoreDocs;
            if (hits != null && hits.length > 0) {
                T dsi = (T) this.type.getDeclaredConstructor().newInstance();  // ? not very ok . . .
                for (ScoreDoc sc : hits) {
                    int did = sc.doc;
                    Document doc = indexSearcher.getIndexReader().storedFields().document(did);
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
        } finally {
            searcherManager.release(indexSearcher);
        }
        return null;
    }

    /**
     * entity exist ?
     *
     * @param id doc id
     * @return
     */
    public boolean exist(String id) throws IOException {
        IndexSearcher indexSearcher = searcherManager.acquire();
        try {
            BooleanQuery.Builder builder = new BooleanQuery.Builder();
            FieldDesc fd = docId;
            builder.add(new TermQuery(new Term(fd.getField().getName(), id)), BooleanClause.Occur.FILTER);
            TopDocs topDocs = indexSearcher.searchAfter(null, builder.build(), 1);
            ScoreDoc[] hits = topDocs.scoreDocs;
            return hits != null && hits.length > 0;
        } finally {
            searcherManager.release(indexSearcher);
        }
    }

    /**
     * delete doc
     *
     * @param field
     * @param v
     */
    public long deleteDocuments(String field, Object v) throws Exception {
        Query query = buildExactQuery(field, v);
        if (query != null) {
            return deleteDocuments(query);
        } else {
            throw new RuntimeException("query not valid ");
        }
    }

    /**
     * delete from query
     *
     * @param queries
     */
    public long deleteDocuments(Query... queries) throws IOException {
        return indexWriter.deleteDocuments(queries);
    }

    /**
     * delete from term
     *
     * @param terms
     * @return
     * @throws IOException
     */
    public long deleteDocuments(Term... terms) throws IOException {
        return indexWriter.deleteDocuments(terms);
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
     * query
     *
     * @param after
     * @param query
     * @param n
     * @param sort
     * @return
     * @throws Exception
     */
    public QueryResult<T> query(T after, Query query, int n, Sort sort) throws Exception {
        FieldDoc fd = after != null ? new FieldDoc(after.doc, after.score, sort == null ? new Object[]{} : Arrays.stream(sort.getSort()).map(f -> {
            try {
                return value(after, f);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }).toArray()) : null;
        return queryAfter(fd, query, n, sort);
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
        return queryAfter((FieldDoc) null, field, v, n, sort);
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
        return queryAfter(after != null ? new FieldDoc(after.doc, after.score, sort == null ? new Object[]{} : Arrays.stream(sort.getSort()).map(f -> {
            try {
                return value(after, f);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }).toArray()) : null, field, v, n, sort);
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
    public QueryResult<T> queryAfter(FieldDoc after, String field, Object v, int n, Sort sort) throws Exception {
        QueryResult<T> queryResult = null;
        Query query = buildExactQuery(field, v);
        if (query != null) {
//            if (sort != null && after != null) {
//                after = (after instanceof FieldDoc) ? after : new FieldDoc(after.doc, after.score);
//            }
            queryResult = queryAfter(after, query, n, sort);
        }
        return queryResult == null ? QueryResult.empty() : queryResult;
    }

    /**
     * all result
     *
     * @return
     * @throws Exception
     */
    public QueryResult<T> all() throws Exception {
        return all(null, 128, null);
    }

    /**
     * all result
     *
     * @param after
     * @param n
     * @return
     * @throws Exception
     */
    public QueryResult<T> all(T after, int n, Sort sort) throws Exception {
        Query q = new MatchAllDocsQuery();
        return queryAfter(after != null ? new FieldDoc(after.doc, after.score, sort == null ? new Object[]{} : Arrays.stream(sort.getSort()).map(f -> {
            try {
                return value(after, f);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }).toArray()) : null, q, n, sort);
    }

    /**
     * build query for filed exact query
     *
     * @param field
     * @param v
     * @return
     */
    public Query buildExactQuery(String field, Object v) {
        List<FieldDesc> list = docId.getField().getName().equals(field) ? Collections.singletonList(docId) : allFields.get(field);
        if (!list.isEmpty()) {
            FieldDesc fd = list.get(list.size() - 1);
            Class<?> type = fd.getInner();
            if (type == int.class || type == Integer.class) {
                return IntPoint.newExactQuery(field, (Integer) v);
            } else if (type == long.class || type == Long.class) {
                return LongPoint.newExactQuery(field, (Long) v);
            } else if (type == BigInteger.class) {
                return BigIntegerPoint.newExactQuery(field, (BigInteger) v);
            } else if (type == float.class || type == Float.class) {
                return FloatPoint.newExactQuery(field, (Float) v);
            } else if (type == double.class || type == Double.class) {
                return DoublePoint.newExactQuery(field, (Double) v);
            } else if (type == boolean.class || type == Boolean.class) {
                return new TermQuery(new Term(field, ((Boolean) v) ? "true" : "false"));
            } else if (type == String.class) {
                return new TermQuery(new Term(field, (String) v));
            }
        }
        return null;
    }

    /**
     * boolean query builder
     *
     * @return
     */
    public static BooleanQuery.Builder booleanQuery() {
        return new BooleanQuery.Builder();
    }

    /**
     * or
     *
     * @param queries
     * @return
     */
    public static BooleanQuery.Builder should(BooleanQuery.Builder builder, Query... queries) {
        Arrays.stream(queries).forEach(q -> builder.add(q, BooleanClause.Occur.SHOULD));
        return builder;
    }

    /**
     * must
     *
     * @param queries
     * @return
     */
    public static BooleanQuery.Builder must(BooleanQuery.Builder builder, Query... queries) {
        Arrays.stream(queries).forEach(q -> builder.add(q, BooleanClause.Occur.MUST));
        return builder;
    }

    /**
     * must not
     *
     * @param builder
     * @param queries
     */
    public static BooleanQuery.Builder mustNot(BooleanQuery.Builder builder, Query... queries) {
        Arrays.stream(queries).forEach(q -> builder.add(q, BooleanClause.Occur.MUST_NOT));
        return builder;
    }

    /**
     * filter
     *
     * @param builder
     * @param queries
     */
    public static BooleanQuery.Builder filter(BooleanQuery.Builder builder, Query... queries) {
        Arrays.stream(queries).forEach(q -> builder.add(q, BooleanClause.Occur.FILTER));
        return builder;
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
    public QueryResult<T> queryAfter(FieldDoc after, Query query, int n, Sort sort) throws Exception {
        IndexSearcher indexSearcher = null;
        long total;
        List<T> ret = new ArrayList<>(n);
        try {
            indexSearcher = searcherManager.acquire();
            TopDocs topDocs = sort == null ? indexSearcher.searchAfter(after, query, n) : indexSearcher.searchAfter(after, query, n, sort);
            total = topDocs.totalHits.value;
            ScoreDoc[] hits = topDocs.scoreDocs;
            if (hits != null && hits.length > 0) {
                T dsi = (T) this.type.getDeclaredConstructor().newInstance();  // ? not very ok . . .
                for (ScoreDoc sc : hits) {
                    int did = sc.doc;
                    Document doc = indexSearcher.getIndexReader().storedFields().document(did);
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
     * tokens from default analyzer
     *
     * @param text
     * @return
     * @throws IOException
     */
    public List<String> tokens(String text) throws IOException {
        return tokens(defaultAnalyzer, text);
    }

    /**
     * tokens from analyzer
     *
     * @param text
     * @return
     */
    public static List<String> tokens(Analyzer analyzer, String text) throws IOException {
        List<String> tokens = new ArrayList<>();
        try (TokenStream stream = analyzer.tokenStream("", text)) {
            CharTermAttribute cta = stream.addAttribute(CharTermAttribute.class);
            stream.reset();
            while ((stream.incrementToken())) {
                tokens.add(cta.toString());
            }
        } catch (Exception ex) {
            throw new IOException(ex);
        }
        return tokens;
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
     * refresh with blocking
     *
     * @throws IOException
     */
    public void maybeRefreshBlocking() throws IOException {
        searcherManager.maybeRefreshBlocking();
    }

    /**
     * refresh listener
     *
     * @param listener
     */
    public void addRefreshListener(ReferenceManager.RefreshListener listener) {
        searcherManager.addListener(listener);
    }

    /**
     * remove refresh listener
     *
     * @param listener
     */
    public void removeRefreshListener(ReferenceManager.RefreshListener listener) {
        searcherManager.removeListener(listener);
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
    public Analyzer defaultAnalyzer() {
        return defaultAnalyzer;
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
