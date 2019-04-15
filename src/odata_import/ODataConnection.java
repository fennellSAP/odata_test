package odata_import;


import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;


/**
 * Handles connecting to an OData service, sending a payload and processing a {@link Response}. Each
 * {@link ODataConnection} handles one OData entity.
 *
 * @author Jonathan Benn
 */
public class ODataConnection {

    static final String HTTP_REQUEST_FAILED_WITH_RESPONSE_CODE = "HTTP Request failed with response code: ";
    static final String INVALID_PARAMETER_VALUE_PAIRS = "parameterValuePairs length must be a non-zero and even value: ";
    static final String UNMATCHED_PARAMETER_VALUE_PAIRS = "Each parameter must be matched by a value: ";

    /**
     * The character set used by the server
     */
    public static final String CHARSET = StandardCharsets.UTF_8.name();

    /**
     * URL suffix to add to ask OData to return the number of matching entities in the response body
     */
    public static final String COUNT = "$count/";

    /**
     * Matches the OData type Edm.Guid
     */
    public static final String IS_GUID_REGEX = "^[gG][uU][iI][dD]'([A-Fa-f0-9\\-]+)'$";

    /**
     * @param assertion
     *            an expression that must evaluate to {@code true}
     * @param message
     *            the message to include in the {@link IllegalArgumentException} if the {@code assertion} is
     *            {@code false}
     * @throws IllegalArgumentException
     *             if {@code assertion} is {@code false}
     */
    private static void verify(boolean assertion, String message) {
        if (!assertion) {
            throw new IllegalArgumentException(message);
        }
    }

    /**
     * Encodes the given string so that it will be appropriate to post in a URL. Alphanumeric characters will not be
     * changed, and neither will these special characters: {@code "."}, {@code "-"}, {@code "*"}, {@code "_"},
     * {@code "'"} (if the single quotes appear at the beginning and end of the string, or as part of a {@code datetime}
     * entity) and {@code "$"} (if it appears at the start of the string). All other characters will be encoded.
     * <i>E.g.</i>
     *
     * <pre>
     *    encode("Hello_World") => Hello_World
     *    encode("'Hello World!'") => 'Hello%20World%21'
     *    encode("Harlan's World!") => Harlan%27s%20World%21
     *    encode("$variable$") => $variable%24
     *    encode("datetime'2019-02-21T22:46:11.123'") => datetime'2019-02-21T22%3A46%3A11.123'
     *    encode("guid'abcdef-ABCDEF-1234567890'") => guid'abcdef-ABCDEF-1234567890'
     * </pre>
     *
     * TODO this needs to correctly support all OData primitive data types
     * https://www.odata.org/documentation/odata-version-2-0/overview/
     *
     * @param s
     *            the string to encode
     * @return the original string with inappropriate characters encoded
     */

    /**
     * The session cookie. If unknown, will be {@code null}.
     */
    private String cookie = null;

    /**
     * The Cross-Site Request Forgery (CSRF) security token. If unknown, will be {@code null}.
     */
    private String csrfToken = null;

    /**
     * The OData entity name (as it appears in the URL), with no trailing slash.
     */
    private String entityName;

    /**
     * the case-sensitive password to use when connecting to this OData service
     */
    private String password;

    /**
     * The OData service URL, including the trailing slash.
     */
    private final String serviceRootUrl;

    /**
     * the case-sensitive username to use when connecting to this OData service
     */
    private String username;

    /**
     * @param serviceRootUrl
     *            The OData service URL, including the trailing slash.
     * @param entityName
     *            The OData entity name (as it appears in the URL), with no trailing slash. If you don't know or don't
     *            care about this value, input an empty string.
     * @param username
     *            the case-sensitive username to use when connecting to this OData service
     * @param password
     *            the case-sensitive password to use when connecting to this OData service
     */
    public ODataConnection(String serviceRootUrl, String entityName, String username, String password) {
        verify((serviceRootUrl != null) && (isLastCharacterSlash(serviceRootUrl)),
                "serviceRootUrl must not be null and must end with a '/' character");
        verify((entityName != null) && (!isLastCharacterSlash(entityName)),
                "entityName must not be null and must end with a '/' character");
        verify(username != null, "username must not be null");
        verify(password != null, "password must not be null");
        this.serviceRootUrl = serviceRootUrl;
        this.entityName = entityName;
        this.username = username;
        this.password = password;
    }

