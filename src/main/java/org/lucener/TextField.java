package org.lucener;

import org.apache.lucene.analysis.Analyzer;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * text field
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface TextField {
    
    /**
     * stored
     *
     * @return
     */
    boolean stored() default false;

    /**
     * analyzer
     *
     * @return
     */
    Class<? extends Analyzer> analyzer() default IKAnalyzer.class;

    /**
     * ik analyzer use smart
     *
     * @return
     */
    boolean ikSmart() default false;

}
