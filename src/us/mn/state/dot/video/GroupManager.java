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

import java.lang.NumberFormatException;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;


/**
 * The GroupManager that tracks/controls connection counts.
 * It is designed for use in a non-proxy scenario.
 * NOTE: THIS IS PURE PROTOTYPE CODE THAT UNFORTUNATELY, DO TO SCHEDULE
 * CONSTRAINTS, BECAME PRODUCTION CODE.  IT NEEDS A FULL REDESIGN AND
 * REWRITE.
 */
public class GroupManager {


// FIXME:
// the (lack of) concurrency design here is a complete mess.
// reexamine concurrency assumptions, what methods are intended
// to be called from a thread-safe context, etc.
// consider what to lock on, what to shallow/deep copy, etc.
// too many methods are synchronized.


	private static GroupManager singletonInstance = null;

	protected static Logger logger;
	private final Properties props;

	// list of connection groups
	static final private ArrayList<ConnGroup> conn_groups = new ArrayList<ConnGroup>();

	// map of stream ids to camera
	static final private ConcurrentHashMap<Integer, String> conn_map = new ConcurrentHashMap<Integer, String>();

	private int nextSerialNumber = 0;


	/** Constructor (private) */
	private GroupManager(Properties p) {
		props = p;
		logger = Logger.getLogger(Constants.LOGGER_NAME);

		loadConfigFromProps(props);

		// temp debug log
		for (ConnGroup g : conn_groups) {
			logger.warning(g.toString());
			logger.warning("GN: " + g.getName() + " MAXCONN: " + g.getConnLimit());
		}
		logCurrentStatus();
		}


	// only uses Properties value passed during first use.
	public static synchronized GroupManager getGroupManager(Properties p) {
		if (singletonInstance == null)
			singletonInstance = new GroupManager(p);
		return singletonInstance;
	}


	// intended to be called from a thread-safe context
	private void loadConfigFromProps(Properties props) {
		Set<String> propNames = props.stringPropertyNames();
		for (String s : propNames) {
			// example: conngroup.pots.members=C990,C995,C998,C999
			if (s.matches("^conngroup\\.[^\\.]+\\.members$")) {
				String[] fields = s.split("\\.", 3);
				String groupName  = fields[1].trim();
				ConnGroup cg = getGroupByName(groupName);
				if (cg == null) {
					cg = new ConnGroup(groupName);
					conn_groups.add(cg);
				}
				String entryList = props.getProperty("conngroup." + groupName + ".members");
				if (entryList != null) {
					String entries[] = entryList.split(",");	// untrimmed
					for (String m : entries) {
						String entry = m.trim();
						if (!isCameraInGroup(entry))
							cg.addMember(entry);
						else
							logger.warning("Camera " + entry + " is already in a group.  Not adding to " + cg.getName());
					}
				}
			}
			// example: conngroup.pots.maxconn=2
			else if (s.matches("^conngroup\\.[^\\.]+\\.maxconn$")) {
				String[] fields = s.split("\\.", 3);
				String groupName  = fields[1].trim();
				ConnGroup cg = getGroupByName(groupName);
				if (cg == null) {
					cg = new ConnGroup(groupName);
					conn_groups.add(cg);
				}
				String sMaxConn = props.getProperty("conngroup." + groupName + ".maxconn");
				if (sMaxConn == null)
					continue;

				Integer maxConn = null;
				try {
					maxConn = Integer.valueOf(sMaxConn);
				}
				catch(NumberFormatException e) {
					continue;
				}
				if (cg != null) {
					cg.setConnLimit(maxConn.intValue());
				}
			}

		}
	}


	// get an array of the ConnGroup objects
	// NOTE: should be a deep copy, but a shallow copy is okay for now because the ConnGroup objects don't really change
		// after initialization...
	// uses conn_groups
	public synchronized ConnGroup[] getGroups() {
		return conn_groups.toArray(new ConnGroup[0]);
	}


	// uses conn_groups
	private synchronized ConnGroup getGroupByName(String n) {
		if (n == null)
			return null;
		for (ConnGroup g : conn_groups) {
			if (n.equals(g.getName()))
				return g;
		}
		return null;
	}


	// uses nextSerialNumber
	private synchronized int issueSerialNumber() {
		++nextSerialNumber;
		return (nextSerialNumber - 1);
	}