    /**
     * Adds the "Authorization" header to the given HTTP request {@code connection}. If the session cookie has already
     * been set, then does nothing.
     *
     * @param connection
     *            an HTTP request that has not been sent yet
     */
    private void addAuthorization(HttpURLConnection connection) {
        if (this.cookie == null) {
            try {
                String plainTextAuthorization = username + ":" + password;
                String encodedAuthorization = Base64.getEncoder()
                        .encodeToString(plainTextAuthorization.getBytes(CHARSET));
                connection.setRequestProperty("Authorization", "Basic " + encodedAuthorization);
            }
            catch (UnsupportedEncodingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * @param entity
     *            a local {@link ODataSerializable} instance
     * @param keyAnnotationType
     *            the type of annotation to extract from the {@code entity}.
     * @return a flat array of name-value pairs, given an {@link ODataSerializable} entity and annotation type.
     */
    private String[] getAnnotationsArray(ODataSerializable entity, Class<? extends Annotation> keyAnnotationType) {
        Map<String, String> annotations = ODataSerializable.getAnnotationsAndGetterValues(entity, keyAnnotationType,
                "'");
        List<String> parameters = new ArrayList<>();
        for (Entry<String, String> entry : annotations.entrySet()) {
            parameters.add(entry.getKey());
            parameters.add(entry.getValue());
        }
        return parameters.toArray(new String[] {});
    }

    /**
     * Creates and returns a new object of the same class as the inputed {@code entity}. Copies over the values for all
     * attributes of the same type as {@code keyAnnotationType} from the inputed {@code entity} to the newly created
     * object.
     *
     * @param entity
     *            an {@link ODataSerializable} entity for which we want a copy
     * @param keyAnnotationType
     *            identifies which attributes to copy over to the new instance
     * @return a new object of the same class as the inputed {@code entity}, with attributes identified by
     *         {@code keyAnnotationType} having their values copied over
     * @throws IOException
     *             if something goes wrong instantiating the new object or copying values
     */
    ODataSerializable getNewEntityCopy(ODataSerializable entity, Class<? extends Annotation> keyAnnotationType)
            throws IOException {

        ODataSerializable remoteCopy;
        try {
            remoteCopy = entity.getClass().newInstance();
        }
        catch (InstantiationException | IllegalAccessException e) {
            throw new IOException(e);
        }
        Map<String, String> getterValueAnnotations = ODataSerializable.getAnnotationsAndGetterValues(entity,
                keyAnnotationType, null);
        Map<String, Method> setterAnnotations = ODataSerializable.getAnnotationsAndSetters(remoteCopy.getClass(),
                keyAnnotationType, null);
        for (Entry<String, String> getterValueEntry : getterValueAnnotations.entrySet()) {
            try {
                setterAnnotations.get(getterValueEntry.getKey()).invoke(remoteCopy, getterValueEntry.getValue());
            }
            catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                throw new IOException(e);
            }
        }
        return remoteCopy;
    }

    /**
     * @param s
     *            a string to test
     * @return {@code true} if the last character in the string is a slash ({@code /})
     */
    boolean isLastCharacterSlash(String s) {
        return s.isEmpty() ? false : s.substring(s.length() - 1, s.length()).equals("/");
    }

    /**
     * @return an OData URL for fetching the list of entities. TODO for example...
     */
    public String entityRootUrl() {
        return serviceRootUrl + entityName + "/";
    }

    /**
     * @param entity
     *            the local {@link ODataSerializable} instance that we want to fetch from the server
     * @return an OData URL for fetching the entity by its ID. The entity's ID fields are identified by the
     *         {@link ODataKey} annotation. Multiple keys are allowed, in which case the keys will be identified by name
     *         and comma-separated (and are rendered in arbitrary order). String values will be surrounded by single
     *         quotes. The URL ends with a trailing slash. <i>E.g.</i> {@code https://example.com/entity(12345)/} or
     *         {@code https://example.com/entity(ATTR_B=12345,ATTR_A='xyz')/}
     */

    /**
     * The URL parameters might be rendered in any order
     *
     * @param entity
     *            the local {@link ODataSerializable} instance to use as a template for filtering server-side instances
     * @param keyAnnotationType
     *            the type of annotation to use to generate the filter. <i>e.g.</i> you could filter based on
     *            {@link ODataKey} or {@link ODataSecondaryKey}. Do not use a sub-entity type marked by
     *            {@link ODataExpand}.
     * @return the URL suffix that will filter for the given {@code entity} based on its fields annotated by
     *         {@code annotationType}. TODO for example...
     * @throws RuntimeException
     *             if {@code entity} does not contain any {@code keyAnnotationType} annotations
     */

    /**
     * <i>e.g.</i> {@code filterSuffix("ID", "'Hello'") => ?%24filter=ID%20eq%20%27Hello%27}
     *
     * @param parameterValuePairs
     *            the parameter-value pairs to filter on. For String values, they should already be surrounded by single
     *            quotes ({@code '})
     * @return the OData URL suffix for filtering entities equal to the given parameter-value pairs, will be encoded.
     *         Can be appended directly after the service URL (<i>e.g.</i> {@link #entityRootUrl()}).
     * @throws IllegalArgumentException
     *             if an uneven number of parameters are provided, or 0 parameters are provided
     */

    /**
     * Generates a URL query/parameter suffix based on an {@link ODataSerializable} instance. The entity's selected
     * attribute values will be rendered as URL query parameters. For example, given the following Entity:
     *
     * <pre>
     * public class Entity implements ODataSerializable {
     *     ...
     *     &#64;ODataValue("EntityId")
     *     private int id = 413286;
     *
     *     &#64;ODataValue("EntityName")
     *     private String name = "Hello World";
     *     ...
     * }
     * </pre>
     *
     * Here is an example call and return value:
     *
     * <pre>
     * parameterSuffix(new Entity(), @ODataValue.class) => ?EntityId=413286&amp;EntityName=%27Hello%20World%27
     * </pre>
     *
     * The attributes might be rendered in the URL in any arbitrary order
     *
     * @param entity
     *            the local {@link ODataSerializable} instance to use as a template for generating URL parameters
     * @param keyAnnotationType
     *            the type of annotation to use to generate the URL parameters. <i>e.g.</i> you could filter based on
     *            {@link ODataKey} or {@link ODataValue}.
     * @return a URL suffix that includes all of the annotated attribute-value pairs
     * @throws RuntimeException
     *             if {@code entity} does not contain any {@code keyAnnotationType} annotations
     */

    /**
     * Given a collection of parameter-value pairs, generates an appropriate URL query/parameter suffix. This suffix can
     * be appended to a service url, <i>e.g.</i> {@link #entityRootUrl()}. Parameters and their values will be encoded
     * so that they are valid for use in a URL. <i>e.g.</i>
     *
     * <pre>
     * parameterSuffix("param1", "1", "param 2", "'Hello World'") => ?param1=1&amp;param%202='Hello%20World'
     * </pre>
     *
     * @param parameters
     *            unencoded parameters and values, provided in matched pairs
     * @return a URL query/parameter suffix that matches the given parameters, or an empty string if no parameters are
     *         inputed.
     * @throws IllegalArgumentException
     *             if an uneven number of parameters are provided
     */

    /**
     * Sends an HTTP change request (that modifies entities on the server) to the specified {@code url}. Will handle the
     * authorization and session management.
     *
     * @param url
     *            the URL to send the request to
     * @param requestMethod
     *            the HTTP request verb to use (<i>e.g.</i> {@code POST} or {@code PUT})
     * @param payload
     *            the data to send in the body of the request, or set to {@code null} if there is no payload to send.
     * @param etagHeader
     *            the {@code etag} header for the entity, that indicates the last time the entity was modified. Set to
     *            {@code null} if unknown, in which case the data will be fetched. This value is not used for
     *            {@code POST} requests and can be safely set to {@code null}. For non-{@code POST} requests, if you
     *            don't want to fetch an {@code etag}, then enter the string {@code "_NO_ETAG_"}
     * @return the {@link Response} object that represents a completed request
     * @throws IOException
     *             if something goes wrong communicating with the remote server
     */
    public Response sendChangeRequest(String url, String requestMethod, String payload, String etagHeader)
            throws IOException {

        // refresh the session tokens
        boolean needEtag = (requestMethod.equals("POST") == false) && (!"_NO_ETAG_".equals(etagHeader));
        if ((this.csrfToken == null) || (this.cookie == null) || (needEtag && (etagHeader == null))) {
            String fetchTokenAndCookieUrl = (needEtag) ? url : serviceRootUrl;
            Response response = sendGetRequest(fetchTokenAndCookieUrl);
            etagHeader = response.getEtagHeader();

            if (response.getResponseCode() != 200) {
                throw new IOException("Failed on GET request to refresh session tokens: " + response.getErrorMessage()
                        + "\nurl: " + url + "\npayload: " + payload);
            }
            else if (needEtag && (etagHeader == null)) {
                throw new IOException("Failed to obtain etag: " + response.getErrorMessage() + "\nurl: " + url
                        + "\npayload: " + payload);
            }
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod(requestMethod);

        addAuthorization(connection);

        connection.setRequestProperty("Accept", "application/xml");
        connection.setRequestProperty("Accept-Charset", CHARSET);
        connection.setRequestProperty("DataServiceVersion", "2.0");
        connection.setRequestProperty("MaxDataServiceVersion", "2.0");
        connection.setRequestProperty("sap-cancel-on-close", "true");
        connection.setRequestProperty("cache-control", "no-cache");
        connection.setRequestProperty("x-csrf-token", this.csrfToken);
        connection.setRequestProperty("cookie", this.cookie);

        if (needEtag) {
            connection.setRequestProperty("If-Match", etagHeader);
        }

        if (payload != null) {
            byte[] postData = payload.getBytes(CHARSET);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Content-Length", Integer.toString(postData.length));
            connection.setDoOutput(true);
            try (DataOutputStream stream = new DataOutputStream(connection.getOutputStream())) {
                stream.write(postData);
            }
        }

        connection.connect();
        return new Response(connection, payload);
    }

    /**
     * Sends an HTTP GET request to the server to counts the number of {@link ODataSerializable} instances it has (based
     * on the given filters)
     *
     * @param filterSuffix
     *            the URL suffix to use to filter the number of entities. You could use
     *            {@link ODataConnection#filterSuffix(ODataSerializable, Class)} to obtain this value.
     * @return the number of entity instances that match the given filter on the server
     * @throws IOException
     *             if something goes wrong communicating with the remote server
     */
    public int sendCountRequest(String filterSuffix) throws IOException {
        String url = entityRootUrl() + COUNT + filterSuffix;
        Response response = sendGetRequest(url);
        if (response.getResponseCode() == 200) {
            return Integer.parseInt(response.getBody());
        }
        throw new IOException(HTTP_REQUEST_FAILED_WITH_RESPONSE_CODE + response.getErrorMessage() + "\nurl: " + url);
    }

    /**
     * Sends an HTTP POST request to the server to create a new instance of the {@link ODataSerializable} on the server
     *
     * @param entity
     *            the local {@link ODataSerializable} instance for which we'd like to create an identical server-side
     *            instance
     * @param annotationType
     *            all fields annotated with this will be serialized in the request. This annotation type should normally
     *            be {@link ODataValue}.
     * @return the {@link Response} object that represents a completed request
     * @throws IOException
     *             if something goes wrong communicating with the remote server
     */
    public Response sendCreateRequest(ODataSerializable entity, Class<? extends Annotation> annotationType)
            throws IOException {
        String url = entityRootUrl();
        String payload = entity.toJson(annotationType);
        Response response = sendChangeRequest(url, "POST", payload, null);
        if (response.getResponseCode() != 201) {
            throw new IOException(HTTP_REQUEST_FAILED_WITH_RESPONSE_CODE + response.getErrorMessage() + "\nurl: " + url
                    + "\npayload: " + payload);
        }
        return response;
    }

    /**
     * Sends an HTTP DELETE request to the server to delete the given {@code ODataSerializable}.
     *
     * @param entity
     *            the local {@link ODataSerializable} instance that identifies the server-side instance to be deleted
     * @param etagHeader
     *            the {@code etag} header for the server-side entity instance, that indicates the last time the entity
     *            was modified. Set to {@code null} if unknown, in which case the data will be fetched.
     * @return an HTTP {@link Response} object representing the server's response
     * @throws IOException
     *             if something went wrong communicating with the server
     */

    /**
     * Sends an HTTP GET request to the server for a particular {@link ODataSerializable}'s properties.
     *
     * @param entity
     *            the local {@link ODataSerializable} instance that identifies the server-side instance whose properties
     *            we wish to fetch. This local object will be modified.
     * @param annotationType
     *            all fields annotated with this will be de-serialized from the response. This annotation type should
     *            normally be {@link ODataValue}.
     * @param expand
     *            if {@code true} then entity properties that are a list of {@link ODataSerializable} entities and
     *            identified by the annotation {@link ODataExpand} will also be fetched from the server
     * @return an HTTP {@link Response} object representing the server's response
     * @throws IOException
     *             if something went wrong communicating with the server
     */

    /**
     * Sends an HTTP GET request to the server for a particular {@link ODataSerializable}'s properties.
     *
     * @param entity
     *            the local {@link ODataSerializable} instance that identifies the server-side instance whose properties
     *            we wish to fetch. This local object will be modified.
     * @param annotationType
     *            all fields annotated with this will be de-serialized from the response. This annotation type should
     *            normally be {@link ODataValue}.
     * @param keyAnnotationType
     *            the type of annotation to use to uniquely identify the server-side entity. <i>e.g.</i> you could
     *            filter based on {@link ODataKey} or {@link ODataSecondaryKey}. When using the {@link ODataKey}
     *            annotation, the OData request will include this entity ID as part of the path (<i>e.g.</i>
     *            {@code ../Entity(ID)/}). When using a different annotation, the attribute-value pairs will be added as
     *            a filter to the URL parameters (<i>e.g.</i> {@code ../Entity/?$filter=param1 eq value1}).
     * @param expand
     *            if {@code true} then entity properties that are a list of {@link ODataSerializable} entities and
     *            identified by the annotation {@link ODataExpand} will also be fetched from the server
     * @return an HTTP {@link Response} object representing the server's response
     * @throws IOException
     *             if something went wrong communicating with the server
     */

    /**
     * Sends an HTTP GET request to the server to see if a particular server-side {@link ODataSerializable} exists.
     *
     * @param entity
     *            the local {@link ODataSerializable} instance that identifies the server-side instance whose existence
     *            we wish to verify
     * @param keyAnnotationType
     *            the type of annotation to use to uniquely identify the server-side entity. <i>e.g.</i> you could
     *            filter based on {@link ODataKey} or {@link ODataSecondaryKey}.
     * @return {@code true} if the {@link ODataSerializable} instance exists on the server
     * @throws IOException
     *             if something went wrong communicating with the server
     */

    /**
     * Sends an HTTP GET request to the server to fetch an expandable list of child entities that belong to the
     * {@code parentEntity}. Expandable properties are a list of type {@link ODataSerializable} and the property in the
     * {@code parentEntity} is identified by the {@link ODataExpand} annotation.
     *
     * @param parentEntity
     *            the local {@link ODataSerializable} instance that identifies the server-side instance whose property
     *            we wish to expand by fetching a list of sub-entities
     * @param expandSuffix
     *            the OData suffix added to the URL to fetch the sub-entities (excluding trailing slash). This should
     *            usually be provided by the {@link ODataExpand} annotation.
     * @param childEntityType
     *            the type of the child whose instances we are fetching from the server. This should usually be provided
     *            by the {@link ODataExpand} annotation.
     * @return a list of child sub-entities
     * @throws IOException
     *             if something went wrong communicating with the server
     */

    /**
     * Sends an HTTP GET request to the server for a remote copy of the given entity, without modifying the given
     * entity.
     *
     * @param entity
     *            the local {@link ODataSerializable} instance that identifies the server-side instance whose properties
     *            we wish to fetch. This local object will NOT be modified.
     * @param annotationType
     *            all fields annotated with this will be de-serialized from the response. This annotation type should
     *            normally be {@link ODataValue}.
     * @param keyAnnotationType
     *            the type of annotation to use to uniquely identify the server-side entity. <i>e.g.</i> you could
     *            filter based on {@link ODataKey} or {@link ODataSecondaryKey}.
     * @param expand
     *            if {@code true} then entity properties that are a list of {@link ODataSerializable} entities and
     *            identified by the annotation {@link ODataExpand} will also be fetched from the server
     * @throws IOException
     *             if something went wrong communicating with the server
     *
     * @return a new local copy of the remote entity
     */


    /**
     * Sends an HTTP GET request to the server to fetch all {@link ODataSerializable} instances of the given
     * {@code entityType}.
     *
     * @param entityType
     *            the type of {@link ODataSerializable} entity that we wish to fetch from the server
     * @param annotationType
     *            all fields annotated with this will be de-serialized from the response. This annotation type should
     *            normally be {@link ODataValue}.
     * @param expand
     *            if {@code true} then entity properties that are a list of {@link ODataSerializable} entities and
     *            identified by the annotation {@link ODataExpand} will also be fetched from the server
     * @return the list of entities fetched from the server
     * @throws IOException
     *             if something went wrong communicating with the server
     */
//    public List<? extends ODataSerializable> sendGetAllRequest(Class<? extends ODataSerializable> entityType,
//            Class<? extends Annotation> annotationType, boolean expand) throws IOException {
//        // TODO implement expand
//        String url = entityRootUrl();
//        Response response = sendGetRequest(url);
//        if (response.getResponseCode() == 200) {
//            try {
//                return ODataSerializable.fromXml(entityType, response.getDocument(), annotationType);
//            }
//            catch (DeserializationException e) {
//                throw new IOException(e.setUrl(url).setLocalEntity(entityType));
//            }
//        }
//        throw new IOException(HTTP_REQUEST_FAILED_WITH_RESPONSE_CODE + response.getErrorMessage() + "\nurl: " + url);
//    }

    /**
     * Sends an HTTP GET request to the specified {@code url}. Will handle the authorization and session management.
     *
     * @param url
     *            the URL to fetch
     * @return the {@link Response} object that represents a completed request
     * @throws IOException
     *             if something goes wrong communicating with the remote server
     */
    public Response sendGetRequest(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        addAuthorization(connection);

        connection.setRequestMethod("GET");
        connection.setRequestProperty("Accept", "application/xml");
        connection.setRequestProperty("Accept-Charset", ODataConnection.CHARSET);
        connection.setRequestProperty("x-csrf-token", "Fetch");
        if (this.cookie != null) {
            connection.setRequestProperty("Cookie", this.cookie);
        }
        connection.connect();

        this.csrfToken = connection.getHeaderField("x-csrf-token");
        if (this.csrfToken == null) {
            throw new IOException("Failed to obtain x-csrf-token" + "\nurl: " + url);
        }
        
        String cookie = connection.getHeaderField("Set-Cookie");
        if ((cookie != null) && (cookie.contains("SAP_SESSIONID"))) {
            this.cookie = cookie;
        }
        if (this.cookie == null) {
            throw new IOException("Failed to obtain session cookie" + "\nurl: " + url);
        }

        return new Response(connection);
    }

    /**
     * Sends an HTTP PUT request to the server to update the given {@link ODataSerializable} instance with the local
     * values.
     *
     * @param entity
     *            the local {@link ODataSerializable} instance that contains the data we want reflected on the server
     * @param annotationType
     *            all fields annotated with this will be serialized in the request. This annotation type should normally
     *            be {@link ODataValue}.
     * @param etagHeader
     *            the {@code etag} header for the server-side entity instance, that indicates the last time the entity
     *            was modified. Set to {@code null} if unknown, in which case the data will be fetched.
     * @return an HTTP {@link Response} object representing the server's response
     * @throws IOException
     *             if something went wrong communicating with the server
     */
//    public Response sendUpdateRequest(ODataSerializable entity, Class<? extends Annotation> annotationType,
//            String etagHeader) throws IOException {
//        String url = entityRootUrlWithId(entity);
//        String payload = entity.toJson(annotationType);
//        Response response = sendChangeRequest(url, "PUT", payload, etagHeader);
//        if (response.getResponseCode() != 204) {
//            throw new IOException(HTTP_REQUEST_FAILED_WITH_RESPONSE_CODE + response.getErrorMessage() + "\nurl: " + url
//                    + "\npayload: " + payload);
//        }
//        return response;
//    }

    /**
     * @param cookie
     *            the session cookie
     */
    public void setCookie(String cookie) {
        this.cookie = cookie;
    }

    /**
     * @param entityName
     *            The OData entity name (as it appears in the URL), with no trailing slash.
     */
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
}
