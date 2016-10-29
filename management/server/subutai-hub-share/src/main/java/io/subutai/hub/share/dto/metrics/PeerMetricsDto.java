package io.subutai.hub.share.dto.metrics;


import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;


@JsonIgnoreProperties( ignoreUnknown = true )
public class PeerMetricsDto
{
    @JsonProperty( "peer_id" )
    private String peerId;

    @JsonProperty( "startTime" )
    private Long startTime;

    @JsonProperty( "endTime" )
    private Long endTime;

    @JsonProperty( "metrics" )
    private Set<HostMetricsDto> metrics = new HashSet<>();


    public String getPeerId()
    {
        return peerId;
    }


    public void setPeerId( final String peerId )
    {
        this.peerId = peerId;
    }


    public Long getStartTime()
    {
        return startTime;
    }


    public void setStartTime( final Long startTime )
    {
        this.startTime = startTime;
    }


    public Long getEndTime()
    {
        return endTime;
    }


    public void setEndTime( final Long endTime )
    {
        this.endTime = endTime;
    }


    public Set<HostMetricsDto> getMetrics()
    {
        return metrics;
    }


    public void setMetrics( final Set<HostMetricsDto> metrics )
    {
        this.metrics = metrics;
    }


    public void addHostMetrics( final HostMetricsDto metrics )
    {
        if ( metrics == null )
        {
            throw new IllegalArgumentException( "Host metrics could not be null." );
        }

        this.metrics.add( metrics );
    }


    @JsonIgnore
    private HostMetricsDto getHostMetricsById( final String hostId )
    {
        for ( HostMetricsDto hostMetrics : this.metrics )
        {
            if ( hostMetrics.getHostId().equals( hostId ) )
            {
                return hostMetrics;
            }
        }
        return null;
    }
}
