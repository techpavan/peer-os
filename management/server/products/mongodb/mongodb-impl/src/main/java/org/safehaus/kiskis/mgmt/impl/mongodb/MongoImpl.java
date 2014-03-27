/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.kiskis.mgmt.impl.mongodb;

import org.safehaus.kiskis.mgmt.impl.mongodb.common.Tasks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.safehaus.kiskis.mgmt.api.agentmanager.AgentManager;
import org.safehaus.kiskis.mgmt.api.dbmanager.DbManager;
import org.safehaus.kiskis.mgmt.api.dbmanager.ProductOperation;
import org.safehaus.kiskis.mgmt.api.lxcmanager.LxcCreateException;
import org.safehaus.kiskis.mgmt.api.lxcmanager.LxcDestroyException;
import org.safehaus.kiskis.mgmt.api.lxcmanager.LxcManager;
import org.safehaus.kiskis.mgmt.api.taskrunner.Operation;
import org.safehaus.kiskis.mgmt.api.taskrunner.Task;
import org.safehaus.kiskis.mgmt.api.taskrunner.TaskCallback;
import org.safehaus.kiskis.mgmt.api.taskrunner.TaskRunner;
import org.safehaus.kiskis.mgmt.api.taskrunner.TaskStatus;
import org.safehaus.kiskis.mgmt.impl.mongodb.common.TaskType;
import org.safehaus.kiskis.mgmt.impl.mongodb.operation.AddDataNodeOperation;
import org.safehaus.kiskis.mgmt.impl.mongodb.operation.AddRouterOperation;
import org.safehaus.kiskis.mgmt.impl.mongodb.operation.InstallClusterOperation;
import org.safehaus.kiskis.mgmt.api.mongodb.Config;
import org.safehaus.kiskis.mgmt.api.mongodb.Mongo;
import org.safehaus.kiskis.mgmt.api.mongodb.NodeType;
import org.safehaus.kiskis.mgmt.api.taskrunner.Result;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;
import org.safehaus.kiskis.mgmt.shared.protocol.ProductOperationView;
import org.safehaus.kiskis.mgmt.shared.protocol.Request;
import org.safehaus.kiskis.mgmt.shared.protocol.Response;
import org.safehaus.kiskis.mgmt.shared.protocol.Util;
import org.safehaus.kiskis.mgmt.shared.protocol.enums.NodeState;

/**
 *
 * @author dilshat
 */
public class MongoImpl implements Mongo {

    private static final Logger LOG = Logger.getLogger(MongoImpl.class.getName());

    private static TaskRunner taskRunner;
    private static AgentManager agentManager;
    private static DbManager dbManager;
    private static LxcManager lxcManager;
    private static ExecutorService executor;

    public void setLxcManager(LxcManager lxcManager) {
        MongoImpl.lxcManager = lxcManager;
    }

    public void setAgentManager(AgentManager agentManager) {
        MongoImpl.agentManager = agentManager;
    }

    public void setDbManager(DbManager dbManager) {
        MongoImpl.dbManager = dbManager;
    }

    public void setTaskRunner(TaskRunner taskRunner) {
        MongoImpl.taskRunner = taskRunner;
    }

    public static TaskRunner getTaskRunner() {
        return taskRunner;
    }

    public static AgentManager getAgentManager() {
        return agentManager;
    }

    public static DbManager getDbManager() {
        return dbManager;
    }

    public static LxcManager getLxcManager() {
        return lxcManager;
    }

    public static ExecutorService getExecutor() {
        return executor;
    }

    public void init() {
        executor = Executors.newCachedThreadPool();
    }

    public void destroy() {
        MongoImpl.taskRunner = null;
        MongoImpl.agentManager = null;
        MongoImpl.dbManager = null;
        MongoImpl.lxcManager = null;
        executor.shutdown();
    }

