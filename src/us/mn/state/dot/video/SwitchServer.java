/*
 * Project: Video
 * Copyright (C) 2014-2015  AHMCT, University of California
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

import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.NumberFormatException;
import java.lang.StringBuilder;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.sql.*;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


// NOTE:
// if the configuration (e.g. group limits, camera names, metadata, etc.)
// change while the server is down, this presents a problem at startup when the stored resources are requested again
// (unless all connections were closed at shutdown)...
//
// We really need to be able to query the decoders, but this is not feasible at this time,
// as some (all?) VAPIX decoders do not support this.



/**
 * SwitchServer is the main thread for the VAPIX switch server.
 * NOTE: THIS IS PURE PROTOTYPE CODE THAT UNFORTUNATELY, DO TO SCHEDULE
 * CONSTRAINTS, BECAME PRODUCTION CODE.  IT NEEDS A FULL REDESIGN AND
 * REWRITE.
 */
public final class SwitchServer extends HttpServlet {

	// timeouts for connections to decoders
	public static final int TIMEOUT_CONN_MS = 3000;		// connection timeout (3 sec.)
	public static final int TIMEOUT_READ_MS = 3000;		// read timeout (3 sec.)

	// map of the current state of the decoders, keyed by decoder name
	static final private ConcurrentHashMap<String, DecoderState> dec_map
		= new ConcurrentHashMap<String, DecoderState>();

	/** The GroupManager */
	protected static GroupManager groupManager = null;

	// mapping of camera names to decoder source names
	static final private ConcurrentHashMap<String, String> srcNames
		= new ConcurrentHashMap<String, String>();


	protected DatabaseConnection tms = null;

	/** The logger used to log all output for the application */
	protected static Logger logger;


	@Override
	public void init(ServletConfig config) throws ServletException {
		super.init( config );
		ServletContext ctx = config.getServletContext();
		Properties props = (Properties)ctx.getAttribute("properties");
		if (logger == null)
			logger = Logger.getLogger(Constants.LOGGER_NAME);
		groupManager = (GroupManager)ctx.getAttribute("groupmanager");
		loadConfigFromProps(props);
		tms = DatabaseConnection.create(props);
		restoreStateFromDb();
		logger.info( "SwitchServer initialized successfully." );
	}


	// intended to be called from a thread-safe context
	private void loadConfigFromProps(Properties props) {
		Set<String> propNames = props.stringPropertyNames();

		// import decoder names and hosts from properties
		for (String s : propNames) {
			// decoder.NAME.host=HOST:PORT
			if (s.matches("^decoder\\.[^\\.]+\\.host$")) {
				String[] fields = s.split("\\.", 3);
				String decoderName = fields[1].trim();
				logger.info("getting property: decoder." + decoderName + ".host");
				String host = props.getProperty("decoder." + decoderName + ".host");
				if (host != null) {
					DecoderState dec = dec_map.get(decoderName);
					if (dec == null) {
						dec = new DecoderState();
						dec_map.put(decoderName, dec);
					}
					dec.setName(decoderName);
					dec.setHost(host.trim());
					logger.info("set name/host for decoder: " + dec.getName() + "," + dec.getHost());
				}
			}
		}

		// decoder auth from properties
		for (String s : propNames) {
			// decoder.NAME.auth=USER:PASS
			if (s.matches("^decoder\\.[^\\.]+\\.auth$")) {
				String[] fields = s.split("\\.", 3);
				String decoderName  = fields[1].trim();
				logger.info("getting property: decoder." + decoderName + ".auth");
				String auth = props.getProperty("decoder." + decoderName + ".auth");
				if (auth != null) {
					DecoderState dec = dec_map.get(decoderName);
					if (dec == null) {
						dec = new DecoderState();
						dec_map.put(decoderName, dec);
					}
					logger.info("setting auth for " + decoderName);
					dec.setAuth(auth.trim());
				}
			}
		}

		// import camera source names from properties
		for (String s : propNames) {
			// srcname.C001=CAM-one
			if (s.matches("^srcname\\.[^\\.]+$")) {
				String[] fields = s.split("\\.", 2);
				String camName = fields[1].trim();
				logger.info("getting property: srcname." + camName);
				String srcName = props.getProperty("srcname." + camName);
				if (srcName != null) {
					logger.info("adding srcNames[" + camName + "," + srcName + "]");
					srcNames.put(camName, srcName.trim());
				}
			}
		}
	}

