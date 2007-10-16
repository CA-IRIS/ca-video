/*
* VideoServer
* Copyright (C) 2003-2007  Minnesota Department of Transportation
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
* Foundation, Inc., 59 temple Place, Suite 330, Boston, MA  02111-1307  USA
*/
package us.mn.state.dot.video;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.util.Hashtable;

import javax.imageio.stream.FileImageInputStream;


/**
 * The AxisServer class encapsulates information about an axis video
 * capture server
 *
 * @author    Timothy Johnson
 * @created   July 2, 2003
 * @version   $Revision: 1.24 $ $Date: 2006/04/11 21:22:48 $
 */

public final class AxisServer{

	/** Collection of all Axis servers */
	private static final Hashtable<String, AxisServer> servers =
		new Hashtable<String, AxisServer>();
		
	/** The URLConnection used for getting stills */
	private URLConnection stillsCon;
	
	/** Constant for small sized images */
	public static final int SMALL = 1;

	/** Constant for medium sized images */
	public static final int MEDIUM = 2;

	/** Constant for large sized images */
	public static final int LARGE = 3;

	/** Constant string for no camera connected */
	public static final int NO_CAMERA_CONNECTED = -1;

	/** The base URI for a request for an image */
	private final String BASE_IMAGE_URI = "/axis-cgi/jpg/image.cgi?" +
		"showlength=1&";
	
	private final String BASE_STREAM_URI = "/axis-cgi/mjpg/video.cgi?" +
		"showlength=1&";
	
	/** The compression request parameter */
	private static final String PARAM_COMPRESSION = "compression";
	
	/** The clock request parameter */
	private static final String PARAM_CLOCK = "clock";

	/** The date request parameter */
	private static final String PARAM_DATE = "date";

	/** The size request parameter */
	private static final String PARAM_SIZE = "resolution";
	
	/** The camera request parameter */
	private static final String PARAM_CAMERA = "camera";
	
	/** The parameter value for small images */
	private static final String VALUE_SMALL = "176x144";

	/** The parameter value for medium size images */
	private static final String VALUE_MEDIUM = "352x240";

	/** The parameter value for large images */
	private static final String VALUE_LARGE = "704x480";

	/** The parameter value for off */
	private static final String VALUE_OFF = "0";
	
	/** Location of the no_video image */
	private static String noVideoFile = 
		"/usr/local/tomcat/current/webapps/@@NAME@@/images/novideo.jpg";

	private static byte[] noVideo = createNoVideoImage();
	
	/** The number of video channels available */
	private int channels = 4;

	/** The host name (or IP) of the server */
	private final String hostName;

	/** The port on which the axis server listens for HTTP requests */
	private int httpPort = 80;

	/** The username used to connect to this server.  Only required when
	 * Axis server does not allow anonymous connections.
	 */
	private String username = null;
	
	/** The password used to connect to this server.  Only required when
	 * Axis server does not allow anonymous connections.
	 */
	private String password = null;

	/** The ids of the cameras that are connected. */
	private String[] ids = new String[ channels + 1 ];
	// ids.size is one more than the channel count so that
	// we can use the actual channel number to index
	// into the ids instead of using channel - 1.

	/** Get an AxisServer by host (name or IP) */
	public static AxisServer getServer(String host){
		AxisServer s = servers.get(host);
		if(s==null){
			s = new AxisServer(host);
			servers.put(host, s);
		}
		return s;
	}
	
	public static void printServers(){
		for(AxisServer s: servers.values()) {
			System.out.println(s);
		}

	}
	
	public static byte[] getNoVideoImage(){
		return noVideo;
	}

	/** Constructor for the axis server object */
	protected AxisServer(String host) {
		hostName = host;
		for( int i=0; i<ids.length; i++ ) {
			ids[ i ] = null;
		}
	}
	
	private String getIp() throws UnknownHostException{
		return InetAddress.getByName(hostName).getHostAddress();
	}
	
	/**
	 * Get a URL for connecting to the MJPEG stream of an Axis Server.
	 * @param c The client object containing request parameters.
	 * @return
	 */
	private URL getStreamURL(Client c){
		int channel = getChannel(c.getCameraId());
		if(channel == NO_CAMERA_CONNECTED) return null;
		try{
			return new URL( "http://" + hostName + ":" +
					httpPort + BASE_STREAM_URI +
					createCameraParam(c) + "&" +
					createSizeParam(c.getSize()) + "&" +
					createCompressionParam(c.getCompression()));
		}catch(Exception e){
		}
		return null;
	}

	private String createCompressionParam(int comp){
		return PARAM_COMPRESSION + "=" + comp;	
	}
	
	private String createCameraParam(Client c){
		return PARAM_CAMERA + "=" + getChannel(c.getCameraId());	
	}