    public UUID installCluster(final Config config) {
        final ProductOperation po
                = dbManager.createProductOperation(Config.PRODUCT_KEY,
                        String.format("Installing cluster %s", config.getClusterName()));
        if (po == null) {
            return null;
        }

        executor.execute(new Runnable() {

            public void run() {

                //check if mongo cluster with the same name already exists
                if (dbManager.getInfo(Config.PRODUCT_KEY, config.getClusterName(), Config.class) != null) {
                    po.addLogFailed(String.format("Cluster with name '%s' already exists\nInstallation aborted", config.getClusterName()));
                    return;
                }

                try {
                    int numberOfLxcsNeeded = config.getNumberOfConfigServers() + config.getNumberOfRouters() + config.getNumberOfDataNodes();
                    //clone lxc containers
                    po.addLog(String.format("Creating %d lxc containers", numberOfLxcsNeeded));
                    Map<Agent, Set<Agent>> lxcAgentsMap = lxcManager.createLxcs(numberOfLxcsNeeded);

                    Set<Agent> cfgServers = new HashSet<Agent>();
                    Set<Agent> routers = new HashSet<Agent>();
                    Set<Agent> dataNodes = new HashSet<Agent>();
                    for (int i = 0; i < numberOfLxcsNeeded; i++) {
                        Agent lxcAgent = null;
                        for (Iterator<Map.Entry<Agent, Set<Agent>>> it = lxcAgentsMap.entrySet().iterator(); it.hasNext();) {
                            Map.Entry<Agent, Set<Agent>> lxcAgents = it.next();
                            Iterator<Agent> it2 = lxcAgents.getValue().iterator();
                            if (it2.hasNext()) {
                                lxcAgent = it2.next();
                                it2.remove();
                                break;
                            } else {
                                it.remove();
                            }
                        }
                        if (cfgServers.size() < config.getNumberOfConfigServers()) {
                            cfgServers.add(lxcAgent);
                        } else if (routers.size() < config.getNumberOfRouters()) {
                            routers.add(lxcAgent);
                        } else if (dataNodes.size() < config.getNumberOfDataNodes()) {
                            dataNodes.add(lxcAgent);
                        } else {
                            break;
                        }
                    }
                    config.setConfigServers(cfgServers);
                    config.setDataNodes(dataNodes);
                    config.setRouterServers(routers);

                    if (dbManager.saveInfo(Config.PRODUCT_KEY, config.getClusterName(), config)) {
                        installMongoCluster(config, po);
                    } else {
                        //destroy all lxcs also

                        Set<String> lxcHostnames = new HashSet<String>();
                        for (Agent lxcAgent : config.getConfigServers()) {
                            lxcHostnames.add(lxcAgent.getHostname());
                        }
                        for (Agent lxcAgent : config.getRouterServers()) {
                            lxcHostnames.add(lxcAgent.getHostname());
                        }
                        for (Agent lxcAgent : config.getDataNodes()) {
                            lxcHostnames.add(lxcAgent.getHostname());
                        }
                        try {
                            lxcManager.destroyLxcs(lxcHostnames);
                        } catch (LxcDestroyException ex) {
                            po.addLogFailed("Could not save new cluster configuration to DB! Please see logs. Use LXC module to cleanup\nInstallation aborted");
                        }
                        po.addLogFailed("Could not save new cluster configuration to DB! Please see logs\nInstallation aborted");
                    }

                } catch (LxcCreateException ex) {
                    po.addLogFailed(ex.getMessage());
                }

            }
        });

        return po.getId();
    }

