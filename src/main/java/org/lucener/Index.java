package org.lucener;

import org.apache.lucene.analysis.Analyzer;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * lucene index
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Index {

    /**
     * index directory path
     *
     * @return
     */
    String value() default "";

    /**
     * store serialized data ?
     *
     * @return
     */
    boolean stored() default true;

    /**
     * analyzer
     *
     * @return
     */
    Class<? extends Analyzer> analyzer() default IKAnalyzer.class;
}
