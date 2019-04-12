package odata_import;

/**
 * Notifies the caller that the server OData entity does not have the requested property.
 * <p>
 * We made a new exception so that the caller can add context before re-throwing the exception.
 *
 * @author Jonathan Benn
 */
public class DeserializationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public static final String DESERIALIZATION_MISMATCH = "Deserialization mismatch: the server entity does not have property: ";
    public static final String LOCAL_TYPE = "\nlocal type: ";
    public static final String URL = "\nurl: ";

    /**
     * The local {@link ODataSerializable} type whose properties we are trying to fill
     */
    private String localEntity = null;

    /**
     * The name of the remote OData entity property that was requested
     */
    private String missingProperty = null;

    /**
     * The server URL that was requested
     */
    private String url = null;

    /**
     * @return The local {@link ODataSerializable} type whose properties we are trying to fill
     */
    public String getLocalEntity() {
        return localEntity;
    }

    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder(DESERIALIZATION_MISMATCH + this.missingProperty);
        if (this.localEntity != null) {
            sb.append(LOCAL_TYPE + this.localEntity);
        }
        if (this.url != null) {
            sb.append(URL + this.url);
        }
        return sb.toString();
    }

    /**
     * @return The name of the remote OData entity property that was requested
     */
    public String getMissingProperty() {
        return missingProperty;
    }

    /**
     * @return The server URL that was requested
     */
    public String getUrl() {
        return url;
    }

    /**
     * @param localEntity
     *            The local {@link ODataSerializable} type whose properties we are trying to fill
     * @return this object (for function call chaining)
     */
    public DeserializationException setLocalEntity(Class<? extends ODataSerializable> localEntity) {
        this.localEntity = localEntity.getSimpleName();
        return this;
    }

    /**
     * @param missingProperty
     *            The name of the remote OData entity property that was requested
     * @return this object (for function call chaining)
     */
    public DeserializationException setMissingProperty(String missingProperty) {
        this.missingProperty = missingProperty;
        return this;
    }

    /**
     * @param url
     *            The server URL that was requested
     * @return this object (for function call chaining)
     */
    public DeserializationException setUrl(String url) {
        this.url = url;
        return this;
    }
}
