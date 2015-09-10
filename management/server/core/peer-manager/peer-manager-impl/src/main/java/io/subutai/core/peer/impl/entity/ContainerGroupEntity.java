package io.subutai.core.peer.impl.entity;


import java.util.Set;
import java.util.UUID;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.Table;

import io.subutai.common.util.CollectionUtil;
import io.subutai.core.peer.api.ContainerGroup;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;


/**
 * ContainerGroup class.
 */
@Entity
@Table( name = "container_group" )
@Access( AccessType.FIELD )
public class ContainerGroupEntity implements ContainerGroup
{
    @Id
    @Column( name = "env_id", nullable = false )
    private String environmentId;
    @Column( name = "initiator_peer_id", nullable = false )
    private String initiatorPeerId;
    @Column( name = "owner_id", nullable = false )
    private String ownerId;

    @ElementCollection( targetClass = String.class, fetch = FetchType.EAGER )
    private Set<String> containerIds = Sets.newHashSet();


    public ContainerGroupEntity( final UUID environmentId, final UUID initiatorPeerId, final UUID ownerId )
    {
        Preconditions.checkNotNull( environmentId );
        Preconditions.checkNotNull( initiatorPeerId );
        Preconditions.checkNotNull( ownerId );

        this.environmentId = environmentId.toString();
        this.initiatorPeerId = initiatorPeerId.toString();
        this.ownerId = ownerId.toString();
    }


    protected ContainerGroupEntity() {}


    @Override
    public UUID getEnvironmentId()
    {
        return UUID.fromString( environmentId );
    }


    public void setEnvironmentId( final String environmentId )
    {
        this.environmentId = environmentId;
    }


    @Override
    public UUID getInitiatorPeerId()
    {
        return UUID.fromString( initiatorPeerId );
    }


    public void setInitiatorPeerId( final String initiatorPeerId )
    {
        this.initiatorPeerId = initiatorPeerId;
    }


    @Override
    public UUID getOwnerId()
    {
        return UUID.fromString( ownerId );
    }


    public void setOwnerId( final String ownerId )
    {
        this.ownerId = ownerId;
    }


    @Override
    public Set<UUID> getContainerIds()
    {
        Set<UUID> ids = Sets.newHashSet();

        for ( String id : containerIds )
        {
            ids.add( UUID.fromString( id ) );
        }

        return ids;
    }


    public void setContainerIds( final Set<UUID> containerIds )

    {
        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( containerIds ), "Invalid container ids set" );

        Set<String> ids = Sets.newHashSet();

        for ( UUID id : containerIds )
        {
            ids.add( id.toString() );
        }

        this.containerIds = ids;
    }
}