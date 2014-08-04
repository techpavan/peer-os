package org.safehaus.subutai.plugin.mongodb.impl.handler;


import java.util.UUID;

import org.safehaus.subutai.api.commandrunner.Command;
import org.safehaus.subutai.plugin.mongodb.api.MongoClusterConfig;
import org.safehaus.subutai.plugin.mongodb.impl.MongoImpl;
import org.safehaus.subutai.plugin.mongodb.impl.common.Commands;
import org.safehaus.subutai.shared.operation.AbstractOperationHandler;
import org.safehaus.subutai.shared.operation.ProductOperation;
import org.safehaus.subutai.shared.protocol.Agent;
import org.safehaus.subutai.shared.protocol.Util;


/**
 * Handles stop mongo node operation
 */
public class StopNodeOperationHandler extends AbstractOperationHandler<MongoImpl> {
    private final ProductOperation po;
    private final String lxcHostname;


    public StopNodeOperationHandler( MongoImpl manager, String clusterName, String lxcHostname ) {
        super( manager, clusterName );
        this.lxcHostname = lxcHostname;
        po = manager.getTracker().createProductOperation( MongoClusterConfig.PRODUCT_KEY,
                String.format( "Stopping node %s in %s", lxcHostname, clusterName ) );
    }


    @Override
    public UUID getTrackerId() {
        return po.getId();
    }


    @Override
    public void run() {
        MongoClusterConfig config = manager.getCluster( clusterName );
        if ( config == null ) {
            po.addLogFailed( String.format( "Cluster with name %s does not exist", clusterName ) );
            return;
        }

        Agent node = manager.getAgentManager().getAgentByHostname( lxcHostname );
        if ( node == null ) {
            po.addLogFailed( String.format( "Agent with hostname %s is not connected", lxcHostname ) );
            return;
        }
        if ( !config.getAllNodes().contains( node ) ) {
            po.addLogFailed(
                    String.format( "Agent with hostname %s does not belong to cluster %s", lxcHostname, clusterName ) );
            return;
        }

        po.addLog( "Stopping node..." );
        Command stopNodeCommand = Commands.getStopNodeCommand( Util.wrapAgentToSet( node ) );
        manager.getCommandRunner().runCommand( stopNodeCommand );

        if ( stopNodeCommand.hasSucceeded() ) {
            po.addLogDone( String.format( "Node on %s stopped", lxcHostname ) );
        }
        else {
            po.addLogFailed(
                    String.format( "Failed to stop node %s. %s", lxcHostname, stopNodeCommand.getAllErrors() ) );
        }
    }
}
