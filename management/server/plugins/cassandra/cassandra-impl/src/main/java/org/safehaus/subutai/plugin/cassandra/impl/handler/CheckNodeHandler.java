package org.safehaus.subutai.plugin.cassandra.impl.handler;


import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.safehaus.subutai.common.exception.CommandException;
import org.safehaus.subutai.common.protocol.AbstractOperationHandler;
import org.safehaus.subutai.common.protocol.CommandResult;
import org.safehaus.subutai.common.protocol.RequestBuilder;
import org.safehaus.subutai.common.tracker.TrackerOperation;
import org.safehaus.subutai.core.command.api.command.AgentResult;
import org.safehaus.subutai.core.command.api.command.Command;
import org.safehaus.subutai.core.environment.api.helper.Environment;
import org.safehaus.subutai.core.peer.api.ContainerHost;
import org.safehaus.subutai.plugin.cassandra.api.CassandraClusterConfig;
import org.safehaus.subutai.plugin.cassandra.impl.CassandraImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CheckNodeHandler extends AbstractOperationHandler<CassandraImpl>
{

    private static final Logger LOG = LoggerFactory.getLogger( CheckServiceHandler.class.getName() );
    private String clusterName;
    private UUID agentUUID;


    public CheckNodeHandler( final CassandraImpl manager, final String clusterName, UUID agentUUID )
    {
        super( manager, clusterName );
        this.agentUUID = agentUUID;
        this.clusterName = clusterName;
        trackerOperation = manager.getTracker().createTrackerOperation( CassandraClusterConfig.PRODUCT_KEY,
                String.format( "Checking %s cluster...", clusterName ) );
    }


    @Override
    public void run()
    {
        CassandraClusterConfig config = manager.getCluster( clusterName );
        if ( config == null )
        {
            trackerOperation.addLogFailed( String.format( "Cluster with name %s does not exist", clusterName ) );
            return;
        }

        Environment environment = manager.getEnvironmentManager().getEnvironmentByUUID( config.getEnvironmentId() );
        Iterator iterator = environment.getContainers().iterator();

        ContainerHost host = null;
        while ( iterator.hasNext() )
        {
            host = ( ContainerHost ) iterator.next();
            if ( host.getId().equals( agentUUID ) )
            {
                break;
            }
        }

        if ( host == null )
        {
            trackerOperation.addLogFailed( String.format( "No Container with ID %s", agentUUID ) );
            return;
        }

        if ( !config.getNodes().contains( UUID.fromString( host.getId().toString() ) ) )
        {
            trackerOperation.addLogFailed(
                    String.format( "Agent with ID %s does not belong to cluster %s", host.getId(), clusterName ) );
            return;
        }

        try
        {

            CommandResult result = host.execute( new RequestBuilder( "service cassandra status" ) );
            if ( result.getExitCode() == 0 )
            {
                if ( result.getStdOut().contains( "running..." ) )
                {
                    trackerOperation.addLogDone( "Service running" );
                }
                else
                {
                    trackerOperation.addLogFailed( String.format( "Unexpected result, %s", result.getStdErr() ) );
                }
            }
            else
            {
                trackerOperation.addLogFailed( String.format( "Checking service failed, %s", result.getStdErr() ) );
            }
        }
        catch ( CommandException e )
        {
            trackerOperation.addLogFailed( String.format( "Error running command, %s", e.getMessage() ) );
            return;
        }
    }


    private void logStatusResults( TrackerOperation po, Command checkStatusCommand )
    {

        StringBuilder log = new StringBuilder();

        for ( Map.Entry<UUID, AgentResult> e : checkStatusCommand.getResults().entrySet() )
        {

            String status = "UNKNOWN";
            if ( e.getValue().getExitCode() == 0 )
            {
                status = "Cassandra is running";
            }
            else if ( e.getValue().getExitCode() == 768 )
            {
                status = "Cassandra is not running";
            }

            log.append( String.format( "%s", status ) );
        }
        po.addLogDone( log.toString() );
    }
}
