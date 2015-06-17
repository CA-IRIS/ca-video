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

import java.lang.Integer;
import java.lang.String;

/**
 * A class representing the current state of a video decoder.
 * NOTE: THIS IS PURE PROTOTYPE CODE THAT UNFORTUNATELY, DO TO SCHEDULE
 * CONSTRAINTS, BECAME PRODUCTION CODE.  IT NEEDS A FULL REDESIGN AND
 * REWRITE.
 */

public class DecoderState {
	private String name = null;
	private String host = null;
	private String auth = null;
	// serial # of current stream, or null if not streaming
	private Integer cur_serial = null;
	// cid of current camera, or null if not streaming
	private String cur_cid = null;

	DecoderState() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public String getAuth() {
		return auth;
	}

	public void setAuth(String auth) {
		this.auth = auth;
	}

	public synchronized boolean isStreaming() {
		return ((cur_serial != null) && (cur_cid != null));
	}

	public synchronized void setStreamState(int serial, String cid) {
		if (cid == null)
			return;
		cur_serial = serial;
		cur_cid = cid;
	}

	public synchronized void clearStreamState() {
		cur_serial = null;
		cur_cid = null;
	}

	public Integer getStreamSerial() {
		return cur_serial;
	}

	public String getStreamCamera() {
		return cur_cid;
	}
}

