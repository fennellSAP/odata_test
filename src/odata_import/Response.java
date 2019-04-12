package odata_import;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;

/**
 * A wrapper around the HTTP response (Adapter Pattern), which offers convenience functions and allows us to override
 * the response during testing. If there is an HTTP response body then you can use <i>either</i> {@link #getBody()} for
 * the raw text, <i>or</i> {@link #getDocument()} for the parsed XML, but not both.
 *
 * @author Jonathan Benn
 */
public class Response {

    /**
     * The character set used by the server
     */
    static final String CHARSET = StandardCharsets.UTF_8.name();

    /**
     * Converts the given XML document into a {@link String}.
     *
     * @param document
     *            an XML document to convert into a {@link String}
     * @return the String version of the given XML {@code document}
     */
    public static String xmlToString(Document document) {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        }
        catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * The body of the HTTP response. If there was no response body, or if the body was parsed as an XML document, then
     * this value will be {@code null}.
     */
    private String body = null;

    /**
     * The {@link HttpURLConnection} used to build this {@link Response}. In testing cases this value may be
     * {@code null}.
     */
    private HttpURLConnection connection = null;

    /**
     * The body of the HTTP response, parsed as an XML Document. If there was no response body, or if the body was not
     * parsed as XML, then this will be {@code null}.
     */
    private Document document = null;

    /**
     * The value in the response's {@code etag} header. If the header does not exist, will default to {@code null}
     * because that is the failure return value from {@link HttpURLConnection#getHeaderField(String)}
     */
    private String etagHeader = null;

    /**
     * The value in the response's {@code Location} header. If the header does not exist, will default to {@code null}
     * because that is the failure return value from {@link HttpURLConnection#getHeaderField(String)}
     */
    private String locationHeader = null;

    /**
     * The payload that was sent in the corresponding request. Will default to {@code null} if no payload was sent.
     */
    private String payload;

    /**
     * The HTTP response code, <i>e.g.</i> {@code 200} or {@code 404}.
     */
    private int responseCode;

    /**
     * The HTTP response message, <i>e.g.</i> {@code OK} or {@code Not Found}.
     */
    private String responseMessage;

    /**
     * The value in the response's {@code sap-message} header. If the header does not exist, will default to
     * {@code null} because that is the failure return value from {@link HttpURLConnection#getHeaderField(String)}
     */
    private String sapMessageHeader;

    /**
     * The URL the corresponding request was sent to
     */
    private URL url;

    /**
     * Generates a {@link Response} object based on the given {@code connection}. Will also obtain other information
     * like the response body and certain headers. If requested, by calling {@link #getDocument()}, parses the body of
     * the response as an XML document.
     * <p>
     * Will assume that there was no payload (<i>i.e.</i> {@link #getRequestPayload()} will return {@code null}). If
     * there was a payload, then use constructor {@link #Response(HttpURLConnection, String)}.
     *
     * @param connection
     *            the HTTP connection, which is assumed to be the result of a <u><b>sent</b></u> request
     * @throws IOException
     *             if something went wrong with the HTTP connection or parsing the XML document
     */
    public Response(HttpURLConnection connection) throws IOException {
        this(connection, null);
    }

    /**
     * Generates a {@link Response} object based on the given {@code connection}. Will also obtain other information
     * like the response body and certain headers. If requested, by calling {@link #getDocument()}, parses the body of
     * the response as an XML document.
     *
     * @param connection
     *            the HTTP connection, which is assumed to be the result of a <u><b>sent</b></u> request
     * @param payload
     *            the payload that was sent in the corresponding request. Input {@code null} if no payload was sent.
     * @throws IOException
     *             if something went wrong with the HTTP connection or parsing the XML document
     */
    public Response(HttpURLConnection connection, String payload) throws IOException {
        this.connection = connection;
        this.responseCode = connection.getResponseCode();
        this.responseMessage = connection.getResponseMessage();
        this.etagHeader = connection.getHeaderField("etag");
        this.locationHeader = connection.getHeaderField("Location");
        this.sapMessageHeader = connection.getHeaderField("sap-message");
        this.url = connection.getURL();
        this.payload = payload;
    }

    /**
     * For testing only, use {@link #Response(HttpURLConnection)} in production code.
     *
     * @param responseCode
     *            the HTTP response code, <i>e.g.</i> {@code 200 OK} or {@code 404 Not Found}
     */
    public Response(int responseCode) {
        this.responseCode = responseCode;
        this.body = "";
    }

    /**
     * For testing only, use {@link #Response(HttpURLConnection)} in production code.
     *
     * @param responseCode
     *            the HTTP response code, <i>e.g.</i> {@code 200 OK} or {@code 404 Not Found}
     * @param document
     *            the body of the HTTP response, parsed as an XML Document.
     */
    public Response(int responseCode, Document document) {
        this.responseCode = responseCode;
        this.document = document;
    }

    /**
     * For testing only, use {@link #Response(HttpURLConnection)} in production code.
     *
     * @param responseCode
     *            the HTTP response code, <i>e.g.</i> {@code 200 OK} or {@code 404 Not Found}
     * @param locationHeader
     *            the content of the {@code Location} response header
     */
    public Response(int responseCode, String locationHeader) {
        this.responseCode = responseCode;
        this.locationHeader = locationHeader;
        this.body = "";
    }