    private void installMongoCluster(final Config config, final ProductOperation po) {
        final Operation installOperation = new InstallClusterOperation(config);
        po.addLog(String.format("Running task %s", installOperation.peekNextTask().getDescription()));

        taskRunner.executeTask(installOperation.getNextTask(), new TaskCallback() {
            private final StringBuilder startConfigServersOutput = new StringBuilder();
            private final StringBuilder startRoutersOutput = new StringBuilder();
            private final StringBuilder startDataNodesOutput = new StringBuilder();

            @Override
            public Task onResponse(Task task, Response response, String stdOut, String stdErr) {

                if (task.getData() != null) {
                    boolean taskOk = false;
                    if (task.getData() == TaskType.START_CONFIG_SERVERS) {
                        startConfigServersOutput.append(response.getStdOut());
                        if (Util.countNumberOfOccurences(startConfigServersOutput.toString(),
                                "child process started successfully, parent exiting")
                                == config.getConfigServers().size()) {
                            taskOk = true;
                        }
                    } else if (task.getData() == TaskType.START_ROUTERS) {
                        startRoutersOutput.append(response.getStdOut());
                        if (Util.countNumberOfOccurences(startRoutersOutput.toString(),
                                "child process started successfully, parent exiting")
                                == config.getRouterServers().size()) {
                            taskOk = true;
                        }
                    } else if (task.getData() == TaskType.START_REPLICA_SET) {
                        startDataNodesOutput.append(response.getStdOut());
                        if (Util.countNumberOfOccurences(startDataNodesOutput.toString(),
                                "child process started successfully, parent exiting")
                                == config.getDataNodes().size()) {
                            taskOk = true;
                        }
                    }
                    if (taskOk) {
                        task.setCompleted(true);
                        task.setTaskStatus(TaskStatus.SUCCESS);
                        taskRunner.removeTaskCallback(task.getUuid());
                    }
                }

                if (task.isCompleted()) {
                    if (task.getTaskStatus() == TaskStatus.SUCCESS) {
                        po.addLog(String.format("Task %s succeeded", task.getDescription()));
                        if (installOperation.hasNextTask()) {
                            po.addLog(String.format("Running task %s", installOperation.peekNextTask().getDescription()));
                            return installOperation.getNextTask();
                        } else {
                            po.addLogDone(String.format("Operation %s completed", installOperation.getDescription()));

                        }
                    } else {
                        String err = "";
                        for (Map.Entry<UUID, Result> res : task.getResults().entrySet()) {
                            if (!Util.isStringEmpty(res.getValue().getStdErr())) {
                                err = res.getValue().getStdErr();
                                break;
                            }
                        }
                        po.addLogFailed(String.format("Task %s failed. Operation %s failed\n%s", task.getDescription(), installOperation.getDescription(), err));

                    }
                }

                return null;
            }
        });

    }

    public UUID addNode(final String clusterName, final NodeType nodeType) {
        final ProductOperation po
                = dbManager.createProductOperation(Config.PRODUCT_KEY,
                        String.format("Adding %s to %s", nodeType, clusterName));

        executor.execute(new Runnable() {

            public void run() {
                Config config = dbManager.getInfo(Config.PRODUCT_KEY, clusterName, Config.class);
                if (config == null) {
                    po.addLogFailed(String.format("Cluster with name %s does not exist", clusterName));
                    return;
                }
                if (nodeType == NodeType.DATA_NODE && config.getDataNodes().size() == 7) {
                    po.addLogFailed("Replica set cannot have more than 7 members.\nOperation aborted");
                    return;
                }
                try {

                    po.addLog("Creating lxc container");

                    Map<Agent, Set<Agent>> lxcAgentsMap = lxcManager.createLxcs(1);

                    Agent lxcAgent = lxcAgentsMap.entrySet().iterator().next().getValue().iterator().next();

                    //start addition of node
                    addNodeInternal(po, config, nodeType, lxcAgent);

                } catch (LxcCreateException ex) {
                    po.addLogFailed(ex.getMessage());
                }

            }
        });

        return po.getId();
    }

