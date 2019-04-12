package odata_import;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * An OData value that contains other OData entities, and whose value is obtained by expanding (sending another request
 * to get the information on the sub-entities)
 *
 * We expect a getter and setter of type List<? extends ODataSerializable>
 *
 * @author Jonathan Benn
 */
@Retention(RUNTIME)
@Target(FIELD)
public @interface ODataExpand {

    /**
     * @return the fully-qualified type of the class (which extends ODataSerializable)
     */
    String type();

    /**
     * @return the OData property name
     */
    String value();

    /**
     * @return (optional) the sort order for this entity when serializing (lower numbers appear first)
     */
    int order() default 0;
}