	// intended to be called from a thread-safe context
	private void restoreStateFromDb() {
		// restore statemap from db
		ConcurrentHashMap<String, String> tempStateMap = tms.getDecoderMap();
logger.info("TEMPSTATEMAP SIZE == " + tempStateMap.size());
		if (tempStateMap != null) {
			// request locks for restored connection state
			for (String did : tempStateMap.keySet()) {
				String cid = tempStateMap.get(did);
				logger.info("ABOUT TO REQUEST LOCK FOR cid=" + cid + " GROUP");
				int serial = groupManager.requestResource(cid);
				logger.info("REQUEST [" + cid + "]: " + serial);
				if (serial >= 0) {
					mapDecoder(did, cid, serial);
				}
				else {
					logger.severe("failed to obtain lock for restored connection to " + cid);
				}
			}
		}
		else {
			logger.severe("fatal error retrieving restore state from db");
		}
	}

	// intended to be called from a thread-safe context
	private boolean isCameraStreamingToADecoder(String cid) {
		if (cid == null)
			return false;
		for (DecoderState d : dec_map.values()) {
			if (cid.equals(d.getStreamCamera()))
				return true;
		}
		return false;
	}


	/**
	 * Handles the HTTP <code>GET</code> method.
	 * @param request servlet request
	 * @param response servlet response
	 */
	@Override
	protected void doGet(HttpServletRequest request,
		HttpServletResponse response) {

		String servletName = this.getClass().getSimpleName();
		Thread t = Thread.currentThread();
		Calendar cal = Calendar.getInstance();
		//this.request = request;

		String cid  = request.getParameter("cid");		// camera id, might be null
		String did  = request.getParameter("did");		// decoder id, might be null
		String cmd  = request.getParameter("cmd");		// cmd, might be null

		try {
			t.setName("VIDEO " + servletName + " " +
				Constants.DATE_FORMAT.format(cal.getTime()) +
				" id=" + cid + ", did=" + did + ", cmd=" + cmd);
		}
		catch (Throwable th) {
			logger.warning(cid + ": " + th.getMessage());
		}

		response.setContentType("text/plain");
		response.setCharacterEncoding("UTF-8");

		StringBuilder outSB = new StringBuilder();
		boolean success = handleCmd(cmd, did, cid, outSB);
		try {
			OutputStream os = response.getOutputStream();
			byte[] outBytes = outSB.toString().getBytes("UTF-8");
			os.write(outBytes);
			os.flush();				// keep?
		}
		catch (UnsupportedEncodingException e) {
			// NOP: not much we can do here...
		}
		catch (IOException e) {
			// NOP: not much we can do here...
		}

		// TODO: if success false, non-200?
		response.setStatus(200);

		try {
			t.setName(t.getName() + " done");
			response.flushBuffer();			// remove?
			response.getOutputStream().close();
		}
		catch (Exception e) {
		}
	}


	/**
	 * Handle a command, with optional "did" and "cid" arguments.
	 * <p><blockquote><pre>
	 *     Command summary:
	 *     CMD          ARGS       FUNCTION
	 *     -------------------------------------------------------------------
	 *     grouputil               get current group utilization/limits for all groups
	 *     camconns                get current # of conns for all active cameras
	 *     numconns     cid        get current # of conns for camera cid
	 *     decstat                 get current decoder:camera mappings
	 *     conn         did,cid    connect decoder did to camera cid
	 *     disccam      cid        disconnect camera cid from all decoders to which it's connected
	 *     discdec      did        disconnect decoder did from whatever it's connected to
	 * </pre></blockquote>


	 */
	private synchronized boolean handleCmd(String cmd, String did, String cid, StringBuilder out) {
		if (cmd == null)
			return false;
		else if ("grouputil".equals(cmd))
			return handleCmdGrouputil(out);
		else if ("numconns".equals(cmd))
			return handleCmdNumconns(cid, out);
		else if ("camconns".equals(cmd))
			return handleCmdCamconns(out);
		else if ("decstat".equals(cmd))
			return handleCmdDecstat(out);
		else if ("conn".equals(cmd))
			return handleCmdConn(did, cid, out);
		else if ("disccam".equals(cmd))
			return handleCmdDisccam(cid, out);
		else if ("discdec".equals(cmd))
			return handleCmdDiscdec(did, out);
		out.append("ERROR: invalid cmd\n");
		return false;
	}