    private void addNodeInternal(final ProductOperation po, final Config config, final NodeType nodeType, final Agent agent) {
        final Operation operation
                = (nodeType == NodeType.DATA_NODE)
                ? new AddDataNodeOperation(config, agent)
                : new AddRouterOperation(config, agent);

        po.addLog(String.format("Running task %s", operation.peekNextTask().getDescription()));

        taskRunner.executeTask(operation.getNextTask(), new TaskCallback() {

            private final StringBuilder routersOutput = new StringBuilder();

            @Override
            public Task onResponse(Task task, Response response, String stdOut, String stdErr) {
                if (task.getData() == TaskType.FIND_PRIMARY_NODE) {

                    if (task.isCompleted()) {
                        Agent primaryNodeAgent = null;
                        Pattern p = Pattern.compile("primary\" : \"(.*)\"");
                        Matcher m = p.matcher(stdOut);
                        if (m.find()) {
                            String primaryNodeHost = m.group(1);
                            if (!Util.isStringEmpty(primaryNodeHost)) {
                                String hostname = primaryNodeHost.split(":")[0].replace("." + config.getDomainName(), "");
                                primaryNodeAgent = agentManager.getAgentByHostname(hostname);
                            }
                        }

                        if (primaryNodeAgent != null) {
                            Request registerSecondaryWithPrimaryCmd = operation.peekNextTask().getRequests().iterator().next();
                            registerSecondaryWithPrimaryCmd.setUuid(primaryNodeAgent.getUuid());
                        } else {
                            task.setTaskStatus(TaskStatus.FAIL);
                        }
                    }
                } else if (task.getData() == TaskType.START_REPLICA_SET
                        || task.getData() == TaskType.START_ROUTERS
                        || task.getData() == TaskType.START_CONFIG_SERVERS
                        || task.getData() == TaskType.RESTART_ROUTERS) {
                    if (task.getData() == TaskType.RESTART_ROUTERS && !Util.isStringEmpty(response.getStdOut())) {
                        routersOutput.append(response.getStdOut());
                    }

                    if ((task.getData() == TaskType.RESTART_ROUTERS
                            && Util.countNumberOfOccurences(routersOutput.toString(),
                                    "child process started successfully, parent exiting")
                            == config.getRouterServers().size())
                            || (task.getData() != TaskType.RESTART_ROUTERS
                            && stdOut.indexOf(
                                    "child process started successfully, parent exiting") > -1)) {
                        task.setTaskStatus(TaskStatus.SUCCESS);
                        task.setCompleted(true);
                        taskRunner.removeTaskCallback(task.getUuid());
                    }
                }

                if (task.isCompleted()) {
                    if (task.getTaskStatus() == TaskStatus.SUCCESS) {
                        po.addLog(String.format("Task %s succeeded", task.getDescription()));

                        if (operation.hasNextTask()) {
                            po.addLog(String.format("Running task %s", operation.peekNextTask().getDescription()));

                            return operation.getNextTask();
                        } else {
                            po.addLog(String.format("Operation %s completed", operation.getDescription()));

                            if (nodeType == NodeType.DATA_NODE) {
                                config.getDataNodes().add(agent);
                            } else if (nodeType == NodeType.CONFIG_NODE) {
                                config.getConfigServers().add(agent);
                            } else if (nodeType == NodeType.ROUTER_NODE) {
                                config.getRouterServers().add(agent);
                            }
                            if (dbManager.saveInfo(Config.PRODUCT_KEY, config.getClusterName(), config)) {
                                po.addLogDone("Cluster info updated in DB\nDone");
                            } else {
                                po.addLogFailed("Error while updating cluster info in DB. Check logs. Use LXC Module to cleanup\nFailed");
                            }
                        }
                    } else {
                        String err = "";
                        for (Map.Entry<UUID, Result> res : task.getResults().entrySet()) {
                            if (!Util.isStringEmpty(res.getValue().getStdErr())) {
                                err = res.getValue().getStdErr();
                                break;
                            }
                        }
                        po.addLogFailed(String.format("Task %s failed. Operation %s failed\n%s", task.getDescription(), operation.getDescription(), err));
                    }
                }

                return null;
            }
        });
    }

    public UUID uninstallCluster(final String clusterName) {
        final ProductOperation po
                = dbManager.createProductOperation(Config.PRODUCT_KEY,
                        String.format("Destroying cluster %s", clusterName));
        executor.execute(new Runnable() {

            public void run() {
                Config config = dbManager.getInfo(Config.PRODUCT_KEY, clusterName, Config.class);
                if (config == null) {
                    po.addLogFailed(String.format("Cluster with name %s does not exist", clusterName));
                    return;
                }
                Set<String> lxcHostnames = new HashSet<String>();
                for (Agent lxcAgent : config.getConfigServers()) {
                    lxcHostnames.add(lxcAgent.getHostname());
                }
                for (Agent lxcAgent : config.getRouterServers()) {
                    lxcHostnames.add(lxcAgent.getHostname());
                }
                for (Agent lxcAgent : config.getDataNodes()) {
                    lxcHostnames.add(lxcAgent.getHostname());
                }
                try {
                    lxcManager.destroyLxcs(lxcHostnames);
                    po.addLog("Lxc containers successfully destroyed");
                } catch (LxcDestroyException ex) {
                    po.addLog(String.format("%s, skipping...", ex.getMessage()));
                }

                if (dbManager.deleteInfo(Config.PRODUCT_KEY, config.getClusterName())) {
                    po.addLogDone("Cluster info deleted from DB\nDone");
                } else {
                    po.addLogFailed("Error while deleting cluster info from DB. Check logs.\nFailed");
                }
            }
        });

        return po.getId();
    }

