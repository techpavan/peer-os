package io.subutai.core.identity.api.model;


import java.util.Date;
import java.util.List;

import io.subutai.common.security.relation.RelationLink;


public interface User extends RelationLink
{


    int getTrustLevel();

    void setTrustLevel( int trustLevel );

    Long getId();

    void setId( Long id );

    String getUserName();

    void setUserName( String userName );

    String getFullName();

    void setFullName( String fullName );

    String getPassword();

    void setPassword( String password );

    String getSalt();

    void setSalt( String salt );

    String getEmail();

    void setEmail( String email );

    List<Role> getRoles();

    void setRoles( List<Role> roles );

    int getType();

    void setType( int type );

    int getStatus();

    void setStatus( int status );

    String getSecurityKeyId();

    void setSecurityKeyId( String securityKeyId );

    void setFingerprint( String fingerprint );

    String getFingerprint();

    String getStatusName();

    String getTypeName();


    Date getValidDate();

    void setValidDate( Date validDate );

    String getAuthId();

    void setAuthId( String authId );

    boolean isIdentityValid();

    boolean isBazaarUser();
}