	// intended to be called from a thread-safe context
	private boolean handleCmdGrouputil(StringBuilder out) {
		ConnGroup[] groups = groupManager.getGroups();
		out.append("UTIL\t");
		for (int i=0; i<groups.length; ++i) {
			ConnGroup g = groups[i];
			out.append(g.getName());
			out.append(":" + groupManager.getGroupUtil(g));
			out.append("/");
			out.append(g.getConnLimit());
			if (i < (groups.length-1)) out.append(",");
		}
		out.append("\n");
		return true;
	}


	// intended to be called from a thread-safe context
	private boolean handleCmdNumconns(String cid, StringBuilder out) {
		if (cid == null)
			return false;
		if (!isValidCamera(cid))
			return false;
		// handle request to return the number of active connections for a camera... (registered streams + decoder conns)
		int conns = groupManager.getNumConnsForCamera(cid);
		out.append("NUMCONNS\t" + conns + "\n");
		return true;
	}


	// intended to be called from a thread-safe context
	private boolean handleCmdCamconns(StringBuilder out) {
		// return camconns for all active cameras (inactive cameras excluded)
		HashMap<String, Integer> activeConns = groupManager.getActiveConnMap();

		boolean first = true;
		out.append("CAMCONNS\t");
		for (String c : activeConns.keySet()) {
			if (!first) out.append(",");
			out.append(c);
			out.append(":");
			out.append(activeConns.get(c));
			first = false;
		}
		out.append("\n");
		return true;
	}


	// intended to be called from a thread-safe context
	private boolean handleCmdDecstat(StringBuilder out) {
		int numDids = dec_map.size();
		int i = 0;
		out.append("DECSTAT\t");
		for (DecoderState d : dec_map.values()) {
			String v = d.getStreamCamera();
			out.append(d.getName());
			out.append(":");
			if (v != null)
				out.append(v);
			if (i < (numDids - 1))
				out.append(",");
			++i;
		}
		out.append("\n");
		return true;
	}


	// intended to be called from a thread-safe context
	private boolean handleCmdConn(String did, String cid, StringBuilder out) {
		if ((cid == null) || (did == null))
			return false;
		if (!isValidCamera(cid))
			return false;

		DecoderState dec = dec_map.get(did);
		if (dec == null)
			return false;

		// deny if the camera is already streaming to a decoder
		// (this logic was added later, which is why disccam can handle multiple disconnects)
		if (isCameraStreamingToADecoder(cid)) {
			out.append("Error: "+cid+" already in use by a decoder.\n");
			return false;
		}

		// if decoder is streaming, disconnect and release
		if (dec.isStreaming()) {
			int curSerial = dec.getStreamSerial().intValue();
			if (!(executeDisconnect(did))) {
				out.append("Error requesting disconnect.\n");
				return false;
			}
			boolean relStatus = groupManager.releaseResource(curSerial);
			logger.info("RELEASE [" + curSerial + "]: " + relStatus);
			unmapDecoder(did);
		}

		// request resource and, if granted, connect
		int newSerial = groupManager.requestResource(cid);
		logger.info("REQUEST [" + cid + "]: " + newSerial);
		if (newSerial < 0) {
			out.append("Error: group connection limit reached.\n");
			return false;
		}
		// execute decoder connect
		if (!(executeConnect(did,cid))) {
			// connect failed.  release resource.
			out.append("Error requesting connect.\n");
//			int curSerial = dec.getStreamSerial().intValue();
//			boolean relStatus = groupManager.releaseResource(curSerial);
			boolean relStatus = groupManager.releaseResource(newSerial);
			logger.info("RELEASE [" + newSerial + "]: " + relStatus);
			return false;
		}
		mapDecoder(did, cid, newSerial);
		out.append("OK\n");
		return true;
	}