    public UUID destroyNode(final String clusterName, final String lxcHostname) {
        final ProductOperation po
                = dbManager.createProductOperation(Config.PRODUCT_KEY,
                        String.format("Destroying %s in %s", lxcHostname, clusterName));

        //go on operation
        executor.execute(new Runnable() {

            public void run() {
                final Config config = dbManager.getInfo(Config.PRODUCT_KEY, clusterName, Config.class);
                if (config == null) {
                    po.addLogFailed(String.format("Cluster with name %s does not exist", clusterName));
                    return;
                }

                Agent agent = agentManager.getAgentByHostname(lxcHostname);
                if (agent == null) {
                    po.addLogFailed(String.format("Agent with hostname %s is not connected", lxcHostname));
                    return;
                }

                final NodeType nodeType = getNodeType(config, agent);
                if (nodeType == NodeType.CONFIG_NODE && config.getConfigServers().size() == 1) {
                    po.addLogFailed("This is the last configuration server in the cluster. Please, destroy cluster instead\n.Operation aborted");
                    return;
                } else if (nodeType == NodeType.DATA_NODE && config.getDataNodes().size() == 1) {
                    po.addLogFailed("This is the last data node in the cluster. Please, destroy cluster instead\n.Operation aborted");
                    return;
                } else if (nodeType == NodeType.ROUTER_NODE && config.getRouterServers().size() == 1) {
                    po.addLogFailed("This is the last router in the cluster. Please, destroy cluster instead\n.Operation aborted");
                    return;
                }

                if (nodeType == NodeType.CONFIG_NODE) {
                    config.getConfigServers().remove(agent);
                    //restart routers
                    po.addLog("Restarting routers...");
                    Task stopMongoTask = taskRunner.
                            executeTask(Tasks.getStopMongoTask(config.getRouterServers()));
                    //don't check status of this task since this task always ends with execute_timeouted
                    if (stopMongoTask.isCompleted()) {
                        Task startRoutersTask = taskRunner.
                                executeTask(Tasks.getStartRoutersTask(config.getRouterServers(),
                                                config.getConfigServers(), config));

                        final AtomicInteger okCount = new AtomicInteger(0);
                        taskRunner.executeTask(startRoutersTask, new TaskCallback() {

                            public Task onResponse(Task task, Response response, String stdOut, String stdErr) {
                                if (stdOut.indexOf("child process started successfully, parent exiting") > -1) {

                                    okCount.incrementAndGet();
                                }
                                if (okCount.get() == config.getRouterServers().size()) {
                                    taskRunner.removeTaskCallback(task.getUuid());
                                    synchronized (task) {
                                        task.notifyAll();
                                    }
                                } else if (task.isCompleted()) {
                                    synchronized (task) {
                                        task.notifyAll();
                                    }
                                }
                                return null;
                            }
                        });

                        synchronized (startRoutersTask) {
                            try {
                                startRoutersTask.wait(startRoutersTask.getAvgTimeout() * 1000 + 1000);
                            } catch (InterruptedException ex) {
                            }
                        }

                        if (okCount.get() != config.getRouterServers().size()) {
                            po.addLog("Not all routers restarted. Use Terminal module to restart them, skipping...");
                        }
                    } else {
                        po.addLog("Could not restart routers. Use Terminal module to restart them, skipping...");
                    }

                } else if (nodeType == NodeType.DATA_NODE) {
                    config.getDataNodes().remove(agent);
                    //unregister from primary
                    po.addLog("Unregistering this node from replica set...");
                    Task findPrimaryNodeTask = taskRunner.
                            executeTask(Tasks.getFindPrimaryNodeTask(agent, config));

                    if (findPrimaryNodeTask.isCompleted()) {
                        Pattern p = Pattern.compile("primary\" : \"(.*)\"");
                        Matcher m = p.matcher(findPrimaryNodeTask.getResults().entrySet().iterator().next().getValue().getStdOut());
                        Agent primaryNodeAgent = null;
                        if (m.find()) {
                            String primaryNodeHost = m.group(1);
                            if (!Util.isStringEmpty(primaryNodeHost)) {
                                String hostname = primaryNodeHost.split(":")[0].replace("." + config.getDomainName(), "");
                                primaryNodeAgent = agentManager.getAgentByHostname(hostname);
                            }
                        }
                        if (primaryNodeAgent != null) {
                            if (primaryNodeAgent != agent) {
                                Task unregisterSecondaryNodeFromPrimaryTask
                                        = taskRunner.
                                        executeTask(
                                                Tasks.getUnregisterSecondaryFromPrimaryTask(
                                                        primaryNodeAgent, agent, config));
                                if (unregisterSecondaryNodeFromPrimaryTask.getTaskStatus() != TaskStatus.SUCCESS) {
                                    po.addLog("Could not unregister this node from replica set, skipping...");
                                }
                            }
                        } else {
                            po.addLog("Could not determine primary node for unregistering from replica set, skipping...");
                        }
                    } else {
                        po.addLog("Could not determine primary node for unregistering from replica set, skipping...");
                    }

                } else if (nodeType == NodeType.ROUTER_NODE) {
                    config.getRouterServers().remove(agent);
                }
                //destroy lxc
                po.addLog("Destroying lxc container...");
                Agent physicalAgent = agentManager.getAgentByHostname(agent.getParentHostName());
                if (physicalAgent == null) {
                    po.addLog(
                            String.format("Could not determine physical parent of %s. Use LXC module to cleanup, skipping...",
                                    agent.getHostname()));
                } else {
                    if (!lxcManager.destroyLxcOnHost(physicalAgent, agent.getHostname())) {
                        po.addLog("Could not destroy lxc container. Use LXC module to cleanup, skipping...");
                    }
                }
                //update db
                po.addLog("Updating db...");
                if (!dbManager.saveInfo(Config.PRODUCT_KEY, config.getClusterName(), config)) {
                    po.addLogFailed(String.format("Error while updating cluster info [%s] in DB. Check logs\nFailed",
                            config.getClusterName()));
                } else {
                    po.addLogDone("Done");
                }
            }
        });

        return po.getId();
    }

