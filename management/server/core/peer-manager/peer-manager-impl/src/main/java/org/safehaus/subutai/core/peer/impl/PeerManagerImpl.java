package org.safehaus.subutai.core.peer.impl;


import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.safehaus.subutai.core.agent.api.AgentManager;
import org.safehaus.subutai.core.executor.api.CommandExecutor;
import org.safehaus.subutai.core.hostregistry.api.HostRegistry;
import org.safehaus.subutai.core.lxc.quota.api.QuotaManager;
import org.safehaus.subutai.core.messenger.api.Messenger;
import org.safehaus.subutai.core.peer.api.LocalPeer;
import org.safehaus.subutai.core.peer.api.ManagementHost;
import org.safehaus.subutai.core.peer.api.Peer;
import org.safehaus.subutai.core.peer.api.PeerException;
import org.safehaus.subutai.core.peer.api.PeerGroup;
import org.safehaus.subutai.core.peer.api.PeerInfo;
import org.safehaus.subutai.core.peer.api.PeerManager;
import org.safehaus.subutai.core.peer.api.RequestListener;
import org.safehaus.subutai.core.peer.impl.command.CommandRequestListener;
import org.safehaus.subutai.core.peer.impl.command.CommandResponseListener;
import org.safehaus.subutai.core.peer.impl.container.CreateContainerRequestListener;
import org.safehaus.subutai.core.peer.impl.dao.PeerDAO;
import org.safehaus.subutai.core.peer.impl.request.MessageRequestListener;
import org.safehaus.subutai.core.peer.impl.request.MessageResponseListener;
import org.safehaus.subutai.core.registry.api.TemplateRegistry;
import org.safehaus.subutai.core.strategy.api.StrategyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;


/**
 * PeerManager implementation
 */
public class PeerManagerImpl implements PeerManager
{

    private static final Logger LOG = LoggerFactory.getLogger( PeerManagerImpl.class.getName() );
    private static final String SOURCE_REMOTE_PEER = "PEER_REMOTE";
    private static final String SOURCE_LOCAL_PEER = "PEER_LOCAL";
    private static final String PEER_GROUP = "PEER_GROUP";
    private AgentManager agentManager;
    private PeerDAO peerDAO;
    private QuotaManager quotaManager;
    private TemplateRegistry templateRegistry;
    private DataSource dataSource;
    //    private CommunicationManager communicationManager;
    private CommandExecutor commandExecutor;
    private LocalPeer localPeer;
    private StrategyManager strategyManager;
    private PeerInfo peerInfo;
    private Messenger messenger;
    private CommandResponseListener commandResponseListener;
    private Set<RequestListener> requestListeners = Sets.newHashSet();
    private MessageResponseListener messageResponseListener;
    private EntityManager entityManager;
    private HostRegistry hostRegistry;


    public PeerManagerImpl( final DataSource dataSource, final Messenger messenger )
    {
        Preconditions.checkNotNull( dataSource, "Data source is null" );
        this.dataSource = dataSource;
        this.messenger = messenger;
    }


    public void setEntityManagerFactory( EntityManagerFactory entityManagerFactory )
    {
        this.entityManager = entityManagerFactory.createEntityManager();
    }


    public void setHostRegistry( final HostRegistry hostRegistry )
    {
        this.hostRegistry = hostRegistry;
    }


    public EntityManager getEntityManager()
    {
        return entityManager;
    }


    public void init()
    {
        try
        {
            this.peerDAO = new PeerDAO( dataSource );
        }
        catch ( SQLException e )
        {
            LOG.error( e.getMessage(), e );
        }

        List<PeerInfo> result = peerDAO.getInfo( SOURCE_LOCAL_PEER, PeerInfo.class );
        if ( result.isEmpty() )
        {
            peerInfo = new PeerInfo();
            peerInfo.setId( generatePeerId() );
            peerInfo.setName( "Local Subutai server" );
            peerInfo.setOwnerId( UUID.randomUUID() );
            peerDAO.saveInfo( SOURCE_LOCAL_PEER, peerInfo.getId().toString(), peerInfo );
        }
        else
        {
            peerInfo = result.get( 0 );
        }
        localPeer = new LocalPeerImpl( this, agentManager, templateRegistry, peerDAO, quotaManager, strategyManager,
                requestListeners, commandExecutor, hostRegistry );
        localPeer.init();

        //add command request listener
        addRequestListener( new CommandRequestListener( localPeer, this ) );
        //add command response listener
        commandResponseListener = new CommandResponseListener();
        addRequestListener( commandResponseListener );
        //subscribe to peer message requests
        messenger.addMessageListener( new MessageRequestListener( this, messenger, requestListeners ) );
        //subscribe to peer message responses
        messageResponseListener = new MessageResponseListener();
        messenger.addMessageListener( messageResponseListener );
        //add create container requests listener
        addRequestListener( new CreateContainerRequestListener( localPeer ) );
        //add echo listener
        addRequestListener( new EchoRequestListener() );
    }


