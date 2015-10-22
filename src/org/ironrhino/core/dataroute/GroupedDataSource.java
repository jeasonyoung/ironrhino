package org.ironrhino.core.dataroute;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.PostConstruct;
import javax.sql.DataSource;

import org.ironrhino.core.stat.Key;
import org.ironrhino.core.stat.StatLog;
import org.ironrhino.core.util.RoundRobin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.datasource.AbstractDataSource;

public class GroupedDataSource extends AbstractDataSource implements BeanNameAware {

	private Logger logger = LoggerFactory.getLogger(getClass());

	private int maxAttempts = 3;

	// inject starts
	private String masterName;

	private Map<String, Integer> writeSlaveNames;

	private Map<String, Integer> readSlaveNames;

	// inject end

	private String groupName;

	private DataSource master;

	private Map<String, DataSource> writeSlaves = new HashMap<String, DataSource>();

	private Map<String, DataSource> readSlaves = new HashMap<String, DataSource>();

	private RoundRobin<String> readRoundRobin;

	private RoundRobin<String> writeRoundRobin;

	private Set<DataSource> deadDataSources = new HashSet<DataSource>();

	private Map<DataSource, Integer> failureCount = new ConcurrentHashMap<DataSource, Integer>();

	private int deadFailureThreshold = 3;

	@Autowired
	private BeanFactory beanFactory;

	public void setDeadFailureThreshold(int deadFailureThreshold) {
		this.deadFailureThreshold = deadFailureThreshold;
	}

	@Override
	public void setBeanName(String beanName) {
		this.groupName = beanName;
	}

	public void setMasterName(String masterName) {
		this.masterName = masterName;
	}

	public void setReadSlaveNames(Map<String, Integer> readSlaveNames) {
		this.readSlaveNames = readSlaveNames;
	}

	public void setWriteSlaveNames(Map<String, Integer> writeSlaveNames) {
		this.writeSlaveNames = writeSlaveNames;
	}

	public String getGroupName() {
		return groupName;
	}

	public void setMaxAttempts(int maxAttempts) {
		this.maxAttempts = maxAttempts;
	}

	public RoundRobin<String> getReadRoundRobin() {
		return readRoundRobin;
	}

	public RoundRobin<String> getWriteRoundRobin() {
		return writeRoundRobin;
	}

	@PostConstruct
	public void init() {
		if (masterName != null)
			master = (DataSource) beanFactory.getBean(masterName);
		if (writeSlaveNames != null && writeSlaveNames.size() > 0) {
			for (String name : writeSlaveNames.keySet())
				writeSlaves.put(name, (DataSource) beanFactory.getBean(name));
			if (masterName != null)
				writeSlaves.put(masterName, master);
			writeRoundRobin = new RoundRobin<String>(writeSlaveNames, new RoundRobin.UsableChecker<String>() {
				@Override
				public boolean isUsable(String target) {
					DataSource ds = writeSlaves.get(target);
					return !deadDataSources.contains(ds);
				}
			});
		}
		if (readSlaveNames != null && readSlaveNames.size() > 0) {
			for (String name : readSlaveNames.keySet())
				readSlaves.put(name, (DataSource) beanFactory.getBean(name));
			readRoundRobin = new RoundRobin<String>(readSlaveNames, new RoundRobin.UsableChecker<String>() {
				@Override
				public boolean isUsable(String target) {
					DataSource ds = readSlaves.get(target);
					return !deadDataSources.contains(ds);
				}
			});
		}
	}

	@Override
	public Connection getConnection(String username, String password) throws SQLException {
		return getConnection(username, password, maxAttempts);
	}

	public Connection getConnection(String username, String password, int attempts) throws SQLException {
		DataSource ds = null;
		String dbname = null;
		boolean read = false;
		if (DataRouteContext.isReadonly() && getReadRoundRobin() != null) {
			read = true;
			dbname = getReadRoundRobin().pick();
			ds = readSlaves.get(dbname);
		}
		if (ds == null && getWriteRoundRobin() != null) {
			dbname = getWriteRoundRobin().pick();
			ds = writeSlaves.get(dbname);
		}
		if (ds == null && masterName != null) {
			dbname = masterName;
			ds = master;
		}
		if (ds == null)
			throw new IllegalStateException("No underlying DataSource found");
		try {
			Connection conn = username == null ? ds.getConnection() : ds.getConnection(username, password);
			if (read)
				conn.setReadOnly(true);
			failureCount.remove(ds);
			StatLog.add(new Key("dataroute", true, groupName, dbname, "success"));
			return conn;
		} catch (SQLException e) {
			logger.error(e.getMessage(), e);
			if (--attempts < 1)
				throw e;
			Integer failureTimes = failureCount.get(ds);
			if (failureTimes == null)
				failureTimes = 1;
			else
				failureTimes += 1;
			if (failureTimes == deadFailureThreshold) {
				failureCount.remove(ds);
				deadDataSources.add(ds);
				logger.error("datasource [" + groupName + ":" + dbname + "] down!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
				StatLog.add(new Key("dataroute", false, groupName, dbname, "down"));
			} else {
				failureCount.put(ds, failureTimes);
			}
			StatLog.add(new Key("dataroute", true, groupName, dbname, "failed"));
			return getConnection(username, password, attempts);
		}
	}

	@Override
	public Connection getConnection() throws SQLException {
		return getConnection(null, null);
	}

	public void checkDeadDataSources() {
		Iterator<DataSource> it = deadDataSources.iterator();
		while (it.hasNext()) {
			DataSource ds = it.next();
			try {
				Connection conn = ds.getConnection();
				conn.close();
				it.remove();
				String dbname = null;
				for (Map.Entry<String, DataSource> entry : writeSlaves.entrySet()) {
					if (entry.getValue() == ds) {
						dbname = entry.getKey();
						break;
					}
				}
				if (dbname == null)
					for (Map.Entry<String, DataSource> entry : readSlaves.entrySet()) {
						if (entry.getValue() == ds) {
							dbname = entry.getKey();
							break;
						}
					}
				logger.warn("datasource[" + groupName + ":" + dbname + "] recovered");
			} catch (Exception e) {
				logger.debug(e.getMessage(), e);
			}
		}
	}

}