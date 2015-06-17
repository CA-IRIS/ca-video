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

import java.lang.String;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A class representing a Connection Group -- a group of cameras to which
 * connections, for purposes of network management, are to be managed
 * with a group-specific policy.
 * NOTE: THIS IS PURE PROTOTYPE CODE THAT UNFORTUNATELY, DO TO SCHEDULE
 * CONSTRAINTS, BECAME PRODUCTION CODE.  IT NEEDS A FULL REDESIGN AND
 * REWRITE.
 */

public class ConnGroup {
	private final String name;
	private int connLimit = 0;
	private final ArrayList<String> members = new ArrayList<String>();

	ConnGroup(String name) {
		this.name = name;
	}

	public void addMember(String m) {
		if (m != null)
			members.add(m);
	}

	public String getName() {
		return name;
	}

	public int getConnLimit() {
		return connLimit;
	}

	public void setConnLimit(int l) {
		if (l >= 0)
			connLimit = l;
		else
			connLimit = 0;
	}

	public String[] getMembers() {
		return members.toArray(new String[0]);
	}

	public String toString() {
		String s = "ConnGroup[" + name + "," + connLimit + "]:";
		for (int i=0; i<members.size(); ++i) {
			s += members.get(i);
			if (i < (members.size()-1))
				s += ",";
		}
		return s;
	}

}