	// intended to be called from a thread-safe context
	private boolean handleCmdDisccam(String cid, StringBuilder out) {
		// process disconnect request: disconnect this camera from all decoders to which it's connected
		if (cid == null)
			return false;
		if (!isValidCamera(cid))
			return false;

		boolean foundOne = false;
		// iterate through dec:cam map, disconnecting all decoders connected to camera cid
		for (DecoderState d : dec_map.values()) {
			if (!(d.isStreaming()))
				continue;
			// check current camera c for decoder d
			String c = d.getStreamCamera();
			if (cid.equals(c)) {
				foundOne = true;
				if (!(executeDisconnect(d.getName()))) {
					out.append("Error requesting disconnect.\n");
					return false;
				}
				Integer curSerial = d.getStreamSerial();
				boolean relStatus = groupManager.releaseResource(curSerial.intValue());
				logger.info("RELEASE [" + c + "]: " + relStatus);
				unmapDecoder(d.getName());
			}
		}
		if (!foundOne) {
			out.append("Camera not currently connected to a decoder.\n");
			return false;
		}
		out.append("OK\n");
		return true;
	}


	// disconnect decoder did
	// intended to be called from a thread-safe context
	private boolean handleCmdDiscdec(String did, StringBuilder out) {
		// design requirement: consider disconnecting a non-streaming decoder to not be an error.
		// also, issue the command anyway in this case.
		if (did == null)
			return false;
		DecoderState dec = dec_map.get(did);
		if (dec == null)
			return false;
		boolean streamingState = dec.isStreaming();
//		if (!streamingState)) {
//			out.append("OK\n");
//			return true;
//		}

		String c = dec.getStreamCamera();
		Integer curSerial = dec.getStreamSerial();

		if (!executeDisconnect(did)) {
			out.append("Error requesting disconnect.\n");
			return false;
		}

		// only release if we thought it was streaming
		if (streamingState == true) {
			boolean relStatus = groupManager.releaseResource(curSerial.intValue());
			logger.info("RELEASE [" + c + "]: " + relStatus);
			unmapDecoder(did);
		}
		out.append("OK\n");
		return true;
	}


	// intended to be called from a thread-safe context
	private void mapDecoder(String did, String cid, int serial) {
		if ((did == null) || (cid == null))
			return;
		DecoderState d = dec_map.get(did);
		if (d == null)
			return;
		d.setStreamState(serial, cid);
		tms.mapDecoder(did, cid);
	}


	// intended to be called from a thread-safe context
	private void unmapDecoder(String did) {
		if (did == null)
			return;
		DecoderState d = dec_map.get(did);
		if (d == null)
			return;
		d.clearStreamState();
		tms.mapDecoder(did, null);
	}

	private boolean isValidCamera(String cid) {
		String type = tms.getEncoderType(cid);
		if (type != null)
			return true;
		return false;
	}

	// intended to be called from a thread-safe context
	private boolean executeConnect(String did, String cid) {

		DecoderState d = dec_map.get(did);
		if (d == null)
			return false;

		String decHost = d.getHost();

		if (decHost == null)
			return false;

		String decAuth = d.getAuth();

		String srcName = srcNames.get(cid);
		// if no sourcename for cid, fallback to cid
		if ((srcName == null) || (srcName.trim().equals("")))
			srcName = cid;

		logger.info("executing command to decoder [" + did + ","+decHost+"] "
			+ "to switch to source ["+cid+","+srcName+"]");

		HttpURLConnection conn = null;
		InputStream is = null;
		try {
			URL url = new URL("http://" + decHost
				+ "/axis-cgi/admin/videocontrol.cgi?action=goto&sourcename=" + srcName);
			HttpURLConnection.setFollowRedirects(true);
			conn = (HttpURLConnection) (url.openConnection());
			conn.setConnectTimeout(TIMEOUT_CONN_MS);
			conn.setReadTimeout(TIMEOUT_READ_MS);

			if (decAuth != null) {
				String encoded = Base64.encodeBytes(decAuth.getBytes());
				System.err.println("decAuth: u:p=="+decAuth+ ", encoded:" + encoded);
				conn.addRequestProperty("Authorization", "Basic " + encoded.toString());
			}

			is = conn.getInputStream();
		}
		catch (IOException ie) {
			// TODO
		}

		if (is == null)
			return false;
		byte[] b = readBytes(is);
		if (b == null)
			return false;

		int resp = -1;
		try {
			if (conn != null)
				resp = conn.getResponseCode();
		}
		catch (IOException ie) {
			// TODO?
		}
		finally {
			if (conn != null) {
				conn.disconnect();
				conn = null;
			}
		}

		logger.info("response code == " + resp + "; read " + b.length + " bytes: " + bytesToHex(b));
		if (resp != 200) {
			logger.info("error: response code indicates failure");
			return false;
		}
		if (!checkVapixAck(b)) {
			logger.info("error: failed to parse successful VAPIX ACK");
			return false;
		}
		logger.info("successful VAPIX ACK");
		return true;
	}


