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
		// TODO Auto-generated method stub
		response.getWriter().println("This is the OData Test");

		ODataConnection con = new ODataConnection("https://pt6-001-api.wdf.sap.corp/sap/opu/odata/IBP/EXTRACT_ODATA_SRV/", "", "EXT_SCHEDULER_USER", "Welcome1!");
		
		response.getWriter().println(con);
		
		Response res = con.sendGetRequest("https://pt6-001-api.wdf.sap.corp/sap/opu/odata/IBP/EXTRACT_ODATA_SRV/$metadata");
		
		
		
     		// Test with OData Northwind Database
// 		ODataConnection con = new ODataConnection("https://services.odata.org/V3/Northwind/Northwind.svc/", "", "", "");
		
// 		response.getWriter().println(con);
		
// 		Response res = con.sendGetRequest("https://services.odata.org/V3/Northwind/Northwind.svc/Customers");
		
// 		response.getWriter().println(res);
		
		
	}

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	 */
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		// TODO Auto-generated method stub
		doGet(request, response);
	}

}
