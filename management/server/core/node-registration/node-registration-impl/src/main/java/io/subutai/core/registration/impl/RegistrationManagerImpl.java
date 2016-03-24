package io.subutai.core.registration.impl;


import java.sql.Timestamp;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bouncycastle.openpgp.PGPPublicKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.Transformer;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.cxf.jaxrs.client.WebClient;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import io.subutai.common.command.CommandException;
import io.subutai.common.command.CommandResult;
import io.subutai.common.command.RequestBuilder;
import io.subutai.common.dao.DaoManager;
import io.subutai.common.host.ContainerHostInfo;
import io.subutai.common.host.ContainerHostInfoModel;
import io.subutai.common.host.HeartBeat;
import io.subutai.common.host.HeartbeatListener;
import io.subutai.common.host.HostInfo;
import io.subutai.common.host.HostInfoModel;
import io.subutai.common.host.HostInterface;
import io.subutai.common.host.HostInterfaceModel;
import io.subutai.common.host.HostInterfaces;
import io.subutai.common.host.InstanceType;
import io.subutai.common.host.ResourceHostInfoModel;
import io.subutai.common.peer.Host;
import io.subutai.common.peer.HostNotFoundException;
import io.subutai.common.peer.LocalPeer;
import io.subutai.common.peer.Peer;
import io.subutai.common.peer.PeerException;
import io.subutai.common.settings.SystemSettings;
import io.subutai.common.util.P2PUtil;
import io.subutai.common.util.RestUtil;
import io.subutai.core.registration.api.RegistrationManager;
import io.subutai.core.registration.api.RegistrationStatus;
import io.subutai.core.registration.api.exception.NodeRegistrationException;
import io.subutai.core.registration.api.service.ContainerInfo;
import io.subutai.core.registration.api.service.ContainerToken;
import io.subutai.core.registration.api.service.RequestedHost;
import io.subutai.core.registration.impl.dao.ContainerTokenDataService;
import io.subutai.core.registration.impl.dao.RequestDataService;
import io.subutai.core.registration.impl.entity.ContainerTokenImpl;
import io.subutai.core.registration.impl.entity.RequestedHostImpl;
import io.subutai.core.security.api.SecurityManager;
import io.subutai.core.security.api.crypto.EncryptionTool;
import io.subutai.core.security.api.crypto.KeyManager;


public class RegistrationManagerImpl implements RegistrationManager
{
    private static final Logger LOG = LoggerFactory.getLogger( RegistrationManagerImpl.class );
    private SecurityManager securityManager;
    private RequestDataService requestDataService;
    private ContainerTokenDataService containerTokenDataService;
    private DaoManager daoManager;
    private LocalPeer localPeer;
    protected Set<HeartbeatListener> listeners =
            Collections.newSetFromMap( new ConcurrentHashMap<HeartbeatListener, Boolean>() );
    protected ExecutorService notifier = Executors.newCachedThreadPool();


    public RegistrationManagerImpl( final SecurityManager securityManager, final DaoManager daoManager,
                                    final LocalPeer localPeer )
    {
        this.securityManager = securityManager;
        this.daoManager = daoManager;
        this.localPeer = localPeer;
    }


    public void addListener( HeartbeatListener listener )
    {
        if ( listener != null )
        {
            listeners.add( listener );
        }
    }


    public void removeListener( HeartbeatListener listener )
    {
        if ( listener != null )
        {
            listeners.remove( listener );
        }
    }


    public void init()
    {
        containerTokenDataService = new ContainerTokenDataService( daoManager );
        requestDataService = new RequestDataService( daoManager );
    }


    public void dispose()
    {
        notifier.shutdown();
    }


