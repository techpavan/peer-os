package io.subutai.core.messenger.impl;


import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;

import io.subutai.common.util.JsonUtil;
import io.subutai.core.messenger.api.Message;


/**
 * Implementation of Message
 */
public class MessageImpl implements Message
{
    private static final Logger LOG = LoggerFactory.getLogger( MessageImpl.class );
    public static final int MAX_SENDER_LEN = 50;
    private final UUID id;
    private final String sourcePeerId;
    private String sender;
    private String payloadString;


    public MessageImpl( Message message )
    {
        sourcePeerId = message.getSourcePeerId();
        payloadString = message.getPayload();
        id = message.getId();
        sender = message.getSender();
    }


    public MessageImpl( String sourcePeerId, Object payload )
    {
        Preconditions.checkNotNull( sourcePeerId, "Source peer id is null" );
        Preconditions.checkNotNull( payload, "Payload is null" );

        this.sourcePeerId = sourcePeerId;
        payloadString = JsonUtil.toJson( payload );
        id = UUID.randomUUID();
    }


    @Override
    public String getSourcePeerId()
    {
        return sourcePeerId;
    }


    @Override
    public UUID getId()
    {
        return id;
    }


    @Override
    public <T> T getPayload( Class<T> clazz )
    {
        T result = JsonUtil.fromJson( payloadString, clazz );
        LOG.debug( String.format( "Unmarshalled object: %s", result ) );
        return result;
    }


    @Override
    public String getSender()
    {
        return sender;
    }


    @Override
    public void setSender( final String sender )
    {
        Preconditions.checkArgument( StringUtils.length( sender ) <= MAX_SENDER_LEN,
                String.format( "Max sender length must be %d", MAX_SENDER_LEN ) );

        this.sender = sender;
    }


    @Override
    public String getPayload()
    {
        return payloadString;
    }


    @Override
    public String toString()
    {
        return MoreObjects.toStringHelper( this ).add( "id", id ).add( "sourcePeerId", sourcePeerId )
                          .add( "sender", sender ).add( "payloadString", payloadString ).toString();
    }
}