	private URL getImageURL(Client c) {
		int channel = getChannel(c.getCameraId());
		if(channel == NO_CAMERA_CONNECTED) return null;
		try{
			String url = 
				"http://" + hostName + ":" +
				httpPort + BASE_IMAGE_URI +
				createCameraParam(c) + "&" +
				createSizeParam(c.getSize()) + "&" +
				createCompressionParam(c.getCompression());
/*			if(size==SMALL){
				url = url +
					"&" + PARAM_CLOCK + "=" + VALUE_OFF +
					"&" + PARAM_DATE + "=" + VALUE_OFF;
			}*/
			return new URL(url);
		}catch(Exception e){
			return null;
		}
	}

	private String createSizeParam(int size){
		String sizeValue = "";
		switch(size){
			case SMALL:
				sizeValue = VALUE_SMALL;
				break;
			case MEDIUM:
				sizeValue = VALUE_MEDIUM;
				break;
			case LARGE:
				//don't let anyone get the big images until
				//we find a way to limit access to them (bandwidth issue)
				sizeValue = VALUE_MEDIUM;
				break;
		}
		return PARAM_SIZE + "=" + sizeValue;
	}
	
	public byte[] getImage(Client c) throws VideoException{
		URL url = getImageURL(c);
		if(url == null){
			throw new VideoException("No URL for camera " + c.getCameraId());
		}
		byte[] image = fetchImage(url);
		if(image != null) return image;
		return noVideo;
	}

	/** Create a no-video image */
	static byte[] createNoVideoImage(){
		try{
			FileImageInputStream in = null;
			in = new FileImageInputStream(new File(noVideoFile));
			byte[] bytes = new byte[(int)in.length()];
			in.read(bytes, 0, bytes.length);
			return bytes;
		}catch(IOException ioe){
			return null;
		}
	}


	/** Get the id of the camera connected to channel c */
	public String getCamera(int channel) {
		if(channel<1 || channel>channels)
			throw new IndexOutOfBoundsException( "Invalid channel number: " + channel );
		return ids[channel];
	}

	public int getChannel(String id){
		for(int i=1; i<=channels; i++){
			if(id.equals(ids[i])) return i;
		}
		return NO_CAMERA_CONNECTED;
	}

	/** Set the camera for the given channel */
	public void setCamera(String id, int channel) {
		ids[channel] = id;
	}

	public MJPEGStream getStream(Client c) throws VideoException{
		URL url = getStreamURL(c);
		if(url == null) return null;
		try{
			URLConnection con = ConnectionFactory.createConnection(url);
			prepareConnection(con);
			InputStream s = con.getInputStream();
			MJPEGStream videoStream = new MJPEGStream(s);
			return videoStream;
		}catch(Exception e){
			throw new VideoException(e.getMessage() + ": " + url.toString());
		}
	}

	/** Prepare a connection by setting necessary properties and timeouts */
	private void prepareConnection(URLConnection c) throws VideoException {
		if(username!=null && password!=null){
			String userPass = username + ":" + password;
			String encoded = Base64.encodeBytes(userPass.getBytes());
			c.addRequestProperty("Authorization", "Basic " + encoded.toString());
		}
	}
	
	private synchronized final byte[] fetchImage(URL url) throws VideoException{
		InputStream in = null;
		try {
			stillsCon = ConnectionFactory.createConnection(url);
			prepareConnection(stillsCon);
			in = stillsCon.getInputStream();
			int length = Integer.parseInt(
					stillsCon.getHeaderField("Content-Length"));
			return readImage(in, length);
		}catch(Exception e){
			throw new VideoException("Fetch error: " + e.getMessage());
		}finally{
			try{
				in.close();
			}catch(Exception e){
			}
		}
	}

	/** Get the next image in the mjpeg stream 
	 *  in which the Content-Length header is present
	 * @return
	 */
	private byte[] readImage(InputStream in, int imageSize)
			throws IOException{
		byte[] image = new byte[imageSize];
		int bytesRead = 0;
		int currentRead = 0;
		while(bytesRead < imageSize){
			currentRead = in.read(image, bytesRead,
					imageSize - bytesRead);
			if(currentRead==-1){
				break;
			}else{
				bytesRead = bytesRead + currentRead;
			}
		}
		return image;
	}

	public int getHttpPort() {
		return httpPort;
	}

	public void setHttpPort(int httpPort) {
		this.httpPort = httpPort;
	}

	public String toString(){
		String ip = "";
		try{
			ip = getIp();
		}catch(Exception e){}
		return "Axis Server " + hostName + " (" + ip + ")";
	}

	public void setPassword(String pwd) {
		this.password = pwd;
	}

	public void setUsername(String name) {
		this.username = name;
	}
}