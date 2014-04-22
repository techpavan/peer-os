/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.kiskis.mgmt.ui.cassandra;

import com.vaadin.ui.Component;
import org.safehaus.kiskis.mgmt.api.agentmanager.AgentManager;
import org.safehaus.kiskis.mgmt.api.cassandra.Cassandra;
import org.safehaus.kiskis.mgmt.api.tracker.Tracker;
import org.safehaus.kiskis.mgmt.server.ui.services.Module;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author dilshat
 */
public class CassandraUI implements Module {

    public static final String MODULE_NAME = "Cassandra";
    private static Cassandra cassandraManager;
    private static AgentManager agentManager;
    private static Tracker tracker;
    private static ExecutorService executor;

    public static Tracker getTracker() {
        return tracker;
    }

    public void setTracker(Tracker tracker) {
        CassandraUI.tracker = tracker;
    }

    public static Cassandra getCassandraManager() {
        return cassandraManager;
    }

    public static ExecutorService getExecutor() {
        return executor;
    }

    public void setSolrManager(Cassandra solrManager) {
        CassandraUI.cassandraManager = solrManager;
    }

    public static AgentManager getAgentManager() {
        return agentManager;
    }

    public void setAgentManager(AgentManager agentManager) {
        CassandraUI.agentManager = agentManager;
    }

    public void init() {
        executor = Executors.newCachedThreadPool();
    }

    public void destroy() {
        cassandraManager = null;
        agentManager = null;
        tracker = null;
        executor.shutdown();
    }

    public String getName() {
        return MODULE_NAME;
    }

    public Component createComponent() {
        return new CassandraForm();
    }

}
