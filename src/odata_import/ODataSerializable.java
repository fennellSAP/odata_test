package odata_import;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Implementing this interface enables a class to serialize and de-serialize in OData requests. Here is what you need to
 * do:
 *
 * <ol>
 * <li>Ensure that there is a public zero-argument default constructor</li>
 * <li>Annotate any key fields with the {@link ODataKey} or {@link ODataSecondaryKey} annotations. These fields can have
 * any type!</li>
 * <li>Annotate any fields that need to be de-/serialized with the {@link ODataValue} annotation. These fields can have
 * any type!</li>
 * <li>Ensure that any fields annotated with {@link ODataKey}, {@link ODataSecondaryKey} or {@link ODataValue} (but not
 * {@link ODataExpand}) have String-type setters and appropriately typed getters that follow the standard Java naming
 * convention. <i>i.e.</i> attribute {@code myProperty} should have getter {@code T getMyProperty()} (where {@code T}
 * could be any primitive type or a wrapper like {@link Double}, {@link Date}, or {@link String}) and setter
 * {@code setMyProperty(String)}. For the case of a {@link Date}, make sure to set the time zone correctly if it is not
 * the default for the local Java environment: call {@code entity.ODATA_DATE_FORMATTER.setTimeZone()}.</li>
 * <li>Annotate any expandable fields with the {@link ODataExpand} annotation. These fields can have any type!</li>
 * <li>Ensure that any fields annotated with {@link ODataExpand} have {@code List<? extends ODataSerializable>}-type
 * getters and setters that follow the standard Java naming convention. <i>i.e.</i> attribute {@code myProperty} should
 * have getter {@code List<? extends ODataSerializable> getMyProperty()} and setter
 * {@code setMyProperty(List<? extends ODataSerializable>)}</li>
 * </ol>
 *
 * Ensure that all your setters are independent of each other, since we can't predict in which order they will be
 * called.
 *
 * FIXME this interface should be parameterized so that its methods can return the correct type instead of returning
 * generic ODataSerializable
 *
 * @author Jonathan Benn
 */
public interface ODataSerializable {

    public static final String ANNOTATION_TYPE_MUST_HAVE_ORDER_METHOD = "Annotation type must have 'order' method: ";
    public static final String ANNOTATION_TYPE_MUST_HAVE_VALUE_METHOD = "Annotation type must have 'value' method: ";
    public static final String ENTITY_METHOD = " ENTITY: %s METHOD: %s";
    public static final String ERROR_FINDING_GETTER_METHOD = "Error finding getter method! Make sure the method exists and it's public.";
    public static final String ERROR_FINDING_SETTER_METHOD = "Error finding setter method! Make sure the method exists, it's public and accepts a String parameter.";
    public static final String ERROR_INVOKING_GETTER_METHOD = "Error invoking getter method!";
    public static final String GETTER_MUST_RETURN_STRING_LIST_OR_PRIMITIVE = "Getter must return String, List<? extends ODataSerializable> or primitive type.";
    public static final String IN_ENTITY_ANNOTATIONS_MAY_NOT_SHARE_VALUE = "In entity type '%s', annotations of type '%s' may not share the identical value '%s'";
    public static final String NO_FIELDS_FOUND_WITH_ANNOTATION_TYPE = "No fields found with annotation type %s in %s";

    /**
     * The standard format for OData dates, excluding milliseconds
     */
    public static final String ODATA_DATE_FORMAT = "'datetime'''yyyy-MM-dd'T'HH:mm:ss''";

    /**
     * The standard format for OData dates, including milliseconds
     */
    public static final String ODATA_DATE_FORMAT_MS = "'datetime'''yyyy-MM-dd'T'HH:mm:ss.SSS''";

    public static final String RETURN_VALUE_MUST_NEVER_BE_NULL = "Return value must never be null!";
    public static final String XML_DOCUMENT_MUST_CONTAIN_EXACTLY_ONE_ODATA_ENTITY = "XML Document must contain exactly one OData entity. Length was: ";

    /**
     * Defines how {@link Date} objects returned by this class's getters will be formatted as a {@link String}
     * <p>
     * Note how this is not static, therefore each object gets its own formatter, which could have a different time
     * zone. Time zone always defaults to the default for the JVM.
     * <p>
     * FIXME - it seems that I'm mistaken and Java considers this static anyway... make sure this is ok!
     */
    public final DateFormat dateFormat = new DateFormat(ODATA_DATE_FORMAT_MS);

