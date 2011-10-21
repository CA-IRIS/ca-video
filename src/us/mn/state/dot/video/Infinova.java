/*
* VideoServer
* Copyright (C) 2011  Minnesota Department of Transportation
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
 * The InfinovaEncoder class encapsulates information about an Infinova video
 * capture device
 *
 * @author    Timothy Johnson
 */

public final class Infinova extends AbstractEncoder {

	/** The base URI for a request for an image */
	private final String BASE_IMAGE_URI = "/jpgimage/1/image.jpg";
	
	private final String BASE_STREAM_URI = "/jpgimage/1/image.jpg";
	
	/** URI for restarting the server */
	private final String BASE_RESTART_URI = "";
	
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
	
	/** Constructor for the Infinova encoder object */
	public Infinova(String host) {
		super(host);
	}
	
	/**
	 * Get a URL for connecting to the MJPEG stream of an Infinova Server.
	 * @param c The client object containing request parameters.
	 * @return
	 */
	public URL getStreamURL(Client c){
		int channel = getChannel(c.getCameraId());
		if(channel == NO_CAMERA_CONNECTED) return null;
		try{
			return new URL( "http://" + host + ":" +
					getPort() + BASE_STREAM_URI
//					createCameraParam(c) + "&" +
//					createSizeParam(c.getSize()) + "&" +
//					createCompressionParam(c.getCompression())
					);
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
				getPort() + BASE_IMAGE_URI ;
				//createCameraParam(c) + "&" +
				//createSizeParam(c.getSize()) + "&" +
				//createCompressionParam(c.getCompression());
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

	public DataSource getDataSource(Client c) throws VideoException{
		URL url = getStreamURL(c);
		if(url == null) return null;
		try{
			HttpURLConnection con = ConnectionFactory.createConnection(url);
			prepareConnection(con);
			int response = con.getResponseCode();
			//if(response != 200){
			//	throw new Exception("HTTP " + response);
			//}
			return new MultiRequestDataSource(c, con);
		}catch(Exception e){
			throw new VideoException(e.getMessage());
		}
	}
}
