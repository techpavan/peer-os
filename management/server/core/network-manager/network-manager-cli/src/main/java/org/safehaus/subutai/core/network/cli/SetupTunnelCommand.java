package org.safehaus.subutai.core.network.cli;


import org.safehaus.subutai.core.network.api.NetworkManager;
import org.safehaus.subutai.core.network.api.NetworkManagerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;

import com.google.common.base.Preconditions;


@Command( scope = "net", name = "setup-tunnel", description = "Sets up tunnel with peer" )
public class SetupTunnelCommand extends OsgiCommandSupport
{
    private static final Logger LOG = LoggerFactory.getLogger( SetupTunnelCommand.class.getName() );

    private final NetworkManager networkManager;
    @Argument( index = 0, name = "tunnel name", required = true, multiValued = false,
            description = "tunnel name" )
    String tunnelName;
    @Argument( index = 1, name = "peer ip", required = true, multiValued = false,
            description = "peer ip" )
    String peerIp;
    @Argument( index = 2, name = "connection type", required = true, multiValued = false,
            description = "connection type" )
    String connectionType;


    public SetupTunnelCommand( final NetworkManager networkManager )
    {
        Preconditions.checkNotNull( networkManager );

        this.networkManager = networkManager;
    }


    @Override
    protected Object doExecute()
    {

        try
        {
            networkManager.setupTunnel( tunnelName, peerIp, connectionType );
            System.out.println( "OK" );
        }
        catch ( NetworkManagerException e )
        {
            System.out.println( e.getMessage() );
            LOG.error( "Error in SetupTunnelCommand", e );
        }

        return null;
    }
}
