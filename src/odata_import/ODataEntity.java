package odata_import;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Extending this class will provide all the methods an entity needs to contact an OData service, for handling
 * CReate-Update-Delete (CRUD) operations.
 *
 * @author Jonathan Benn
 */
public class ODataEntity implements ODataSerializable {

    /**
     * @see ODataConnection
     */
    private static Map<Class<? extends ODataEntity>, ODataConnection> connections = new HashMap<>();

    /**
     * Verifies that the data contained in the inputed list exists on the server. If it does not, then modifies the
     * server data to fit the inputed data.
     *
     * @param entities
     *            data to verify on the server
     * @throws IOException
     *             if something goes wrong communicating with the server
     */
    public static void require(List<? extends ODataEntity> entities) throws IOException {
        for (ODataEntity entity : entities) {
            entity.require();
        }
    }

    /**
     * @param cookie
     *            the session cookie
     */
    public static void setCookie(String cookie) {
        ODataEntity.cookie = cookie;
        for (ODataConnection connection : connections.values()) {
            connection.setCookie(cookie);
        }
    }

    /**
     * The session cookie
     */
    private static String cookie = null;

    /**
     * @param serviceRootUrl
     *            The OData service URL, including the trailing slash.
     * @param entityName
     *            The OData entity name (as it appears in the URL), with no trailing slash.
     * @param username
     *            the case-sensitive username to use when connecting to this OData service
     * @param password
     *            the case-sensitive password to use when connecting to this OData service
     */
    public ODataEntity(String serviceRootUrl, String entityName, String username, String password) {
        if (connections.containsKey(this.getClass()) == false) {
            ODataConnection connection = new ODataConnection(serviceRootUrl, entityName, username, password);
            if (cookie != null) {
                connection.setCookie(cookie);
            }
            connections.put(this.getClass(), connection);
        }
    }

    /**
     * @return the {@link ODataConnection} appropriate to this entity
     */
    protected ODataConnection getConnection() {
        return connections.get(this.getClass());
    }

    /**
     * Sends a request to the server to create a new remote entity with the same information as this local object. You
     * should specify relevant non-key attributes before calling this method.
     *
     * @throws IOException
     *             if something went wrong contacting the server
     */
    public void create() throws IOException {
        getConnection().sendCreateRequest(this, ODataValue.class);
    }

    /**
     * Requests for the server to delete the remote copy of the entity identified by this object.
     *
     * @throws IOException
     *             if something went wrong contacting the server
     */
    public void delete() throws IOException {
        getConnection().sendDeleteRequest(this, null);
    }

    /**
     * Fetches data from the remote copy of this entity from the server and updates this local object. Will replace any
     * existing data in this object with what's fetched from the server.
     *
     * @throws IOException
     *             if something went wrong contacting the server
     */
    public void display() throws IOException {
        getConnection().sendDisplayRequest(this, ODataValue.class, true);
    }

    /**
     * Sends a request to the server to know if this entity exists, based on the unique key attributes. Non-key
     * attributes are not verified.
     *
     * @return {@code true} if this entity exists on the server
     * @throws IOException
     *             if something went wrong fetching the data from the server
     */
    public boolean exists() throws IOException {
        return getConnection().sendExistsRequest(this, ODataKey.class);
    }

    /**
     * Fetches data from the remote copy of this entity from the server and returns it as a new local object, leaving
     * this local object unaffected.
     *
     * @return a new entity instance identical to what is on the server
     * @throws IOException
     *             if something went wrong contacting the server
     */
    public ODataSerializable fetchRemoteCopy() throws IOException {
        return getConnection().sendFetchRemoteCopyRequest(this, ODataValue.class, ODataKey.class, true);
    }

    /**
     * Fetches all the entities of this type from the server. Will not expand expandable properties. Does not affect
     * this object.
     *
     * @return all the entities of this type currently on the server
     * @throws IOException
     *             if something goes wrong communicating with the server
     */
    public List<? extends ODataSerializable> getAll() throws IOException {
        return getConnection().sendGetAllRequest(this.getClass(), ODataValue.class, false);
    }

    /**
     * Sends requests to the server to ensure that the server has an entity identical to this one, but only makes
     * modifications if necessary.
     *
     * @throws IOException
     *             if something goes wrong communicating with the server
     */
    public void require() throws IOException {

        // if this entity exists on the server
        if (this.exists()) {
            // if the local and remote versions are different
            if (this.equals(this.fetchRemoteCopy()) == false) {
                // then update the copy on the server
                this.update();
            }
            // else if the remote version is identical, then do nothing
        }
        // else if this entity does not exist on the remote server
        else {
            // then create it on the remote server!
            this.create();
        }
    }

    /**
     * Requests for the server to update the remote copy of this entity with the current local values held inside of
     * this object.
     *
     * @throws IOException
     *             if something went wrong contacting the server
     */
    public void update() throws IOException {
        getConnection().sendUpdateRequest(this, ODataValue.class, null);
    }
}
