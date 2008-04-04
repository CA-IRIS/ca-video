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

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.logging.Logger;

import us.mn.state.dot.video.AbstractDataSource;
import us.mn.state.dot.video.AxisServer;
import us.mn.state.dot.video.Client;
import us.mn.state.dot.video.DataSource;
import us.mn.state.dot.video.HttpDataSource;
import us.mn.state.dot.video.RepeaterImageFactory;
import us.mn.state.dot.video.ThreadMonitor;
import us.mn.state.dot.video.VideoException;

/**
 * The StreamDispatcher creates and distributes ClientStreams.
 *
 * @author Timothy Johnson
 */
public class ImageFactoryDispatcher {

	private ThreadMonitor monitor = null;
	
	/** Table of video streams that are active. */
	static final protected Hashtable<String, AbstractDataSource>
		sourceTable = new Hashtable<String, AbstractDataSource>();

	private final Logger logger;
	
	/**Flag that controls whether this instance is acting as a proxy 
	 * or a direct video server */
	private boolean proxy = false;

	protected String[] backendUrls = null;
	
	protected ServerFactory serverFactory;
	
	/** Constructor for the ImagefactoryDispatcher. */
	public ImageFactoryDispatcher(Properties p,
			Logger l, ThreadMonitor m) {
		logger = l;
		monitor = m;
		proxy = new Boolean(p.getProperty("proxy", "false")).booleanValue();
		if(proxy) {
			backendUrls = AbstractDataSource.createBackendUrls(p, 1);
		}else{
			serverFactory = new ServerFactory(p);
		}
		Thread t = new Thread(){
			public void run(){
				while(true){
					Enumeration<AbstractDataSource> e = sourceTable.elements();
					while(e.hasMoreElements()){
						AbstractDataSource src = e.nextElement();
						if(!src.isAlive()){
							Client c = src.getClient();
							logger.info("Purging " + src);
							sourceTable.remove(c.getCameraId() + ":" + c.getSize());
						}
					}
					try{
						Thread.sleep(60 * 1000);
					}catch(InterruptedException ie){
					}
				}
			}
		};
		t.start();
	}
	
	private DataSource createDataSource(Client c)
			throws VideoException {
		if(proxy){
			return new RepeaterImageFactory(
					c, backendUrls[c.getArea()], logger, monitor);
		}else{
			AxisServer server = serverFactory.getServer(c.getCameraId());
			if(server == null) throw new VideoException("No encoder for " + c.getCameraId());
			HttpDataSource src = new HttpDataSource(c, logger, monitor, server.getStreamURL(c));
			return src;
		}
	}

	public synchronized DataSource getDataSource(Client c)
			throws VideoException {
		if(c.getCameraId()==null) throw new VideoException(
				"Invalid camera: " + c.getCameraId());
		String name = c.getCameraId() + ":" + c.getSize();
		logger.info("Dataource count: " + sourceTable.size());
		AbstractDataSource src = sourceTable.get(name);
		if(src != null){
			if(src.isAlive()){
				return src;
			}else{
				sourceTable.remove(name);
			}
		}
		src = (AbstractDataSource)createDataSource(c);
		sourceTable.put(name, src);
		return src;
	}
}