    /**
     * TODO
     *
     * @param annotationType
     * @return
     * @throws DeserializationException
     */
    public static List<? extends ODataSerializable> fromXml(Class<? extends ODataSerializable> entityType,
            Document document, Class<? extends Annotation> annotationType) throws DeserializationException {

        List<ODataSerializable> entities = new ArrayList<>();
        NodeList entries = getEntries(document);

        for (int i = 0; i < entries.getLength(); ++i) {
            ODataSerializable entity = null;
            try {
                entity = entityType.newInstance();
            }
            catch (Throwable t) {
                throw new RuntimeException(t);
            }
            Node entry = entries.item(i);
            entity.fromXml(entry, annotationType);
            entities.add(entity);
        }
        return entities;
    }

    /**
     * Only makes sense to call this for annotations of simple String based attributes
     *
     * FIXME massive duplicate code!
     *
     * FIXME test with annotation type ODataExpand
     *
     * FIXME implement the sorting code in across ODataSerializable
     *
     * @param entity
     * @param annotationType
     * @param quote
     *            the quote character to use for string-type values
     *
     * @return TODO
     * @throws IOException
     */
    public static Map<String, String> getAnnotationsAndGetterValues(ODataSerializable entity,
            Class<? extends Annotation> annotationType, String quote) {

        Map<String, String> unsortedReturnValues = new HashMap<>();
        Map<String, Integer> orderMap = new HashMap<>();

        Field[] fields = entity.getClass().getDeclaredFields();
        for (Field field : fields) {
            Annotation[] annotations = field.getDeclaredAnnotations();
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(annotationType)) {
                    String getterName = "";
                    String key;
                    try {
                        key = (String) annotationType.getMethod("value").invoke(annotation);
                    }
                    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                            | NoSuchMethodException | SecurityException e) {
                        throw new RuntimeException(ANNOTATION_TYPE_MUST_HAVE_VALUE_METHOD + annotationType, e);
                    }
                    Integer order;
                    try {
                        order = (Integer) annotationType.getMethod("order").invoke(annotation);
                    }
                    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                            | NoSuchMethodException | SecurityException e) {
                        throw new RuntimeException(ANNOTATION_TYPE_MUST_HAVE_ORDER_METHOD + annotationType, e);
                    }
                    orderMap.put(key, order);

                    String fieldName = field.getName();
                    getterName = "get" + fieldName.substring(0, 1).toUpperCase()
                            + fieldName.substring(1, fieldName.length());
                    Method getter;
                    try {
                        getter = entity.getClass().getMethod(getterName);
                    }
                    catch (NoSuchMethodException | SecurityException e) {
                        throw new RuntimeException(
                                String.format(ERROR_FINDING_GETTER_METHOD + ENTITY_METHOD, entity, getterName), e);
                    }

                    if (unsortedReturnValues.containsKey(key)) {
                        throw new RuntimeException(String.format(IN_ENTITY_ANNOTATIONS_MAY_NOT_SHARE_VALUE,
                                entity.getClass(), annotation.annotationType(), key));
                    }

