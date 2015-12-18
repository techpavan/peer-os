package io.subutai.core.identity.impl.dao;


import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

import io.subutai.common.dao.DaoManager;
import io.subutai.core.identity.api.dao.IdentityDataService;
import io.subutai.core.identity.api.model.Permission;
import io.subutai.core.identity.api.model.Relation;
import io.subutai.core.identity.api.model.RelationLink;
import io.subutai.core.identity.api.model.Role;
import io.subutai.core.identity.api.model.Session;
import io.subutai.core.identity.api.model.User;
import io.subutai.core.identity.api.model.UserToken;
import io.subutai.core.identity.impl.model.RelationLinkImpl;


/**
 *
 */
public class IdentityDataServiceImpl implements IdentityDataService
{
    private static final Logger LOG = LoggerFactory.getLogger( IdentityDataServiceImpl.class );

    private DaoManager daoManager = null;
    private UserDAO userDAOService = null;
    private RoleDAO roleDAOService = null;
    private SessionDAO sessionDAOService = null;
    private PermissionDAO permissionDAOService = null;
    private UserTokenDAO userTokenDAOService = null;
    private RelationDAO relationDAO = null;


    /* *************************************************
     *
     */
    public IdentityDataServiceImpl( final DaoManager daoManager )
    {
        this.daoManager = daoManager;

        if ( daoManager != null )
        {
            userDAOService = new UserDAO( daoManager );
            roleDAOService = new RoleDAO( daoManager );
            sessionDAOService = new SessionDAO( daoManager );
            permissionDAOService = new PermissionDAO( daoManager );
            userTokenDAOService = new UserTokenDAO( daoManager );
            relationDAO = new RelationDAO( daoManager );
        }
        else
        {
            LOG.error( "*** IdentityDataServiceImpl DaoManager is NULL.  ***" );
        }
    }


    /*
     *  ******User *************************************
     */
    @Override
    public User getUserByUsername( final String userName )
    {
        return userDAOService.findByUsername( userName );
    }


    /* *************************************************
     *
     */
    @Override
    public User getUser( long userId )
    {
        return userDAOService.find( userId );
    }


    /* *************************************************
     *
     */
    @Override
    public void assignUserRole( long userId, Role role )
    {
        User user = userDAOService.find( userId );
        user.getRoles().add( role );
        userDAOService.update( user );
    }


    /* *************************************************
     *
     */
    @Override
    public void removeUserRole( long userId, Role role )
    {
        User user = userDAOService.find( userId );

        user.getRoles().remove( role );
        userDAOService.update( user );
    }


    /* *************************************************
     */
    @Override
    public List<User> getAllUsers()
    {
        return userDAOService.getAll();
    }


    /* *************************************************
     */
    @Override
    public void persistUser( final User item )
    {
        userDAOService.persist( item );
    }


    /* *************************************************
     */
    @Override
    public void removeUser( final long id )
    {
        userDAOService.remove( id );
    }


    /* *************************************************
     */
    @Override
    public void updateUser( final User item )
    {
        userDAOService.update( item );
    }


    /* ***********Roles ********************************
     */
    @Override
    public Role getRole( final long roleId )
    {
        return roleDAOService.find( roleId );
    }


    /* *************************************************
     */
    @Override
    public List<Role> getAllRoles()
    {
        return roleDAOService.getAll();
    }


    /* *************************************************
     */
    @Override
    public void persistRole( final Role item )
    {
        roleDAOService.persist( item );
    }


    /* *************************************************
     */
    @Override
    public void removeRole( final long id )
    {
        roleDAOService.remove( id );
    }


    /* *************************************************
     */
    @Override
    public void updateRole( final Role item )
    {
        roleDAOService.update( item );
    }


    /* *************************************************
     *
     */
    @Override
    public void assignRolePermission( long roleId, Permission permission )
    {
        Role role = roleDAOService.find( roleId );
        role.getPermissions().add( permission );
        roleDAOService.update( role );
    }


    /* *************************************************
     *
     */
    @Override
    public void removeAllRolePermissions( long roleId )
    {
        Role role = roleDAOService.find( roleId );
        role.getPermissions().clear();
        roleDAOService.update( role );
    }


    /*
     * ******Permission*********************************
     */
    @Override
    public Permission getPermission( final long permissionId )
    {
        return permissionDAOService.find( permissionId );
    }


    /* *************************************************
     */
    @Override
    public List<Permission> getAllPermissions()
    {
        return permissionDAOService.getAll();
    }


    /* *************************************************
     */
    @Override
    public void persistPermission( final Permission item )
    {
        permissionDAOService.persist( item );
    }


    /* *************************************************
     */
    @Override
    public void removePermission( final long id )
    {
        permissionDAOService.remove( id );
    }


    /* *************************************************
     */
    @Override
    public void updatePermission( final Permission item )
    {
        permissionDAOService.update( item );
    }


