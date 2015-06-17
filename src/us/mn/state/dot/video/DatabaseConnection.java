/*
 * Video project
 * Copyright (C) 2011  Minnesota Department of Transportation
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

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Properties;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;


/**
 * DatabaseConnection is a convenience class for making a connection to
 * a database.  It handles all of the queries and sql exceptions as
 * well as re-establishing the connection if it is lost.
 * 
 * @author Timothy Johnson
 * @author Travis Swanston
 */
public class DatabaseConnection {

	protected static final String CAMERA_ID = "name";
	protected static final String CAMERA_ENCODER = "encoder";
	protected static final String CAMERA_ENCODER_CHANNEL = "encoder_channel";
	protected static final String CAMERA_PUBLISH = "publish";
	protected static final String CAMERA_ENCODER_TYPE = "encoder_type";

	protected static final String TABLE_CAMERA = "camera_view";

	// SwitchServer
	protected static final String DID = "did";
	protected static final String CID = "cid";
	protected static final String TABLE_DECODER_MAP = "video.decoder_map";

	protected static final String ASCENDING = "asc";
	
	protected static final String DESCENDING = "desc";
	
	/** Username for authentication to the db server */
	private String user = null;

	/** The name of the database to connect to */
	private String dbName = null;

	/** Password for authentication to the db server */
	private String password = null;

	/** Database URL */
	private String url = null;

	/** The connection object used for executing queries */
	protected Connection connection = null;
	
	protected PreparedStatement isPublishedStatement = null;
	
	protected PreparedStatement encoderHostStatement = null;

	protected PreparedStatement encoderTypeStatement = null;

	protected PreparedStatement encoderChannelStatement = null;

	// SwitchServer
	protected PreparedStatement getDecoderMapStatement = null;
	protected PreparedStatement mapDecoderStatement = null;
	protected PreparedStatement unmapDecoderStatement = null;

	private static DatabaseConnection db = null;
	
	private Logger logger = null;

	public static synchronized DatabaseConnection create(final Properties p){
		if(db == null){
			try{
				db = new DatabaseConnection(p);
			}catch(Exception e){
				return null;
			}
		}
		return db;
	}

	private DatabaseConnection(Properties p){
		this.logger = Logger.getLogger(Constants.LOGGER_NAME);
		this.user = p.getProperty("tms.db.user");
		this.dbName = p.getProperty("tms.db.name");
		this.password = p.getProperty("tms.db.pwd");
		String port_name_separator = "/";
		url = "jdbc:postgresql://" +
				p.getProperty("tms.db.host") + ":" +
				p.getProperty("tms.db.port") +
				port_name_separator +
				dbName;
		connect();
	}

	private void connect(){
		try {
			Class.forName( "org.postgresql.Driver" );
			logger.info( "Openning connection to " + dbName + " database." );
			connection = DriverManager.getConnection( url, user, password );
			DatabaseMetaData md = connection.getMetaData();
			String dbVersion = md.getDatabaseProductName() + ":" + md.getDatabaseProductVersion();
			logger.info("DB: " + dbVersion);
			isPublishedStatement = connection.prepareStatement(
					"select " + CAMERA_PUBLISH + " from " + TABLE_CAMERA +
					" where " + CAMERA_ID + " = ?");
			encoderHostStatement = connection.prepareStatement(
					"select " + CAMERA_ENCODER + " from " + TABLE_CAMERA +
					" where " + CAMERA_ID + " = ?");
			encoderTypeStatement = connection.prepareStatement(
					"select " + CAMERA_ENCODER_TYPE + " from " + TABLE_CAMERA +
					" where " + CAMERA_ID + " = ?");
			encoderChannelStatement = connection.prepareStatement(
					"select " + CAMERA_ENCODER_CHANNEL + " from " + TABLE_CAMERA +
					" where " + CAMERA_ID + " = ?");
			// SwitchServer
			getDecoderMapStatement = connection.prepareStatement(
				"SELECT " + DID + ", " + CID + " FROM " + TABLE_DECODER_MAP);
			mapDecoderStatement = connection.prepareStatement(
				"INSERT INTO " + TABLE_DECODER_MAP + " (" + DID + ", " + CID + ") VALUES (?, ?)");
			unmapDecoderStatement = connection.prepareStatement(
				"DELETE FROM " + TABLE_DECODER_MAP + " WHERE " + DID + " = ?");
			logger.info( "Opened connection to " + dbName + " database." );
		} catch ( Exception e ) {
			System.err.println("Error connecting to DB: " + url + " USER: " + user + " PWD: " + password );
		}
	}
	
	/** Get the publish attribute of the camera */
	public synchronized boolean isPublished(String camId){
		try{
			isPublishedStatement.setString(1, camId);
			ResultSet rs = isPublishedStatement.executeQuery();
			if(rs != null && rs.next()){
				return rs.getBoolean(CAMERA_PUBLISH);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return false;
	}

	public synchronized String getEncoderHost(String camId){
		try{
			encoderHostStatement.setString(1, camId);
			ResultSet rs = encoderHostStatement.executeQuery();
			if(rs != null && rs.next()){
				return rs.getString(CAMERA_ENCODER);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	public synchronized String getEncoderType(String camId){
		try{
			encoderTypeStatement.setString(1, camId);
			ResultSet rs = encoderTypeStatement.executeQuery();
			if(rs != null && rs.next()){
				return rs.getString(CAMERA_ENCODER_TYPE);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return null;
	}

	public synchronized int getEncoderChannel(String camId){
		try{
			encoderChannelStatement.setString(1, camId);
			ResultSet rs = encoderChannelStatement.executeQuery();
			if(rs != null && rs.next()){
				return rs.getInt(CAMERA_ENCODER_CHANNEL);
			}
		}catch(Exception e){
			e.printStackTrace();
		}
		return -1;
	}

	// SwitchServer
	public synchronized ConcurrentHashMap<String, String> getDecoderMap() {
		ConcurrentHashMap<String, String> dmap = new ConcurrentHashMap<String, String>();
		try {
			ResultSet rs = getDecoderMapStatement.executeQuery();
			if (rs == null)
				return null;
			String did = null;
			String cid = null;
			while (rs.next()) {
				did = rs.getString(DID);
				cid = rs.getString(CID);
				if ((did != null) && (cid != null))
					dmap.put(did, cid);
			}
		}
		catch(SQLException e) {
			logger.warning("SQLException: " + e.getStackTrace().toString());
			e.printStackTrace();
			return null;
		}
		return dmap;
	}

	// SwitchServer
	public synchronized void mapDecoder(String did, String cid) {
		// technically, should UPSERT or use transaction here
		if (did == null)
			return;
		try {
			unmapDecoderStatement.setString(1, did);
			unmapDecoderStatement.executeUpdate();
		}
		catch(SQLException e) {
			e.printStackTrace();
		}
		if ((cid == null) || (cid.trim().equals("")))
			return;
		try {
			mapDecoderStatement.setString(1, did);
			mapDecoderStatement.setString(2, cid);
			mapDecoderStatement.executeUpdate();
		}
		catch(SQLException e) {
			e.printStackTrace();
		}
	}

}
