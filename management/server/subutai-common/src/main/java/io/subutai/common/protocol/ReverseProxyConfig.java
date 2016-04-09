package io.subutai.common.protocol;


import org.codehaus.jackson.annotate.JsonProperty;


/**
 * DTO object for reverse proxy config
 */
public class ReverseProxyConfig
{
    @JsonProperty( "containerId" )
    private String containerId;
    @JsonProperty( "domainName" )
    private String domainName;
    @JsonProperty( "sslCert" )
    private String sslCert;


    public ReverseProxyConfig( @JsonProperty( "containerId" ) final String containerId,
                               @JsonProperty( "domainName" ) final String domainName,
                               @JsonProperty( "sslCert" ) final String sslCert )
    {
        this.containerId = containerId;
        this.domainName = domainName;
        this.sslCert = sslCert;
    }


    public String getContainerId()
    {
        return containerId;
    }


    public String getDomainName()
    {
        return domainName;
    }


    public String getSslCert()
    {
        return sslCert;
    }
}