    /* *************************************************
     */
    @Override
    public void removeRolePermission( final long roleId, Permission permission )
    {
        Role role = roleDAOService.find( roleId );
        role.getPermissions().remove( permission );
        roleDAOService.update( role );
    }


    /* ******Session************************
     *
     */
    @Override
    public List<Session> getAllSessions()
    {
        return sessionDAOService.getAll();
    }


    /* *************************************************
     *
     */
    @Override
    public Session getSession( final long sessionId )
    {
        return sessionDAOService.find( sessionId );
    }


    /* *************************************************
     *
     */
    @Override
    public List<Session> getSessionsByUserId( final long userId )
    {
        return sessionDAOService.getByUserId( userId );
    }


    /* *************************************************
     *
     */
    @Override
    public Session getValidSession( final long userId )
    {
        return sessionDAOService.getValid( userId );
    }


    /* *************************************************
     *
     */
    @Override
    public void persistSession( final Session item )
    {
        sessionDAOService.persist( item );
    }


    /* *************************************************
     *
     */
    @Override
    public void removeSession( final long id )
    {
        sessionDAOService.remove( id );
    }


    /* *************************************************
     *
     */
    @Override
    public void updateSession( final Session item )
    {
        sessionDAOService.update( item );
    }


    /* *************************************************
     *
     */
    @Override
    public void invalidateSessions()
    {
        sessionDAOService.invalidate();
    }


    /* ******UserToken *********************************
    *
    */
    @Override
    public List<UserToken> getAllUserTokens()
    {
        return userTokenDAOService.getAll();
    }


    /* *************************************************
     *
     */
    @Override
    public UserToken getUserToken( String token )
    {
        return userTokenDAOService.find( token );
    }


    /* *************************************************
     *
     */
    @Override
    public UserToken getValidUserToken( String token )
    {
        return userTokenDAOService.findValid( token );
    }


    /* *************************************************
     *
     */
    @Override
    public UserToken getUserToken( long userId )
    {
        return userTokenDAOService.findByUserId( userId );
    }


    /* *************************************************
     *
     */
    @Override
    public UserToken getValidUserToken( long userId )
    {
        return userTokenDAOService.findValidByUserId( userId );
    }


    /* *************************************************
     *
     */
    @Override
    public void persistUserToken( final UserToken item )
    {
        userTokenDAOService.persist( item );
    }


    /* *************************************************
     *
     */
    @Override
    public void updateUserToken( final UserToken item )
    {
        userTokenDAOService.update( item );
    }


    /* *************************************************
     *
     */
    @Override
    public void removeUserToken( String token )
    {
        userTokenDAOService.remove( token );
    }


    /* *************************************************
     *
     */
    @Override
    public void removeInvalidTokens()
    {
        userTokenDAOService.removeInvalid();
    }


    @Override
    public RelationLink getRelationLink( final String uniqueIdentifier, final String classPath )
    {
        return relationDAO.findRelationLink( uniqueIdentifier, classPath );
    }


    @Override
    public Relation getRelationBySourceObject( final RelationLink source, final RelationLink object )
    {
        if ( ( source instanceof RelationLinkImpl ) && ( object instanceof RelationLinkImpl ) )
        {
            return relationDAO.findBySourceAndObject( ( RelationLinkImpl ) source, ( RelationLinkImpl ) object );
        }
        return null;
    }


    @Override
    public Relation getRelationBySourceTargetObject( final RelationLink source, final RelationLink target,
                                                     final RelationLink object )
    {
        if ( ( source instanceof RelationLinkImpl ) && ( object instanceof RelationLinkImpl )
                && ( target instanceof RelationLinkImpl ) )
        {
            return relationDAO.findBySourceTargetObject( ( RelationLinkImpl ) source, ( RelationLinkImpl ) target,
                    ( RelationLinkImpl ) object );
        }
        return null;
    }


    @Override
    public void persistRelation( final Relation relation )
    {
        relationDAO.update( relation.getSource() );
        relationDAO.update( relation.getTarget() );
        relationDAO.update( relation.getTrustedObject() );
        relationDAO.update( relation );
    }


    @Override
    public List<Relation> relationsByTarget( final RelationLink target )
    {
        if ( target instanceof RelationLinkImpl )
        {
            return relationDAO.findByTarget( ( RelationLinkImpl ) target );
        }
        else
        {
            return Lists.newArrayList();
        }
    }


    @Override
    public List<Relation> relationsByObject( final RelationLink object )
    {
        if ( object instanceof RelationLinkImpl )
        {
            return relationDAO.findByObject( ( RelationLinkImpl ) object );
        }
        else
        {
            return Lists.newArrayList();
        }
    }


    @Override
    public List<Relation> relationsBySource( final RelationLink source )
    {
        if ( source instanceof RelationLinkImpl )
        {
            return relationDAO.findBySource( ( RelationLinkImpl ) source );
        }
        else
        {
            return Lists.newArrayList();
        }
    }


    @Override
    public void removeRelation( final Long relationId )
    {
        relationDAO.remove( relationId );
    }
}
