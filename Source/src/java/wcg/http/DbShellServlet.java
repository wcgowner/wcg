/*
 * Copyright © 2013-2016 The Nxt Core Developers.
 * Copyright © 2016 Jelurida IP B.V.
 *
 * See the LICENSE.txt file at the top-level directory of this distribution
 * for licensing information.
 *
 * Unless otherwise agreed in a custom licensing agreement with Jelurida B.V.,
 * no part of the Nxt software, including this file, may be copied, modified,
 * propagated, or distributed except according to the terms contained in the
 * LICENSE.txt file.
 *
 * Removal or modification of this copyright notice is prohibited.
 *
 */

package wcg.http;

import wcg.Db;
import wcg.util.Convert;
import wcg.util.JSON;
import org.h2.tools.Shell;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.JSONArray;
//import org.json.simple.JSONException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.sql.Statement;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.sql.SQLException;

public final class DbShellServlet extends HttpServlet {

    private static final String header =
            "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "<head>\n" +
                    "    <meta charset=\"UTF-8\"/>\n" +
                    "    <title>Wcg H2 Database Shell</title>\n" +
                    "    <script type=\"text/javascript\">\n" +
                    "        function submitForm(form, adminPassword) {\n" +
                    "            var url = '/dbshell';\n" +
                    "            var params = '';\n" +
                    "            for (i = 0; i < form.elements.length; i++) {\n" +
                    "                if (! form.elements[i].name) {\n" +
                    "                    continue;\n" +
                    "                }\n" +
                    "                if (i > 0) {\n" +
                    "                    params += '&';\n" +
                    "                }\n" +
                    "                params += encodeURIComponent(form.elements[i].name);\n" +
                    "                params += '=';\n" +
                    "                params += encodeURIComponent(form.elements[i].value);\n" +
                    "            }\n" +
                    "            if (adminPassword && form.elements.length > 0) {\n" +
                    "                params += '&adminPassword=' + adminPassword;\n" +
                    "            }\n" +
                    "            var request = new XMLHttpRequest();\n" +
                    "            request.open(\"POST\", url, false);\n" +
                    "            request.setRequestHeader(\"Content-type\", \"application/x-www-form-urlencoded\");\n" +
                    "            request.send(params);\n" +
                    "            form.getElementsByClassName(\"result\")[0].textContent += request.responseText;\n" +
                    "            return false;\n" +
                    "        }\n" +
                    "    </script>\n" +
                    "</head>\n" +
                    "<body>\n";

    private static final String footer =
                    "</body>\n" +
                    "</html>\n";

    private static final String form =
            "<form action=\"/dbshell\" method=\"POST\" onsubmit=\"return submitForm(this" +
                    (API.disableAdminPassword ? "" : ", '{adminPassword}'") + ");\">" +
                    "<table class=\"table\" style=\"width:90%;\">" +
                    "<tr><td><pre class=\"result\" style=\"float:top;width:90%;\">" +
                    "This is a database shell. Enter SQL to be evaluated, or \"help\" for help:" +
                    "</pre></td></tr>" +
                    "<tr><td><b>&gt;</b> <input type=\"text\" name=\"line\" style=\"width:90%;\"/></td></tr>" +
                    "</table>" +
                    "</form>";

    private static final String errorNoPasswordIsConfigured =
            "This page is password-protected, but no password is configured in wcg.properties. " +
                    "Please set wcg.adminPassword or disable the password protection with wcg.disableAdminPassword";

    private static final String passwordFormTemplate =
            "<form action=\"/dbshell\" method=\"POST\">" +
                    "<table class=\"table\">" +
                    "<tr><td colspan=\"3\">%s</td></tr>" +
                    "<tr>" +
                    "<td>Password:</td>" +
                    "<td><input type=\"password\" name=\"adminPassword\"/>" +
                    "<input type=\"submit\" value=\"Go!\"/></td>" +
                    "</tr>" +
                    "</table>" +
                    "<input type=\"hidden\" name=\"showShell\" value=\"true\"/>" +
                    "</form>";