    public List<Config> getClusters() {
        try {

            return dbManager.getInfo(Config.PRODUCT_KEY, Config.class);

        } catch (Exception ex) {
            LOG.log(Level.SEVERE, "Error in getClusters", ex);
        }

        return new ArrayList<Config>();
    }

    public UUID startNode(final String clusterName, final String lxcHostname) {
        final ProductOperation po
                = dbManager.createProductOperation(Config.PRODUCT_KEY,
                        String.format("Starting node %s in %s", lxcHostname, clusterName));

        executor.execute(new Runnable() {

            public void run() {
                Config config = dbManager.getInfo(Config.PRODUCT_KEY, clusterName, Config.class);
                if (config == null) {
                    po.addLogFailed(String.format("Cluster with name %s does not exist", clusterName));
                    return;
                }

                Agent node = agentManager.getAgentByHostname(lxcHostname);
                if (node == null) {
                    po.addLogFailed(String.format("Agent with hostname %s is not connected", lxcHostname));
                    return;
                }

                Task startNodeTask;
                NodeType nodeType = getNodeType(config, node);

                if (nodeType == NodeType.CONFIG_NODE) {
                    startNodeTask = Tasks.getStartConfigServersTask(
                            Util.wrapAgentToSet(node), config);

                } else if (nodeType == NodeType.DATA_NODE) {
                    startNodeTask = Tasks.getStartReplicaSetTask(
                            Util.wrapAgentToSet(node), config);
                } else {
                    startNodeTask = Tasks.getStartRoutersTask(
                            Util.wrapAgentToSet(node),
                            config.getConfigServers(),
                            config);
                }

                taskRunner.executeTask(startNodeTask, new TaskCallback() {

                    public Task onResponse(Task task, Response response, String stdOut, String stdErr) {
                        if (stdOut.indexOf("child process started successfully, parent exiting") > -1) {

                            taskRunner.removeTaskCallback(task.getUuid());
                            task.setData(NodeState.RUNNING);
                            synchronized (task) {
                                task.notifyAll();
                            }
                        } else if (task.isCompleted()) {
                            synchronized (task) {
                                task.notifyAll();
                            }
                        }
                        return null;
                    }
                });

                synchronized (startNodeTask) {
                    try {
                        startNodeTask.wait(startNodeTask.getAvgTimeout() * 1000 + 1000);
                    } catch (InterruptedException ex) {
                    }
                }

                if (NodeState.RUNNING.equals(startNodeTask.getData())) {
                    po.addLogDone(String.format("Node on %s started", lxcHostname));
                } else {
                    po.addLogFailed(String.format("Failed to start node %s. %s",
                            lxcHostname,
                            startNodeTask.getResults().entrySet().iterator().next().getValue().getStdErr()
                    ));
                }

            }

        });

//        return NodeState.RUNNING.equals(startNodeTask.getData());
        return po.getId();
    }

