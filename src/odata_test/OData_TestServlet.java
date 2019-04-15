package odata_test;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import odata_import.ODataConnection;
import odata_import.Response;

/**
 * Servlet implementation class OData_TestServlet
 */
@WebServlet("/")
public class OData_TestServlet extends HttpServlet {
	private static final long serialVersionUID = 1L;
       
    /**
     * @see HttpServlet#HttpServlet()
     */
    public OData_TestServlet() {
        super();
        // TODO Auto-generated constructor stub
    }

	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

		
		response.getWriter().println("This is the OData Test");

		// Create connection
		ODataConnection con = new ODataConnection("https://pt6-001-api.wdf.sap.corp/sap/opu/odata/IBP/EXTRACT_ODATA_SRV/", "", "ODATA_USER_TEST" , "Welcome1!");
		
		// Request data from SAP IBP
		Response res = con.sendGetRequest("https://pt6-001-api.wdf.sap.corp/sap/opu/odata/IBP/EXTRACT_ODATA_SRV/$metadata");
		
		// Print response message to ensure success or view error
		response.getWriter().println(res);
		
		// Get data returned from request
		String data = res.getBody();
		
		// Print data returned from request
		response.getWriter().println(data);
		
		
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doGet(request, response);
	}

}
	