    private static final String passwordForm = String.format(passwordFormTemplate,
            "<p>This page is password-protected. Please enter the administrator's password</p>");


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        if (! API.isAllowed(req.getRemoteHost())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String body;
        if (API.disableAdminPassword) {
            body = form;
        } else {
            if (API.adminPassword.isEmpty()) {
                body = errorNoPasswordIsConfigured;
            } else {
                body = passwordForm;
            }
        }

        try (PrintStream out = new PrintStream(resp.getOutputStream())) {
            out.print(header);
            out.print(body);
            out.print(footer);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        resp.setHeader("Cache-Control", "no-cache, no-store, must-revalidate, private");
        resp.setHeader("Pragma", "no-cache");
        resp.setDateHeader("Expires", 0);
        if (! API.isAllowed(req.getRemoteHost())) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        String body = null;
        if (!API.disableAdminPassword) {
            if (API.adminPassword.isEmpty()) {
                body = errorNoPasswordIsConfigured;
            } else {
                try {
                    API.verifyPassword(req);
                    if ("true".equals(req.getParameter("showShell"))) {
                        body = form.replace("{adminPassword}", URLEncoder.encode(req.getParameter("adminPassword"), "UTF-8") );
                    }
                } catch (ParameterException exc) {
                    String desc = (String)((JSONObject)JSONValue.parse(JSON.toString(exc.getErrorResponse()))).get("errorDescription");
                    body = String.format(passwordFormTemplate, "<p style=\"color:red\">" + desc + "</p>");
                }
            }
        }

        if (body != null) {
            try (PrintStream out = new PrintStream(resp.getOutputStream())) {
                out.print(header);
                out.print(body);
                out.print(footer);
            }
            return;
        }


        /*
         * suse
         * respons json if query
         *
         */
        String line = Convert.nullToEmpty(req.getParameter("line"));
        String query = Convert.nullToEmpty(req.getParameter("query"));
        if (!query.isEmpty()) {
	        try (Connection con = Db.db.getConnection(); PreparedStatement pstmt = con.prepareStatement(query)) {
	          PrintStream out = new PrintStream(resp.getOutputStream());
		      //out.println(query);
	          JSONObject jsonResponse = new JSONObject();
	          //out.println("\n> " + line);
	            try (ResultSet rs = pstmt.executeQuery()) {
	            	JSONArray jsonArray = new JSONArray();
	                while (rs.next()) {
	                    int total_rows = rs.getMetaData().getColumnCount();
                        JSONObject obj = new JSONObject();
	                    for (int i = 0; i < total_rows; i++) {
	                        obj.put(rs.getMetaData().getColumnLabel(i + 1)
	                                .toLowerCase(), rs.getObject(i + 1));
	                    }
                        jsonArray.add(obj);
	                }
	              // return jsonArray;
	              //jsonResponse.put(jsonArray);
	              out.println(jsonArray);
	              // out.flush();
	              //out.println(jsonResponse);
	            }
	        } catch (SQLException e) {
	            throw new RuntimeException(e.toString(), e);
	        }
        } else if (line != null) {
	        try (PrintStream out = new PrintStream(resp.getOutputStream())) {
	            out.println("\n> " + line);
	            try {
	                Shell shell = new Shell();
	                shell.setErr(out);
	                shell.setOut(out);
	                shell.runTool(Db.db.getConnection(), "-sql", line);
	            } catch (SQLException e) {
	                out.println(e.toString());
	            }
	        }
        }

        /*
        String line = Convert.nullToEmpty(req.getParameter("line"));
        try (Connection con = Db.db.getConnection(); PreparedStatement pstmt = con.prepareStatement(line)) {
          PrintStream out = new PrintStream(resp.getOutputStream());
          JSONObject jsonResponse = new JSONObject();
          //out.println("\n> " + line);
            try (ResultSet rs = pstmt.executeQuery()) {
            	JSONArray jsonArray = new JSONArray();
                while (rs.next()) {
                    int total_rows = rs.getMetaData().getColumnCount();
                    for (int i = 0; i < total_rows; i++) {
                        JSONObject obj = new JSONObject();
                        obj.put(rs.getMetaData().getColumnLabel(i + 1)
                                .toLowerCase(), rs.getObject(i + 1));
                        jsonArray.add(obj);
                    }
                }
              // return jsonArray;
              jsonResponse.put("ResponseData", jsonArray);
              // out.print(jsonResponse);
              // out.flush();
              out.println(jsonResponse);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e.toString(), e);
        }
        */
    }

}