    public void destroy()
    {
        localPeer.shutdown();
    }
    //
    //
    //    public void setCommunicationManager( final CommunicationManager communicationManager )
    //    {
    //        this.communicationManager = communicationManager;
    //    }


    public void setCommandExecutor( final CommandExecutor commandExecutor )
    {
        this.commandExecutor = commandExecutor;
    }


    public void setStrategyManager( final StrategyManager strategyManager )
    {
        this.strategyManager = strategyManager;
    }


    public void setAgentManager( final AgentManager agentManager )
    {
        this.agentManager = agentManager;
    }


    public void setTemplateRegistry( final TemplateRegistry templateRegistry )
    {
        this.templateRegistry = templateRegistry;
    }


    public void setQuotaManager( final QuotaManager quotaManager )
    {
        this.quotaManager = quotaManager;
    }


    @Override
    public boolean register( final PeerInfo peerInfo ) throws PeerException
    {
        ManagementHost managementHost = getLocalPeer().getManagementHost();
        managementHost.addAptSource( peerInfo.getId().toString(), peerInfo.getIp() );
        return peerDAO.saveInfo( SOURCE_REMOTE_PEER, peerInfo.getId().toString(), peerInfo );
    }


    @Override
    public boolean update( final PeerInfo peerInfo )
    {
        return peerDAO.saveInfo( SOURCE_REMOTE_PEER, peerInfo.getId().toString(), peerInfo );
    }


    private UUID generatePeerId()
    {
        return UUID.randomUUID();
    }


    @Override
    public List<PeerInfo> peers()
    {
        return peerDAO.getInfo( SOURCE_REMOTE_PEER, PeerInfo.class );
    }


    @Override
    public List<Peer> getPeers()
    {
        List<PeerInfo> peerInfoList = peerDAO.getInfo( SOURCE_REMOTE_PEER, PeerInfo.class );
        List<Peer> result = Lists.newArrayList();
        result.add( getLocalPeer() );
        for ( PeerInfo info : peerInfoList )
        {
            result.add( getPeer( info.getId() ) );
        }

        return result;
    }


    @Override
    public boolean unregister( final String uuid ) throws PeerException
    {
        ManagementHost managementHost = getLocalPeer().getManagementHost();
        PeerInfo p = getPeerInfo( UUID.fromString( uuid ) );
        managementHost.removeAptSource( p.getId().toString(), p.getIp() );
        return peerDAO.deleteInfo( SOURCE_REMOTE_PEER, uuid );
    }


    @Override
    public PeerInfo getPeerInfo( UUID uuid )
    {
        return peerDAO.getInfo( SOURCE_REMOTE_PEER, uuid.toString(), PeerInfo.class );
    }


    @Override
    public List<PeerGroup> peersGroups()
    {
        List<PeerGroup> peerGroups = peerDAO.getInfo( PEER_GROUP, PeerGroup.class );
        /*Set<PeerGroup> peerGroups = new HashSet<>();
        for ( int i = 0; i < 10; i++ )
        {
            PeerGroup peerGroup = new PeerGroup();
            peerGroup.setName( "Group " + i );
            for ( int j = 0; j < 10; j++ )
            {
                peerGroup.addPeerUUID( UUID.randomUUID() );
            }
            peerGroups.add( peerGroup );
        }*/
        return peerGroups;
    }


    @Override
    public void deletePeerGroup( final PeerGroup group )
    {
        peerDAO.deleteInfo( PEER_GROUP, group.getUUID().toString() );
    }


    @Override
    public boolean savePeerGroup( final PeerGroup group )
    {
        return peerDAO.saveInfo( PEER_GROUP, group.getId().toString(), group );
    }


    @Override
    public PeerGroup getPeerGroup( final UUID peerGroupId )
    {
        return peerDAO.getInfo( PEER_GROUP, peerGroupId.toString(), PeerGroup.class );
    }


    @Override
    public Peer getPeer( final UUID peerId )
    {
        if ( peerInfo.getId().equals( peerId ) )
        {
            return localPeer;
        }

        PeerInfo peerInfo = getPeerInfo( peerId );

        if ( peerInfo != null )
        {
            return new RemotePeerImpl( localPeer, peerInfo, messenger, commandResponseListener,
                    messageResponseListener );
        }
        return null;
    }


    @Override
    public LocalPeer getLocalPeer()
    {
        return localPeer;
    }


    @Override
    public PeerInfo getLocalPeerInfo()
    {
        return peerInfo;
    }


    @Override
    public void addRequestListener( RequestListener listener )
    {
        if ( listener != null )
        {
            requestListeners.add( listener );
        }
    }


    @Override
    public void removeRequestListener( RequestListener listener )
    {
        if ( listener != null )
        {
            requestListeners.add( listener );
        }
    }
}