    public void processHeartbeat( final HeartBeat heartbeat )
    {
        try
        {
            for ( final HeartbeatListener listener : listeners )
            {
                notifier.submit( new Runnable()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            listener.onHeartbeat( heartbeat );
                        }
                        catch ( Exception e )
                        {
                            LOG.error( "Error notifying listener #processHeartbeat", e );
                        }
                    }
                } );
            }
        }
        catch ( Exception e )
        {
            LOG.error( "Error in #processHeartbeat", e );
        }
    }


    public RequestDataService getRequestDataService()
    {
        return requestDataService;
    }


    public void setRequestDataService( final RequestDataService requestDataService )
    {
        Preconditions.checkNotNull( requestDataService, "RequestDataService shouldn't be null." );

        this.requestDataService = requestDataService;
    }


    @Override
    public List<RequestedHost> getRequests()
    {
        List<RequestedHost> temp = Lists.newArrayList();
        temp.addAll( requestDataService.getAll() );
        return temp;
    }


    @Override
    public RequestedHost getRequest( final String requestId )
    {
        return requestDataService.find( requestId );
    }


    @Override
    public void queueRequest( final RequestedHost requestedHost ) throws NodeRegistrationException
    {
        if ( requestDataService.find( requestedHost.getId() ) != null )
        {
            LOG.info( "Already requested registration" );
        }
        else
        {
            RequestedHostImpl registrationRequest = new RequestedHostImpl( requestedHost );
            registrationRequest.setStatus( RegistrationStatus.REQUESTED );
            try
            {
                requestDataService.update( registrationRequest );
            }
            catch ( Exception ex )
            {
                throw new NodeRegistrationException( "Failed adding resource host registration request to queue", ex );
            }
            checkManagement( registrationRequest );
        }
    }


    @Override
    public void rejectRequest( final String requestId )
    {
        RequestedHostImpl registrationRequest = requestDataService.find( requestId );
        registrationRequest.setStatus( RegistrationStatus.REJECTED );
        requestDataService.update( registrationRequest );

        WebClient client = RestUtil.createWebClient( registrationRequest.getRestHook() );

        EncryptionTool encryptionTool = securityManager.getEncryptionTool();
        KeyManager keyManager = securityManager.getKeyManager();

        String message = RegistrationStatus.REJECTED.name();
        PGPPublicKey publicKey = keyManager.getPublicKey( registrationRequest.getId() );
        byte[] encodedArray = encryptionTool.encrypt( message.getBytes(), publicKey, true );
        String encoded = message;
        try
        {
            encoded = new String( encodedArray, "UTF-8" );
        }
        catch ( Exception e )
        {
            LOG.error( "Error approving new connections request", e );
        }
        client.query( "Message", encoded ).delete();
    }


    private Set<String> getTunnelNetworks( final Set<Peer> peers )
    {
        Set<String> result = new HashSet<>();

        for ( Peer peer : peers )
        {
            Set<HostInterfaceModel> r = null;
            try
            {
                r = peer.getInterfaces().filterByIp( P2PUtil.P2P_INTERFACE_IP_PATTERN );
            }
            catch ( PeerException e )
            {
                e.printStackTrace();
            }

            Collection tunnels = CollectionUtils.collect( r, new Transformer()
            {
                @Override
                public Object transform( final Object o )
                {
                    HostInterface i = ( HostInterface ) o;
                    SubnetUtils u = new SubnetUtils( i.getIp(), P2PUtil.P2P_SUBNET_MASK );
                    return u.getInfo().getNetworkAddress();
                }
            } );

            result.addAll( tunnels );
        }

        return result;
    }


    @Override
    public void approveRequest( final String requestId )
    {
        RequestedHostImpl registrationRequest = requestDataService.find( requestId );

        if ( registrationRequest == null || !RegistrationStatus.REQUESTED.equals( registrationRequest.getStatus() ) )
        {
            return;
        }

        registrationRequest.setStatus( RegistrationStatus.APPROVED );

        requestDataService.update( registrationRequest );

        importHostPublicKey( registrationRequest.getId(), registrationRequest.getPublicKey() );

        importHostSslCert( registrationRequest.getId(), registrationRequest.getCert() );

        for ( final ContainerInfo containerInfo : registrationRequest.getHostInfos() )
        {
            importHostPublicKey( containerInfo.getId(), containerInfo.getPublicKey() );
        }
    }


    private void importHostSslCert( String hostId, String cert )
    {
        try
        {
            securityManager.getKeyStoreManager().importCertAsTrusted( SystemSettings.getSecurePortX2(), hostId, cert );
            securityManager.getHttpContextManager().reloadKeyStore();
        }
        catch ( Exception e )
        {
            LOG.error( "Error importing host SSL certificate", e );
        }
    }


    private void importHostPublicKey( String hostId, String publicKey )
    {
        try
        {
            KeyManager keyManager = securityManager.getKeyManager();
            keyManager.savePublicKeyRing( hostId, ( short ) 2, publicKey );
        }
        catch ( Exception ex )
        {
            LOG.error( "Error importing host public key", ex );
        }
    }


    @Override
    public void removeRequest( final String requestId )
    {
        requestDataService.remove( requestId );
    }


    @Override
    public void deployResourceHost( List<String> args ) throws NodeRegistrationException
    {
        Host managementHost;
        CommandResult result;

        try
        {
            managementHost = localPeer.getManagementHost();

            Set<Peer> peers = Sets.newHashSet( managementHost.getPeer() );

            Set<String> existingNetworks = getTunnelNetworks( peers );

            String freeP2PSubnet = P2PUtil.findFreeSubnet( existingNetworks );
            args.add( "-I" );
            freeP2PSubnet = freeP2PSubnet.substring( 0, freeP2PSubnet.length() - 1 ) + (
                    Integer.valueOf( freeP2PSubnet.substring( freeP2PSubnet.length() - 1 ) ) + 1 );
            args.add( freeP2PSubnet );

            int ipOctet = ( Integer.valueOf( freeP2PSubnet.substring( freeP2PSubnet.length() - 1 ) ) + 1 );
            String ipRh = freeP2PSubnet.substring( 0, freeP2PSubnet.length() - 1 ) + ipOctet;
            args.add( "-i" );
            args.add( ipRh );

            String p2pHash = P2PUtil.generateHash( freeP2PSubnet );
            args.add( "-n" );
            args.add( p2pHash );

            String deviceName = "aws_rh_p2p_if";
            args.add( "-d" );
            args.add( deviceName );
            String runUser = "root";
            result = managementHost.execute(
                    new RequestBuilder( "/apps/subutai-mng/current/awsdeploy/awsdeploy" ).withCmdArgs( args )
                                                                                         .withRunAs( runUser )
                                                                                         .withTimeout( 600 ) );

            if ( result.getExitCode() != 0 )
            {
                throw new NodeRegistrationException( result.getStdErr() );
            }
        }
        catch ( HostNotFoundException | CommandException e )
        {
            e.printStackTrace();
        }
    }


    @Override
    public ContainerToken generateContainerTTLToken( final Long ttl )
    {
        ContainerTokenImpl token =
                new ContainerTokenImpl( UUID.randomUUID().toString(), new Timestamp( System.currentTimeMillis() ),
                        ttl );
        try
        {
            containerTokenDataService.persist( token );
        }
        catch ( Exception ex )
        {
            LOG.error( "Error persisting container token", ex );
        }

        return token;
    }


    @Override
    public ContainerToken verifyToken( final String token, String containerHostId, String publicKey )
            throws NodeRegistrationException
    {

        ContainerTokenImpl containerToken = containerTokenDataService.find( token );
        if ( containerToken == null )
        {
            throw new NodeRegistrationException( "Couldn't verify container token" );
        }

        if ( containerToken.getDateCreated().getTime() + containerToken.getTtl() < System.currentTimeMillis() )
        {
            throw new NodeRegistrationException( "Container token expired" );
        }
        try
        {
            securityManager.getKeyManager().savePublicKeyRing( containerHostId, ( short ) 2, publicKey );
        }
        catch ( Exception ex )
        {
            throw new NodeRegistrationException( "Failed to store container pubkey", ex );
        }
        return containerToken;
    }


    private void checkManagement( RequestedHost requestedHost )
    {
        try
        {
            try
            {
                localPeer.getManagementHost();
            }
            catch ( HostNotFoundException nfe )
            {
                String requestId = findManagementNode( requestedHost );
                if ( requestId != null
                        && requestedHost.getStatus() == io.subutai.core.registration.api.RegistrationStatus.REQUESTED )
                {
                    approveRequest( requestId );

                    Set<HostInterfaceModel> hostInterfaces = Sets.newHashSet();
                    for ( HostInterface hostInterface : requestedHost.getNetHostInterfaces() )
                    {
                        hostInterfaces.add( new HostInterfaceModel( hostInterface ) );
                    }

                    ResourceHostInfoModel resourceHostInfoModel = new ResourceHostInfoModel(
                            new HostInfoModel( requestedHost.getId(), requestedHost.getHostname(),
                                    new HostInterfaces( requestedHost.getId(), hostInterfaces ),
                                    requestedHost.getArch() ) );

                    resourceHostInfoModel.setInstance( InstanceType.LOCAL );

                    Set<ContainerHostInfoModel> containers = Sets.newHashSet();
                    for ( ContainerHostInfo containerHostInfo : requestedHost.getHostInfos() )
                    {
                        containers.add( new ContainerHostInfoModel( containerHostInfo ) );
                    }

                    resourceHostInfoModel.setContainers( containers );

                    processHeartbeat( new HeartBeat( resourceHostInfoModel ) );
                }
            }
        }
        catch ( Exception e )
        {
            // ignore
        }
    }


    private String findManagementNode( RequestedHost h )
    {
        String result = null;
        if ( h.getStatus() == io.subutai.core.registration.api.RegistrationStatus.REQUESTED )
        {
            for ( HostInfo info : h.getHostInfos() )
            {
                if ( "management".equalsIgnoreCase( info.getHostname() ) )
                {
                    result = h.getId();
                    break;
                }
            }
        }
        return result;
    }
}