                    String getterReturnValue = getGetterValue(entity, getter, annotationType, quote);
                    unsortedReturnValues.put(key, getterReturnValue);
                }
            }
        }

        if (unsortedReturnValues.isEmpty()) {
            throw new RuntimeException(
                    String.format(NO_FIELDS_FOUND_WITH_ANNOTATION_TYPE, annotationType, entity.getClass()));
        }

        List<Map.Entry<String, Integer>> orderEntries = new ArrayList<>(orderMap.entrySet());
        Collections.sort(orderEntries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> x, Map.Entry<String, Integer> y) {
                return Integer.compare(x.getValue(), y.getValue());
            }
        });
        Map<String, String> sortedReturnValues = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> orderEntry : orderEntries) {
            String sortedKey = orderEntry.getKey();
            String sortedValue = unsortedReturnValues.get(sortedKey);
            sortedReturnValues.put(sortedKey, sortedValue);
        }

        return sortedReturnValues;
    }

    /**
     * FIXME code duplication
     *
     * @return TODO
     * @throws IOException
     */
    public static Map<String, Method> getAnnotationsAndSetters(Class<? extends ODataSerializable> entityClass,
            Class<? extends Annotation> annotationType, Class<? extends Annotation> disallowedAnnotationType) {

        Map<String, Method> returnValue = new HashMap<>();

        Field[] fields = entityClass.getDeclaredFields();
        for (Field field : fields) {
            Annotation[] annotations = field.getDeclaredAnnotations();

            // if any field contains the disallowed annotation type
            if (Arrays.stream(annotations).anyMatch(a -> a.annotationType().equals(disallowedAnnotationType))) {
                // then skip that field -- its value should not be set directly
                continue;
            }
            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(annotationType)) {
                    String key;
                    try {
                        key = (String) annotationType.getMethod("value").invoke(annotation);
                    }
                    catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException
                            | NoSuchMethodException | SecurityException e1) {
                        throw new RuntimeException(ANNOTATION_TYPE_MUST_HAVE_VALUE_METHOD + annotationType, e1);
                    }
                    String fieldName = field.getName();
                    String setterName = "set" + fieldName.substring(0, 1).toUpperCase()
                            + fieldName.substring(1, fieldName.length());
                    Method setter = null;
                    try {
                        setter = entityClass.getMethod(setterName, String.class);
                    }
                    catch (NoSuchMethodException e) {
                        throw new RuntimeException(
                                ERROR_FINDING_SETTER_METHOD + String.format(ENTITY_METHOD, entityClass, setterName), e);
                    }

                    if (returnValue.containsKey(key)) {
                        throw new RuntimeException(String.format(IN_ENTITY_ANNOTATIONS_MAY_NOT_SHARE_VALUE, entityClass,
                                annotation.annotationType(), key));
                    }

                    returnValue.put(key, setter);
                }
            }
        }

        if (returnValue.isEmpty()) {
            throw new RuntimeException(
                    String.format(NO_FIELDS_FOUND_WITH_ANNOTATION_TYPE, annotationType, entityClass));
        }

        return returnValue;
    }

    /**
     * FIXME code duplication
     *
     * @return TODO Map of (1) OData expand URL suffix (ODataExpand.value), (2) Entity Type (ODataExpand.type), (3)
     *         setter method for the ODataExpand field
     * @throws IOException
     */
    public static Map<String, Entry<Class<? extends ODataSerializable>, Method>> getAnnotationsAndSettersForExpand(
            ODataSerializable entity) {

        Map<String, Entry<Class<? extends ODataSerializable>, Method>> returnValue = new HashMap<>();
        Class<? extends Annotation> annotationType = ODataExpand.class;

        Field[] fields = entity.getClass().getDeclaredFields();
        for (Field field : fields) {
            Annotation[] annotations = field.getDeclaredAnnotations();

            for (Annotation annotation : annotations) {
                if (annotation.annotationType().equals(annotationType)) {
                    try {
                        String key = (String) annotationType.getMethod("value").invoke(annotation);
                        Class<? extends ODataSerializable> type = (Class<? extends ODataSerializable>) Class
                                .forName((String) annotationType.getMethod("type").invoke(annotation));
                        String fieldName = field.getName();
                        String setterName = "set" + fieldName.substring(0, 1).toUpperCase()
                                + fieldName.substring(1, fieldName.length());
                        Method setter = entity.getClass().getMethod(setterName, List.class);
                        returnValue.put(key, new SimpleEntry<Class<? extends ODataSerializable>, Method>(type, setter));
                    }
                    catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }

        if (returnValue.isEmpty()) {
            throw new RuntimeException(
                    String.format(NO_FIELDS_FOUND_WITH_ANNOTATION_TYPE, annotationType, entity.getClass()));
        }

        return returnValue;
    }

    /**
     * Given a parent XML element/node, finds a child node by its name. If there are multiple elements with the same
     * name, then returns the first one found.
     *
     * @param node
     *            the parent node
     * @param childName
     *            the element name of the child node
     * @return the first child node found, or null if nothing was found
     */
    public static Node getChildByName(Node node, String childName) {
        NodeList children = node.getChildNodes();
        int numberOfChildren = children.getLength();
        for (int i = 0; i < numberOfChildren; ++i) {
            Node child = children.item(i);
            if (child.getNodeName().equals(childName)) {
                return child;
            }
        }
        return null;
    }

    /**
     * @param document
     * @return TODO
     */
    public static NodeList getEntries(Document document) {
        return document.getElementsByTagName("entry");
    }

    /**
     * TODO this needs to correctly support all OData primitive data types
     * https://www.odata.org/documentation/odata-version-2-0/overview/
     *
     * @param entity
     * @param oDataSerializable
     * @param getter
     * @param annotationType
     * @param quote
     *            the quote character to use for string-type values
     * @return TODO return value will be surrounded by quotes if it's a String
     */
    public static String getGetterValue(ODataSerializable entity, Method getter,
            Class<? extends Annotation> annotationType, String quote) {

        Object getterReturnValue = null;
        try {
            getterReturnValue = getter.invoke(entity);
        }
        catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new RuntimeException(String.format(ERROR_INVOKING_GETTER_METHOD + ENTITY_METHOD, entity, getter), e);
        }
        if (getterReturnValue == null) {
            throw new RuntimeException(String.format(RETURN_VALUE_MUST_NEVER_BE_NULL + ENTITY_METHOD, entity, getter));
        }

        String castErrorMessage = String.format(GETTER_MUST_RETURN_STRING_LIST_OR_PRIMITIVE + ENTITY_METHOD, entity,
                getter);

        try {
            // if the getter returns a List of ODataSerializable
            Class<?> returnType = getter.getReturnType();
            if (returnType.equals(List.class)) {
                @SuppressWarnings("unchecked")
                List<? extends ODataSerializable> getterListReturnValue = (List<? extends ODataSerializable>) getterReturnValue;
                return entity.toJson(getterListReturnValue, annotationType);
            }
            // else if the getter returns a GUID type
            else if (returnType.equals(String.class)
                    && ((String) getterReturnValue).matches(ODataConnection.IS_GUID_REGEX)) {
                return (String) getterReturnValue;
            }
            // else if the getter returns a String or String-like type (String-type, use quotes)
            else if (returnType.equals(String.class)) {
                String getterStringReturnValue = (String) getterReturnValue;
                return (quote != null) ? quote + getterStringReturnValue + quote : getterStringReturnValue;
            }
            // else if the getter returns a Date type (date type, no quotes)
            else if (returnType.equals(Date.class)) {
                return dateFormat.format((Date) getterReturnValue);
            }
            else if (returnType.equals(Character.class) || returnType.equals(char.class)) {
                String getterStringReturnValue = Character.toString((char) getterReturnValue);
                return (quote != null) ? quote + getterStringReturnValue + quote : getterStringReturnValue;
            }
            // else if the getter returns a number or Boolean type (raw type, no quotes)
            else if (returnType.equals(Boolean.class) || returnType.equals(boolean.class)) {
                return Boolean.toString((boolean) getterReturnValue);
            }
            else if (returnType.equals(Byte.class) || returnType.equals(byte.class)) {
                return Byte.toString((byte) getterReturnValue);
            }
            else if (returnType.equals(Short.class) || returnType.equals(short.class)) {
                return Short.toString((short) getterReturnValue);
            }
            else if (returnType.equals(Integer.class) || returnType.equals(int.class)) {
                return Integer.toString((int) getterReturnValue);
            }
            else if (returnType.equals(Long.class) || returnType.equals(long.class)) {
                return Long.toString((long) getterReturnValue);
            }
            else if (returnType.equals(Float.class) || returnType.equals(float.class)) {
                return Float.toString((float) getterReturnValue);
            }
            else if (returnType.equals(Double.class) || returnType.equals(double.class)) {
                return Double.toString((double) getterReturnValue);
            }
            else {
                throw new RuntimeException(castErrorMessage);
            }
        }
        catch (ClassCastException e) {
            throw new RuntimeException(castErrorMessage, e);
        }
    }

    /**
     * Updates the entity in-situ based on the provided {@code propertyMap}. For example, for the following class:
     *
     * <pre>
     *      public class Fraction implements ODataSerializable {
     *
     *          &#64;ODataValue("Numerator")
     *          private int numerator;
     *
     *          &#64;ODataValue("Denominator")
     *          private int denominator;
     *
     *          ...
     *      }
     * </pre>
     *
     * You could input the following {@code propertyMap}:
     *
     * <pre>
     * { "Numerator" = "5", "Denominator" = "7" }
     * </pre>
     *
     * @param propertyMap
     *            fieldName-value pairs to use in setting the entity's field values
     * @param annotationType
     *            all entity attributes with this annotation will be updated from the {@code propertyMap}
     * @param requireMissingProperties
     *            if {@code true} then if the {@code propertyMap} does not contain any of the fields annotated with
     *            {@code annotationType} then will throw a {@link DeserializationException}. If {@code false} then
     *            ignores missing fields.
     * @return this object (for function call chaining)
     * @throws DeserializationException
     *             if {@code requireMissingProperties} is {@code true} and there are fields missing from the
     *             {@code propertyMap}
     */
    public default ODataSerializable fromMap(Map<String, String> propertyMap,
            Class<? extends Annotation> annotationType, boolean requireMissingProperties)
            throws DeserializationException {

        // FIXME lots of refactoring potential here, relative to fromXml

        Map<String, Method> annotations = ODataSerializable.getAnnotationsAndSetters(this.getClass(), annotationType,
                ODataExpand.class);

        for (Entry<String, Method> annotation : annotations.entrySet()) {
            String oDataPropertyName = annotation.getKey();
            Method setter = annotation.getValue();
            if (!propertyMap.containsKey(oDataPropertyName)) {
                if (requireMissingProperties) {
                    throw new DeserializationException().setMissingProperty(oDataPropertyName);
                }
                else {
                    continue;
                }
            }
            String valueToSet = propertyMap.get(oDataPropertyName);
            try {
                setter.invoke(this, valueToSet);
            }
            catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return this;
    }

    /**
     * TODO
     *
     * Updates the entity in-situ
     *
     * @param annotationType
     *            all entity attributes with this annotation will be updated from the XML
     * @return
     * @throws DeserializationException
     * @throws RuntimeException
     *             if you input a document containing 0 OData entity entries, or more than one OData entity entry
     */
    public default ODataSerializable fromXml(Document document, Class<? extends Annotation> annotationType)
            throws DeserializationException {
        NodeList entries = getEntries(document);
        if (entries.getLength() != 1) {
            throw new RuntimeException(XML_DOCUMENT_MUST_CONTAIN_EXACTLY_ONE_ODATA_ENTITY + entries.getLength());
        }
        return fromXml(entries.item(0), annotationType);
    }

    /**
     * TODO
     *
     * Updates the entity in-situ
     *
     * @param annotationType
     *            all entity attributes with this annotation will be updated from the XML
     * @return
     * @throws DeserializationException
     */
    public default ODataSerializable fromXml(Node entry, Class<? extends Annotation> annotationType)
            throws DeserializationException {

        Node content = ODataSerializable.getChildByName(entry, "content");
        Node properties = ODataSerializable.getChildByName(content, "m:properties");
        NodeList propertiesChildNodes = properties.getChildNodes();
        Map<String, String> propertyMap = new HashMap<>();

        // FIXME remove "d:" from property map keys
        for (int j = 0; j < propertiesChildNodes.getLength(); ++j) {
            Node property = propertiesChildNodes.item(j);
            propertyMap.put(property.getNodeName(), property.getTextContent());
        }

        Map<String, Method> annotations = ODataSerializable.getAnnotationsAndSetters(this.getClass(), annotationType,
                ODataExpand.class); // FIXME this doesn't need to be executed every time... does it?

        for (Entry<String, Method> annotation : annotations.entrySet()) {
            String oDataPropertyName = annotation.getKey();
            Method setter = annotation.getValue();
            if (propertyMap.containsKey("d:" + oDataPropertyName) == false) {
                throw new DeserializationException().setMissingProperty(oDataPropertyName);
            }
            String valueToSet = propertyMap.get("d:" + oDataPropertyName); // FIXME remove "d:" from property map keys
            try {
                setter.invoke(this, valueToSet);
            }
            catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }

        return this;
    }

    /**
     * TODO
     *
     * @throws IOException
     */
    public default String toJson(Class<? extends Annotation> annotationType) {

        Map<String, String> annotations = getAnnotationsAndGetterValues(this, annotationType, "\"");

        List<String> parameters = annotations.entrySet().stream().map(entry -> {
            return "\"" + entry.getKey() + "\":" + entry.getValue();
        }).collect(Collectors.toList());

        return "{" + String.join(",", parameters) + "}";
    }

    /**
     * CAVEAT this is not static to allow it to be overridden!! FIXME figure out a way to make this static and allow
     * developers to convert to a JSON string more easily (see PlanningFilter and ScheduleJobInfo, because in that case
     * the JSON gets stringified and it's tricky)
     *
     * @param annotationType
     * @return TODO
     */
    public default String toJson(List<? extends ODataSerializable> entities,
            Class<? extends Annotation> annotationType) {
        return "[" + String.join(",", entities.stream().map(e -> e.toJson(annotationType)).collect(Collectors.toList()))
                + "]";
    }
}
