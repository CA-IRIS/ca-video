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

package us.mn.state.dot.video.server;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Properties;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import us.mn.state.dot.video.Client;
import us.mn.state.dot.video.DataSource;
import us.mn.state.dot.video.DataSourceFactory;
import us.mn.state.dot.video.MJPEG;
import us.mn.state.dot.video.MJPEGWriter;
import us.mn.state.dot.video.StreamStatus;
import us.mn.state.dot.video.VideoException;

/**
 * The <code>StreamServer</code> class is a servlet that responds to client requests for
 * a MN/Dot video stream.  The response is a standard multipart http response which
 * can be parsed as an MJPEG stream.
 *
 * @author Timothy Johnson
 */
public class StreamServer extends VideoServlet {

	private static final Hashtable<String, MJPEGWriter> clientStreams =
		new Hashtable<String, MJPEGWriter>();
	
	/** The DataSourceFactory that maintains the DataSources. */
	private static DataSourceFactory dsFactory;

//	private ThreadMonitor monitor = null;
	
	private int maxFrameRate = 3;
	
	private final String HEADER_CONTENT_TYPE =
		"Content-type: multipart/x-mixed-replace; boundary=" + MJPEG.BOUNDARY;
	
	/** Initializes the servlet. */
	public void init( ServletConfig config ) throws ServletException {
		super.init( config );
		//monitor = new ThreadMonitor("ThreadMonitor", 10000, logger);
		ServletContext ctx = config.getServletContext();
		Properties props =(Properties)ctx.getAttribute("properties");
		dsFactory = new DataSourceFactory(props, null);
		try{
			maxFrameRate = Integer.parseInt(props.getProperty("max.framerate"));
		}catch(Exception e){
			logger.info("Max frame rate not defined, using default...");
		}
	}

	/**
	 * Handles the HTTP <code>GET</code> method.
	 * @param request servlet request
	 * @param response servlet response
	 */
	public void processRequest(HttpServletResponse response,
			Client c) throws VideoException {
		DataSource source = dsFactory.getDataSource(c);
		int sc = 200; //default status code ok
		if(!isAuthenticated(c)){
			sc = HttpServletResponse.SC_FORBIDDEN;
		}else if(c.getCameraId() == null){
			sc = HttpServletResponse.SC_NOT_FOUND;
		}else if(source == null){
			sc = HttpServletResponse.SC_NO_CONTENT;
		}
		response.setStatus(sc);
		response.setContentType(HEADER_CONTENT_TYPE);
		try{
			response.flushBuffer();
		}catch(IOException ioe){
			throw new VideoException("Client closed conection: "+ c.toString());
		}
		if(sc != 200) return;
		try{
			response.flushBuffer();
		}catch(IOException ioe){
			throw new VideoException("Client closed conection: "+ c.toString());
		}
		logger.info("streaming...");
		try{
			streamVideo(response, c, source);
		}catch(IOException ioe){
			throw new VideoException(ioe.getMessage());
		}
	}
	
	/** Send MJPEG stream to the client.
	 * This method blocks until all images have been sent or an error
	 * occurs.
	 * @param response
	 * @param c
	 * @param source
	 * @throws IOException
	 */
	private void streamVideo(HttpServletResponse response, Client c, DataSource source)
			throws IOException {
		logger.fine(c.getCameraId() + " creating client stream...");
		MJPEGWriter w =
			new MJPEGWriter(c, response.getOutputStream(),
				source, logger, maxFrameRate);
		//registerStream(c, w); //FIXME: broken for unauthenticated users
		try{
			((Thread)source).start();
		}catch(IllegalThreadStateException its){
			// do nothing... it's already been started.
		}
		w.sendImages();
	}

	private synchronized static final void registerStream(
			Client c, MJPEGWriter w){
		MJPEGWriter oldStream = (MJPEGWriter)clientStreams.get(c.getUser());
		if(oldStream != null){
			oldStream.halt(StreamStatus.FINISHED);
		}
		clientStreams.put(c.getUser(), w);
	}
}