    /**
     * For testing only, use {@link #Response(HttpURLConnection)} in production code.
     *
     * @param responseCode
     *            the HTTP response code, <i>e.g.</i> {@code 200 OK} or {@code 404 Not Found}
     * @param locationHeader
     *            the content of the {@code Location} response header, or {@code null} if it does not exist
     * @param etagHeader
     *            the content of the {@code etag} response header, or {@code null} if it does not exist
     */
    public Response(int responseCode, String locationHeader, String etagHeader) {
        this.responseCode = responseCode;
        this.locationHeader = locationHeader;
        this.etagHeader = etagHeader;
        this.body = "";
    }

    /**
     * For testing only, use {@link #Response(HttpURLConnection)} in production code.
     *
     * @param body
     *            the body of the HTTP response.
     * @param responseCode
     *            the HTTP response code, <i>e.g.</i> {@code 200 OK} or {@code 404 Not Found}
     */
    public Response(String body, int responseCode) {
        this.responseCode = responseCode;
        this.body = body;
    }

    /**
     * This function is necessary because HttpUrlConnection uses two different streams: either the input stream for the
     * normal case, or the error stream for the error case. It's always one or the other.
     *
     * @return the currently valid stream for reading the HTTP response body
     * @throws IOException
     *             if something went wrong obtaining the stream
     */
    private InputStream getStream() throws IOException {
        if (this.connection.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST) {
            return this.connection.getInputStream();
        }
        else {
            return this.connection.getErrorStream();
        }
    }

    /**
     * @return the body of the HTTP response. If there was no response body, or if the body was parsed as an XML
     *         document, then will return {@code null}.
     * @throws IOException
     *             if something went wrong reading the HTTP response body
     */
    public String getBody() throws IOException {

        if ((this.body == null) && (this.document == null)) {
            try {
                try (Scanner scanner = new Scanner(getStream(), CHARSET)) {
                    scanner.useDelimiter("\\A");
                    this.body = scanner.hasNext() ? scanner.next() : "";
                }
            }
            catch (Throwable e) {
                throw new IOException(getErrorMessage(), e);
            }
        }

        return this.body;
    }

    /**
     * @return the body of the HTTP response, parsed as an XML {@link Document}. If there was no response body, or if
     *         the body was not parsed as XML, then will return {@code null}.
     * @throws IOException
     *             if something went wrong parsing the XML
     */
    public Document getDocument() throws IOException {

        if ((this.body == null) && (this.document == null)) {
            try {
                this.document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(getStream());
            }
            catch (Throwable e) {
                throw new IOException(getErrorMessage(), e);
            }
        }

        return document;
    }

    /**
     * @return the body of the HTTP response, parsed as an XML {@link String}. If there was no response body, or if the
     *         body was not parsed as XML, then will return {@code null}.
     * @throws IOException
     *             if something went wrong parsing the XML
     */
    public String getDocumentAsString() throws IOException {
        Document document = getDocument();
        return (document != null) ? xmlToString(document) : null;
    }

    /**
     * @return the HTTP response code and message, plus the error message the server sent (if possible)
     */
    public String getErrorMessage() {
        String errorMessage = "";
        try {
            errorMessage = ": " + getDocument().getElementsByTagName("message").item(0).getTextContent();
        }
        catch (Throwable t) {}
        return (getResponseCodeAndMessage() + errorMessage);
    }

    /**
     * @return the value in the response's {@code etag} header. If the header does not exist then will return
     *         {@code null}.
     */
    public String getEtagHeader() {
        return etagHeader;
    }

    /**
     * @return the value in the response's {@code Location} header. If the header does not exist then will return
     *         {@code null}.
     */
    public String getLocationHeader() {
        return this.locationHeader;
    }

    /**
     * @return the payload that was sent in the corresponding request. Will return {@code null} if no payload was sent.
     */
    public String getRequestPayload() {
        return payload;
    }

    /**
     * @return the URL the corresponding request was sent to
     */
    public URL getRequestUrl() {
        return url;
    }

    /**
     * @return the HTTP response code, <i>e.g.</i> {@code 200} or {@code 404}.
     */
    public int getResponseCode() {
        return this.responseCode;
    }

    /**
     * @return the HTTP response code and message, <i>e.g.</i> {@code 200 OK} or {@code 404 Not Found}. If
     *         {@link #getResponseMessage} would return {@code null}, then will only return the response code.
     */
    public String getResponseCodeAndMessage() {
        return this.responseCode + ((this.responseMessage != null) ? " " + this.responseMessage : "");
    }

    /**
     * @return the HTTP response message, <i>e.g.</i> {@code OK} or {@code Not Found}.
     */
    public String getResponseMessage() {
        return responseMessage;
    }

    /**
     *
     * @return the value in the response's {@code sap-message} header. If the header does not exist then will return
     *         {@code null}.
     */
    public String getSapMessageHeader() {
        return sapMessageHeader;
    }

    @Override
    public String toString() {
        return "Response[url=" + url + ", response=" + getResponseCodeAndMessage() + "]";
    }
}
