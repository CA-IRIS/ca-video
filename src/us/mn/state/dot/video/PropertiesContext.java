/*
 * Project: Video
 * Copyright (C) 2002-2007  Minnesota Department of Transportation
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package us.mn.state.dot.video;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;

import us.mn.state.dot.video.Constants;

/**
 * Sets up the properties context for all servlets.
 * @author Timothy Johnson
 *
 */
public class PropertiesContext extends HttpServlet{

	/** The logger used to log all output for the application */
	private Logger logger;

	/** Properties file */
	protected final File propsFile = new File("/etc/iris/video.properties");

	/** The default time to live for DNS cache within JRE */
	public static final String DNS_TTL = "3600"; // 1 hour

	public static final String PROP_DNS_TTL = "networkaddress.cache.ttl";

	public static final String PROP_DB_REFRESH = "db.refresh";
	
	/** The time interval for refreshing database information (seconds)*/
	public static final String DB_REFRESH = "3600"; // 1 hour
	
	/** Properties */
	static protected final Properties props = new Properties();

	/** Constructor for the VideoServer */
	public void init( ServletConfig config ) throws ServletException {
		super.init( config );
		ServletContext ctx = config.getServletContext();
		try{
			FileInputStream stream = new FileInputStream(propsFile);
			props.load(stream);
			stream.close();
		}catch(IOException ioe){
			System.out.println("Exception loading: " +
					propsFile.getAbsolutePath());
		}
		String dnsTTL = props.getProperty(PROP_DNS_TTL, DNS_TTL);
		//networkaddress.cache.ttl must be set within the java properties files
		// for TOMCAT applications.  This will do nothing here!
		java.security.Security.setProperty(PROP_DNS_TTL, dnsTTL);
		logger = Logger.getLogger(Constants.LOGGER_NAME);
		logger.setLevel(Level.INFO);
		logger.info("DNS Cache duration set to " +
				java.security.Security.getProperty(PROP_DNS_TTL) + " seconds.");
		ctx.setAttribute("properties", props);
		Calendar c = Calendar.getInstance();
		System.out.println(Constants.DATE_FORMAT.format(c.getTime()) + ": Video servlet started.");
		logger.info("Video Server started.");
	}
}