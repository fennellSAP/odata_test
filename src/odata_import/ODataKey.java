package odata_import;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * The primary key is used to uniquely identify the OData entity
 *
 * @author Jonathan Benn
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface ODataKey {

    /**
     * @return the OData property name
     */
    String value();

    /**
     * @return (optional) the sort order for this entity when serializing (lower numbers appear first)
     */
    int order() default 0;
}
