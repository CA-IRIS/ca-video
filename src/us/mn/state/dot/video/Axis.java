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

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Hashtable;


/**
 * The AxisEncoder class encapsulates information about an axis video
 * capture device
 *
 * @author    Timothy Johnson
 * @created   July 2, 2003
 */

public final class Axis extends AbstractEncoder {

	/** The HttpURLConnection used for getting stills */
	private HttpURLConnection stillsCon;
	
	/** The base URI for a request for an image */
	private final String BASE_IMAGE_URI = "/axis-cgi/jpg/image.cgi?" +
		"showlength=1&";
	
	private final String BASE_STREAM_URI = "/axis-cgi/mjpg/video.cgi?" +
	"showlength=1&";
	
	/** URI for restarting the server */
	private final String BASE_RESTART_URI = "/axis-cgi/admin/restart.cgi?";
	
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
	
	/** Constructor for the axis encoder object */
	public Axis(String host) {
		super(host);
	}
	
	/**
	 * Get a URL for connecting to the MJPEG stream of an Axis Server.
	 * @param c The client object containing request parameters.
	 * @return
	 */
	public URL getStreamURL(Client c){
		int channel = getChannel(c.getCameraId());
		if(channel == NO_CAMERA_CONNECTED) return null;
		try{
			return new URL( "http://" + host + ":" +
					getPort() + BASE_STREAM_URI +
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
				"http://" + host + ":" +
				getPort() + BASE_IMAGE_URI +
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
			case Client.SMALL:
				sizeValue = VALUE_SMALL;
				break;
			case Client.MEDIUM:
				sizeValue = VALUE_MEDIUM;
				break;
			case Client.LARGE:
				sizeValue = VALUE_LARGE;
				break;
		}
		return PARAM_SIZE + "=" + sizeValue;
	}
	
	public byte[] getImage(Client c) throws VideoException{
		URL url = getImageURL(c);
		if(url == null){
			throw new VideoException("No URL for camera " + c.getCameraId());
		}
		byte[] image = fetchImage(c, url);
		if(image != null) return image;
		return getNoVideoImage();
	}

	public VideoStream getStream(Client c) throws VideoException{
		URL url = getStreamURL(c);
		if(url == null) return null;
		try{
			URLConnection con = ConnectionFactory.createConnection(url);
			prepareConnection(con);
			int response = stillsCon.getResponseCode();
			if(response == 503){
				throw new Exception("HTTP 503");
			}
			InputStream s = con.getInputStream();
			MJPEGReader videoStream = new MJPEGReader(s);
			return videoStream;
		}catch(Exception e){
			throw new VideoException(e.getMessage());
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
	
	private synchronized final byte[] fetchImage(Client c, URL url) throws VideoException{
		InputStream in = null;
		try {
			stillsCon = ConnectionFactory.createConnection(url);
			prepareConnection(stillsCon);
			int response = stillsCon.getResponseCode();
			if(response == 503){
				throw new Exception("HTTP 503");
			}
			in = stillsCon.getInputStream();
			int length = Integer.parseInt(
					stillsCon.getHeaderField("Content-Length"));
			return readImage(in, length);
		}catch(Exception e){
			throw new VideoException(e.getMessage());
		}finally{
			try{
				stillsCon.disconnect();
			}catch(Exception e){}
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
}