	// uses conn_groups
	// get an array of the group names
	public synchronized String[] getGroupNames() {
		ArrayList<String> grps = new ArrayList<String>();
		for (ConnGroup cg : conn_groups)
			grps.add(cg.getName());
		return grps.toArray(new String[0]);
	}


	// uses getGroupByName(), and interacts with a ConnGroup
	// get the max # of conns for a particular group
	public int getMaxConnForGroup(String g) {
		ConnGroup cg = getGroupByName(g);
		if (cg == null)
			return -1;
		return cg.getConnLimit();
	}


	// does Camera cid have an active (and tracked) connection?
	public boolean hasActiveConnection(String cid) {
		if (cid == null)
			return false;
		for (Integer id : conn_map.keySet()) {
			if (cid.equals(conn_map.get(id)))
				return true;
		}
		return false;
	}


	public synchronized HashMap<String, Integer> getActiveConnMap() {
		HashMap<String, Integer> conns = new HashMap<String, Integer>();
		for (Integer serial : conn_map.keySet()) {
			String cid = conn_map.get(serial);
			// increment value in map, inferring 0 if not present
			Integer accum = conns.get(cid);
			int newval;
			if (accum == null)
				newval = 0 + 1;
			else
				newval = accum.intValue() + 1;
			conns.put(cid, Integer.valueOf(newval));
		}
		return conns;
	}


	// get the current # of *distinct* conns for a particular group
	public int getGroupUtil(ConnGroup cg) {
		if (cg == null)
			return -1;
		int numConn = 0;
		for (String m : cg.getMembers()) {
			if (hasActiveConnection(m))
				++numConn;
		}
		return numConn;
	}


	// get the current # of distinct conns for a particular groupname
	public int getGroupUtil(String gn) {
		return getGroupUtil(getGroupByName(gn));
	}


	// return true if the camera is in a group, else false.
	public boolean isCameraInGroup(String cid) {
		if (getGroupByCamera(cid) != null)
			return true;
		else
			return false;
	}


	// return a camera's group (assumes it's only in one), else null.
	public ConnGroup getGroupByCamera(String cid) {
		if (cid == null)
			return null;
		for (ConnGroup cg : conn_groups) {
			for (String m : cg.getMembers()) {
				if (cid.equals(m))
					return cg;
			}
		}
		return null;
	}


	// current number of active conns by camera
	public int getNumConnsForCamera(String cid) {
		if (cid == null)
			return -1;
		int numConn = 0;
		for (Integer id : conn_map.keySet()) {
			if (cid.equals(conn_map.get(id)))
				++numConn;
		}
		return numConn;
	}


	// returns -1 on error, else serial #
	public synchronized int requestResource(String cameraId) {
		if (cameraId == null)
			return -1;

		int serial = issueSerialNumber();

		// get camera's group
		ConnGroup cg = getGroupByCamera(cameraId);
		if (cg == null) {
			conn_map.put(serial, cameraId);
			return serial;		// if camera not in a group, just allow without any group accounting.
		}

		int groupLimit = cg.getConnLimit();
		int gu = getGroupUtil(cg.getName());
		if (gu >= groupLimit) {
			return -1;
		}
		conn_map.put(serial, cameraId);
		return serial;
	}


	public synchronized boolean releaseResource(int serial) {
		String cid = conn_map.get(serial);
		if (cid == null)
			return false;		// stream not registered
		conn_map.remove(serial);
		return true;
	}


	// temp debug output
	private void logCurrentStatus() {
		logger.warning("=== CURRENT GROUP STATUS:");
		for (ConnGroup cg : conn_groups) {
			logger.warning(cg.toString());
			logger.warning("\t" + getGroupUtil(cg) + "/" + cg.getConnLimit());
		}
		logger.warning("=== CURRENT CONN MAP:");
		for (Integer id : conn_map.keySet()) {
			logger.warning("\t" + id + " : " + conn_map.get(id));
		}
	}


	public synchronized String getUsageString() {
		String out = "";
		out += "GroupManager status:";
		boolean first = true;
		for (ConnGroup cg : conn_groups) {
			if (!first)
				out += " ";
			out += cg.getName() + ":" + getGroupUtil(cg) + "/" + cg.getConnLimit();
			first = false;
		}
		return out;
	}

}