	// intended to be called from a thread-safe context
	private boolean executeDisconnect(String did) {

		DecoderState d = dec_map.get(did);
		if (d == null)
			return false;

		String decHost = d.getHost();

		if (decHost == null)
			return false;

		String decAuth = d.getAuth();

		logger.info("executing command to decoder [" + did + ","+decHost+"] "
			+ "to disconnect");

		HttpURLConnection conn = null;
		InputStream is = null;
		try {
			URL url = new URL("http://" + decHost
				+ "/axis-cgi/admin/videocontrol.cgi?action=disconnect");

			HttpURLConnection.setFollowRedirects(true);
			conn = (HttpURLConnection) (url.openConnection());
			conn.setConnectTimeout(TIMEOUT_CONN_MS);
			conn.setReadTimeout(TIMEOUT_READ_MS);

			if (decAuth != null) {
				String encoded = Base64.encodeBytes(decAuth.getBytes());
				System.err.println("decAuth: u:p=="+decAuth+ ", encoded:" + encoded);
				conn.addRequestProperty("Authorization", "Basic " + encoded.toString());
			}

			is = conn.getInputStream();
		}
		catch (IOException ie) {
			// TODO
		}

		if (is == null)
			return false;
		byte[] b = readBytes(is);
		if (b == null)
			return false;

		int resp = -1;
		try {
			if (conn != null)
				resp = conn.getResponseCode();
		}
		catch (IOException ie) {
			// TODO?
		}
		finally {
			if (conn != null) {
				conn.disconnect();
				conn = null;
			}
		}

		logger.info("response code == " + resp + "; read " + b.length + " bytes: " + bytesToHex(b));
		if (resp != 200) {
			logger.info("error: response code indicates failure");
			return false;
		}
		if (!checkVapixAck(b)) {
			logger.info("error: failed to parse successful VAPIX ACK");
			return false;
		}
		logger.info("successful VAPIX ACK");
		return true;
	}


	/**
	 * Read the specified input stream and return a byte array or null on
	 * error.
	 * @param is Input stream; may be null.
	 * @return array of bytes read, or null on error.
	 */
	private static byte[] readBytes(InputStream is) {
		if (is == null)
			return null;
		byte[] ret = new byte[0];
		try {
			// read until eof
			ArrayList<Byte> al = new ArrayList();
			while(true) {
				int b = is.read();	// throws IOE
				if (b < 0)		// EOF?
					break;
				al.add(new Byte((byte)b));
			}
			// create byte[]
			ret = new byte[al.size()];
			for(int i = 0; i < ret.length; ++i)
				ret[i] = (byte)(al.get(i));
		}
		catch (IOException e) {
			return null;
		}
		return ret;
	}


	private static String bytesToHex(byte[] buf) {
		if (buf == null)
			return "";
		StringBuilder sb = new StringBuilder(buf.length * 3);
		for (int i=0; i < buf.length; ++i) {
			sb.append(String.format("%02x", buf[i] & 0xff));
			if (i < (buf.length - 1))
				sb.append(" ");
		}
		return sb.toString();
	}


	private boolean checkVapixAck(byte[] buf) {
		// check for successful Axis P7701 (VAPIX3?) ACK
		if ( (buf.length >= 3)
			&& (buf[0] == (byte)0x4f)
			&& (buf[1] == (byte)0x6b)
			&& (buf[2] == (byte)0x0a)
			)
		{
			return true;
		}
		// check for successful Axis P7701 (VAPIX3?) ACK
		if ( (buf.length >= 4)
			&& (buf[0] == (byte)0x4f)
			&& (buf[1] == (byte)0x4b)
			&& (buf[2] == (byte)0x0d)
			&& (buf[3] == (byte)0x0a)
			)
		{
			return true;
		}
		return false;
	}

}