    public UUID stopNode(final String clusterName, final String lxcHostname) {
        final ProductOperation po
                = dbManager.createProductOperation(Config.PRODUCT_KEY,
                        String.format("Stopping node %s in %s", lxcHostname, clusterName));

        executor.execute(new Runnable() {

            public void run() {
                Config config = dbManager.getInfo(Config.PRODUCT_KEY, clusterName, Config.class);
                if (config == null) {
                    po.addLogFailed(String.format("Cluster with name %s does not exist", clusterName));
                    return;
                }

                Agent node = agentManager.getAgentByHostname(lxcHostname);
                if (node == null) {
                    po.addLogFailed(String.format("Agent with hostname %s is not connected", lxcHostname));
                    return;
                }

                Task stopNodeTask = taskRunner.executeTask(
                        Tasks.getStopMongoTask(Util.wrapAgentToSet(node)));

                if (stopNodeTask.isCompleted()) {
                    if (stopNodeTask.getTaskStatus() == TaskStatus.SUCCESS) {
                        po.addLogDone(String.format("Node on %s stopped", lxcHostname));
                    } else {
                        po.addLogFailed(String.format("Failed to stop node %s. %s",
                                lxcHostname,
                                stopNodeTask.getResults().entrySet().iterator().next().getValue().getStdErr()
                        ));
                    }
                }

            }
        });

        return po.getId();
    }

    public UUID checkNode(final String clusterName, final String lxcHostname) {
        final ProductOperation po
                = dbManager.createProductOperation(Config.PRODUCT_KEY,
                        String.format("Checking state of %s in %s", lxcHostname, clusterName));

        executor.execute(new Runnable() {

            public void run() {
                Config config = dbManager.getInfo(Config.PRODUCT_KEY, clusterName, Config.class);
                if (config == null) {
                    po.addLogFailed(String.format("Cluster with name %s does not exist", clusterName));
                    return;
                }

                Agent node = agentManager.getAgentByHostname(lxcHostname);
                if (node == null) {
                    po.addLogFailed(String.format("Agent with hostname %s is not connected", lxcHostname));
                    return;
                }

                Task checkNodeTask = taskRunner.executeTask(
                        Tasks.getCheckStatusTask(Util.wrapAgentToSet(node), getNodeType(config, node), config));

                if (checkNodeTask.isCompleted()) {
                    String stdOut = checkNodeTask.getResults().entrySet().iterator().next().getValue().getStdOut();
                    if (stdOut.indexOf("couldn't connect to server") > -1) {
                        po.addLogDone(String.format("Node on %s is stopped", lxcHostname));
                    } else if (stdOut.indexOf("connecting to") > -1) {
                        po.addLogDone(String.format("Node on %s is running", lxcHostname));
                    } else {
                        po.addLogFailed(String.format("Node on %s is not found", lxcHostname));
                    }
                }

            }
        });

        return po.getId();
    }

    public ProductOperationView getProductOperationView(UUID viewId) {
        return dbManager.getProductOperation(Config.PRODUCT_KEY, viewId);
    }

    private NodeType getNodeType(Config config, Agent node) {
        NodeType nodeType = NodeType.DATA_NODE;

        if (config.getRouterServers().contains(node)) {
            nodeType = NodeType.ROUTER_NODE;
        } else if (config.getConfigServers().contains(node)) {
            nodeType = NodeType.CONFIG_NODE;
        }

        return nodeType;
    }

}
