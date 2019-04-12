package odata_import;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An ODataValue is serialized and deserialized
 *
 * If combined with ODataExpand, then ODataValue will only be used for serialization (equivalent to using
 * ODataValueExportOnly) TODO Maybe this should be more explicit? Or allow the user to decide?
 *
 * FIXME all built-in functions should consider ODataValueExportOnly and ODataValueImportOnly
 *
 * @author Jonathan Benn
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface ODataValue {

    /**
     * @return the OData property name
     */
    String value();

    /**
     * @return (optional) the sort order for this entity when serializing (lower numbers appear first)
     */
    int order() default 0;
}
