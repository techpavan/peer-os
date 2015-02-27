package org.safehaus.subutai.common.util;


import java.security.AccessControlContext;
import java.security.AccessController;
import java.util.Set;

import javax.security.auth.Subject;

import org.safehaus.subutai.common.security.NullSubutaiLoginContext;
import org.safehaus.subutai.common.security.ShiroPrincipal;
import org.safehaus.subutai.common.security.SubutaiLoginContext;


/**
 * Security utility
 */
public abstract class SecurityUtil
{
    /**
     * Retrieves SubutaiLoginContext object from karaf session.
     */
    public static SubutaiLoginContext getSubutaiLoginContext()
    {
        SubutaiLoginContext nullLoginContext = NullSubutaiLoginContext.getInstance();
        AccessControlContext acc = AccessController.getContext();
        Subject subject = Subject.getSubject( acc );
        if ( subject == null )
        {
            return nullLoginContext;
        }
        Set<ShiroPrincipal> shiroPrincipal = subject.getPrincipals( ShiroPrincipal.class );

        return shiroPrincipal.isEmpty() ? nullLoginContext : shiroPrincipal.iterator().next().getSubutaiLoginContext();
    }
}
