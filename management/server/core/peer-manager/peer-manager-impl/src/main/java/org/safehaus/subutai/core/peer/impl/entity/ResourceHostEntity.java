package org.safehaus.subutai.core.peer.impl.entity;


import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Transient;

import org.safehaus.subutai.common.command.CommandException;
import org.safehaus.subutai.common.command.CommandResult;
import org.safehaus.subutai.common.command.CommandUtil;
import org.safehaus.subutai.common.command.RequestBuilder;
import org.safehaus.subutai.common.host.HostInfo;
import org.safehaus.subutai.common.peer.ContainerHost;
import org.safehaus.subutai.common.protocol.Template;
import org.safehaus.subutai.common.util.CollectionUtil;
import org.safehaus.subutai.common.util.UUIDUtil;
import org.safehaus.subutai.core.hostregistry.api.HostRegistry;
import org.safehaus.subutai.core.peer.api.ContainerState;
import org.safehaus.subutai.core.peer.api.HostTask;
import org.safehaus.subutai.core.peer.api.ResourceHost;
import org.safehaus.subutai.core.peer.api.ResourceHostException;
import org.safehaus.subutai.core.peer.impl.container.CreateContainerTask;
import org.safehaus.subutai.core.peer.impl.container.DestroyContainerTask;
import org.safehaus.subutai.core.registry.api.TemplateRegistry;
import org.safehaus.subutai.core.strategy.api.ServerMetric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Sets;


/**
 * Resource host implementation.
 */
@Entity
@Table( name = "resource_host" )
@Access( AccessType.FIELD )
public class ResourceHostEntity extends AbstractSubutaiHost implements ResourceHost
{
    private static final String CONTAINER_DOES_NOT_EXIST = "Container \"%s\" does NOT exist";
    private static final String CONTAINER_DESTROYED = "Destruction of \"%s\" completed successfully";
    private static final int DESTROY_TIMEOUT = 180;

    @javax.persistence.Transient
    transient protected static final Logger LOG = LoggerFactory.getLogger( ResourceHostEntity.class );
    @javax.persistence.Transient
    transient private static final Pattern LXC_STATE_PATTERN = Pattern.compile( "State:(\\s*)(.*)" );
    @javax.persistence.Transient
    transient private static final Pattern LOAD_AVERAGE_PATTERN = Pattern.compile( "load average: (.*)" );
    @javax.persistence.Transient
    transient private static final long WAIT_BEFORE_CHECK_STATUS_TIMEOUT_MS = 10000;
    @javax.persistence.Transient
    transient private ExecutorService singleThreadExecutorService = Executors.newSingleThreadExecutor();

    @javax.persistence.Transient
    transient private ExecutorService cachedThredPoolService;

    @OneToMany( mappedBy = "parent", fetch = FetchType.EAGER,
            targetEntity = ContainerHostEntity.class )
    Set<ContainerHost> containersHosts = Sets.newHashSet();

    @Transient
    CommandUtil commandUtil = new CommandUtil();
    @Transient
    TemplateRegistry registry;
    @Transient
    HostRegistry hostRegistry;


    public ResourceHostEntity()
    {
    }


    public ResourceHostEntity( final String peerId, final HostInfo resourceHostInfo )
    {
        super( peerId, resourceHostInfo );
    }


    public ExecutorService getSingleThreadExecutorService()
    {
        if ( singleThreadExecutorService == null )
        {
            singleThreadExecutorService = Executors.newSingleThreadExecutor();
            LOG.debug( String.format( "New single thread executor created for %s", hostname ) );
        }

        return singleThreadExecutorService;
    }


    public <T> Future<T> queueSequentialTask( Callable<T> callable )
    {
        return getSingleThreadExecutorService().submit( callable );
    }


    public Future queueSequentialTask( Runnable runnable )
    {
        return getSingleThreadExecutorService().submit( runnable );
    }


    public <T> Future<T> queueParallelTask( Callable<T> callable )
    {
        return getCachedThreadExecutorService().submit( callable );
    }


    public Future queueParallelTask( Runnable runnable )
    {
        return getCachedThreadExecutorService().submit( runnable );
    }


    private ExecutorService getCachedThreadExecutorService()
    {
        if ( cachedThredPoolService == null )
        {
            cachedThredPoolService = Executors.newCachedThreadPool();
        }

        return cachedThredPoolService;
    }


    @Override
    public synchronized void queue( final HostTask hostTask )
    {
        LOG.debug( String.format( "New sequential task %s queued.", hostTask.getId() ) );
        ExecutorService executorService = getSingleThreadExecutorService();
        //        LOG.debug( executorService.toString() );
        executorService.submit( hostTask );
    }


    public synchronized void run( final HostTask hostTask )
    {
        LOG.info( String.format( "New immediate task %s added.", hostTask.getId() ) );
        getCachedThreadExecutorService().submit( hostTask );
    }


    public boolean startContainerHost( final ContainerHost container ) throws ResourceHostException
    {
        Preconditions.checkNotNull( container, "Container host is null" );

        RequestBuilder requestBuilder =
                new RequestBuilder( String.format( "/usr/bin/lxc-start -n %s -d", container.getHostname() ) )
                        .withTimeout( 60 ).daemon();
        try
        {
            execute( requestBuilder );
        }
        catch ( CommandException e )
        {
            throw new ResourceHostException( "Error on starting container.", e );
        }
        try
        {
            Thread.sleep( WAIT_BEFORE_CHECK_STATUS_TIMEOUT_MS );
        }
        catch ( InterruptedException ignore )
        {
        }

        return ContainerState.RUNNING.equals( getContainerHostState( container ) );
    }


    private ContainerState getContainerHostState( final ContainerHost container ) throws ResourceHostException
    {
        Preconditions.checkNotNull( container, "Container host is null" );

        RequestBuilder requestBuilder =
                new RequestBuilder( String.format( "/usr/bin/lxc-info -n %s", container.getHostname() ) )
                        .withTimeout( 30 );
        CommandResult result;
        try
        {
            result = execute( requestBuilder );
        }
        catch ( CommandException e )
        {
            throw new ResourceHostException( "Error on fetching container state.", e );
        }

        String stdOut = result.getStdOut();

        Matcher m = LXC_STATE_PATTERN.matcher( stdOut );
        if ( m.find() )
        {
            return ContainerState.valueOf( m.group( 2 ) );
        }
        else
        {
            return ContainerState.UNKNOWN;
        }
    }


    public ServerMetric getMetric() throws ResourceHostException
    {
        RequestBuilder requestBuilder =
                new RequestBuilder( "free -m | grep buffers/cache ; df /lxc-data | grep /lxc-data ; uptime ; nproc" )
                        .withTimeout( 30 );
        try
        {
            CommandResult result = execute( requestBuilder );
            ServerMetric serverMetric = null;
            if ( result.hasCompleted() )
            {
                String[] metrics = result.getStdOut().split( "\n" );
                serverMetric = gatherMetrics( metrics );
                //                serverMetric.setAverageMetrics( gatherAvgMetrics() );
            }
            return serverMetric;
        }
        catch ( CommandException e )
        {
            throw new ResourceHostException( "Unable retrieve host metric", e );
        }
    }


    public Set<ContainerHost> getContainerHosts()
    {
        synchronized ( containersHosts )
        {
            return Sets.newConcurrentHashSet( containersHosts );
        }
    }


    /**
     * Gather metrics from linux commands outputs.
     */
    private ServerMetric gatherMetrics( String[] metrics )
    {
        int freeRamMb = 0;
        int freeHddMb = 0;
        int numOfProc = 0;
        double loadAvg = 0;
        double cpuLoadPercent = 100;
        // parsing only 4 metrics
        if ( metrics.length != 4 )
        {
            return null;
        }
        boolean parseOk = true;
        for ( int line = 0; parseOk && line < metrics.length; line++ )
        {
            String metric = metrics[line];
            switch ( line )
            {
                case 0:
                    //-/+ buffers/cache:       1829       5810
                    String[] ramMetric = metric.split( "\\s+" );
                    String freeRamMbStr = ramMetric[ramMetric.length - 1];
                    try
                    {
                        freeRamMb = Integer.parseInt( freeRamMbStr );
                    }
                    catch ( Exception e )
                    {
                        parseOk = false;
                    }
                    break;
                case 1:
                    //lxc-data       143264768 608768 142656000   1% /lxc-data
                    String[] hddMetric = metric.split( "\\s+" );
                    if ( hddMetric.length == 6 )
                    {
                        String hddMetricKbStr = hddMetric[3];
                        try
                        {
                            freeHddMb = Integer.parseInt( hddMetricKbStr ) / 1024;
                        }
                        catch ( Exception e )
                        {
                            parseOk = false;
                        }
                    }
                    else
                    {
                        parseOk = false;
                    }
                    break;
                case 2:
                    // 09:17:38 up 4 days, 23:06,  0 users,  load average: 2.18, 3.06, 2.12
                    Matcher m = LOAD_AVERAGE_PATTERN.matcher( metric );
                    if ( m.find() )
                    {
                        String[] loads = m.group( 1 ).split( "," );
                        try
                        {
                            loadAvg = ( Double.parseDouble( loads[0] ) + Double.parseDouble( loads[1] ) + Double
                                    .parseDouble( loads[2] ) ) / 3;
                        }
                        catch ( Exception e )
                        {
                            parseOk = false;
                        }
                    }
                    else
                    {
                        parseOk = false;
                    }
                    break;
                case 3:
                    try
                    {
                        numOfProc = Integer.parseInt( metric );
                        if ( numOfProc > 0 )
                        {
                            cpuLoadPercent = ( loadAvg / numOfProc ) * 100;
                        }
                        else
                        {
                            break;
                        }
                    }
                    catch ( Exception e )
                    {
                        parseOk = false;
                    }
                    break;
            }
        }
        if ( parseOk )
        {
            return new ServerMetric( getHostname(), freeHddMb, freeRamMb, ( int ) cpuLoadPercent, numOfProc );
        }
        else
        {
            return null;
        }
    }


    public boolean stopContainerHost( final ContainerHost container ) throws ResourceHostException
    {
        Preconditions.checkNotNull( container, "Container host is null" );

        RequestBuilder requestBuilder =
                new RequestBuilder( String.format( "/usr/bin/lxc-stop -n %s", container.getHostname() ) )
                        .withTimeout( 60 ).daemon();
        try
        {
            execute( requestBuilder );
        }
        catch ( CommandException e )
        {
            throw new ResourceHostException( "Error on stopping container.", e );
        }

        try
        {
            Thread.sleep( WAIT_BEFORE_CHECK_STATUS_TIMEOUT_MS );
        }
        catch ( InterruptedException ignore )
        {
        }

        return ContainerState.STOPPED.equals( getContainerHostState( container ) );
    }


    @Override
    public void destroyContainerHost( final ContainerHost containerHost ) throws ResourceHostException
    {

        Preconditions.checkNotNull( containerHost, "Container host is null" );

        if ( getContainerHostByName( containerHost.getHostname() ) == null )
        {
            throw new ResourceHostException(
                    String.format( "Container with name %s does not exist", containerHost.getHostname() ) );
        }

        Future future = queueSequentialTask( new DestroyContainerTask( this, containerHost.getHostname() ) );

        try
        {
            future.get();
        }
        catch ( ExecutionException | InterruptedException e )
        {
            throw new ResourceHostException( "Error destroying container", e );
        }
    }


    @Override
    public ContainerHost getContainerHostByName( final String hostname )
    {

        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostname ), "Invalid hostname" );

        ContainerHost result = null;

        Iterator iterator = getContainerHosts().iterator();

        while ( result == null && iterator.hasNext() )
        {
            ContainerHost host = ( ContainerHost ) iterator.next();

            if ( host.getHostname().equals( hostname ) )
            {
                result = host;
            }
        }
        return result;
    }


    public void removeContainerHost( final ContainerHost containerHost )
    {
        Preconditions.checkNotNull( containerHost, "Container host is null" );

        if ( getContainerHosts().contains( containerHost ) )
        {
            synchronized ( containersHosts )
            {
                containersHosts.remove( containerHost );
            }
        }
    }


    public ContainerHost getContainerHostById( final String id )
    {
        Preconditions.checkArgument( UUIDUtil.isStringAUuid( id ), "Invalid container id" );

        ContainerHost result = null;
        Iterator iterator = getContainerHosts().iterator();

        while ( result == null && iterator.hasNext() )
        {
            ContainerHost host = ( ContainerHost ) iterator.next();

            if ( host.getHostId().equals( id ) )
            {
                result = host;
            }
        }
        return result;
    }


    @Override
    public ContainerHost createContainer( final String templateName, final String hostname, final int timeout )
            throws ResourceHostException
    {
        Preconditions.checkArgument( !Strings.isNullOrEmpty( templateName ), "Invalid template name" );
        Preconditions.checkArgument( !Strings.isNullOrEmpty( hostname ), "Invalid hostname" );
        Preconditions.checkArgument( timeout > 0, "Invalid timeout" );

        if ( registry.getTemplate( templateName ) == null )
        {
            throw new ResourceHostException( String.format( "Template %s is not registered", templateName ) );
        }

        if ( getContainerHostByName( hostname ) != null )
        {
            throw new ResourceHostException( String.format( "Container with name %s already exists", hostname ) );
        }

        Future<ContainerHost> containerHostFuture =
                queueSequentialTask( new CreateContainerTask( this, templateName, hostname, timeout ) );

        try
        {
            return containerHostFuture.get();
        }
        catch ( ExecutionException | InterruptedException e )
        {
            throw new ResourceHostException( "Error creating container", e );
        }

    }


    @Override
    public void prepareTemplates( List<Template> templates ) throws ResourceHostException
    {

        Preconditions.checkArgument( !CollectionUtil.isCollectionEmpty( templates ), "Invalid template set" );

        LOG.debug( String.format( "Preparing templates on %s...", hostname ) );
        for ( Template p : templates )
        {
            prepareTemplate( p );
        }
        LOG.debug( "Template successfully prepared." );
    }


    @Override
    public void prepareTemplate( final Template template ) throws ResourceHostException
    {
        Preconditions.checkNotNull( template, "Invalid template" );

        if ( templateExists( template ) )
        {
            return;
        }
        importTemplate( template );
        if ( templateExists( template ) )
        {
            return;
        }
        // trying add repository
        /* TODO
           download each template except master in ancestry lineage if not installed already
           install it using dpkg -i
           then proceed
          */
        updateRepository( template );
        importTemplate( template );
        if ( !templateExists( template ) )
        {
            LOG.debug( String.format( "Could not prepare template %s on %s.", template.getTemplateName(), hostname ) );
            throw new ResourceHostException(
                    String.format( "Could not prepare template %s on %s", template.getTemplateName(), hostname ) );
        }
    }


    @Override
    public boolean templateExists( final Template template ) throws ResourceHostException
    {
        Preconditions.checkNotNull( template, "Invalid template" );

        try
        {
            CommandResult commandresult = run( Command.LIST_TEMPLATES, template.getTemplateName() );
            if ( commandresult.hasSucceeded() )
            {
                LOG.debug( String.format( "Template %s exists on %s.", template.getTemplateName(), hostname ) );
                return true;
            }
            else
            {
                LOG.warn( String.format( "Template %s does not exists on %s.", template.getTemplateName(), hostname ) );
                return false;
            }
        }
        catch ( CommandException ce )
        {
            LOG.error( "Command exception.", ce );
            throw new ResourceHostException( "General command exception on checking container existence.", ce );
        }
    }


    @Override
    public void importTemplate( Template template ) throws ResourceHostException
    {
        Preconditions.checkNotNull( template, "Invalid template" );

        LOG.debug( String.format( "Trying to import template %s to %s.", template.getTemplateName(), hostname ) );
        try
        {
            CommandResult commandResult = run( Command.IMPORT, template.getTemplateName() );
            if ( !commandResult.hasSucceeded() )
            {
                LOG.warn( "Template import failed. ", commandResult );
            }
        }
        catch ( CommandException ce )
        {
            LOG.error( "Command exception.", ce );
            throw new ResourceHostException( "General command exception on checking container existence.", ce );
        }
    }


    @Override
    public void updateRepository( Template template ) throws ResourceHostException
    {
        Preconditions.checkNotNull( template, "Invalid template" );

        if ( template.isRemote() )
        {
            try
            {
                LOG.debug( String.format( "Adding remote repository %s to %s...", template.getPeerId(), hostname ) );
                CommandResult commandResult = run( Command.ADD_SOURCE, template.getPeerId().toString() );
                if ( !commandResult.hasSucceeded() )
                {
                    LOG.warn( String.format( "Could not add repository %s to %s.", template.getPeerId(), hostname ),
                            commandResult );
                }
                LOG.debug( String.format( "Updating repository index on %s...", hostname ) );
                commandResult = run( Command.APT_GET_UPDATE );
                if ( !commandResult.hasSucceeded() )
                {
                    LOG.warn( String.format( "Could not update repository %s on %s.", template.getPeerId(), hostname ),
                            commandResult );
                }
            }
            catch ( CommandException ce )
            {
                LOG.error( "Command exception.", ce );
                throw new ResourceHostException( "General command exception on updating repository.", ce );
            }
        }
    }


    /**
     * Promotes a given clone into a template with given name. This method gives possibility to promote a copy of the
     * clone instead of the clone itself.
     *
     * @param cloneName name of the clone to be converted
     * @param newName new name for template
     * @param copyit if set <tt>true</tt>, a copy of clone is made first and a copied clone is promoted to template
     *
     * @return <tt>true</tt> if promote successfully completed
     */
    public boolean promote( String cloneName, String newName, boolean copyit ) throws ResourceHostException
    {
        List<String> args = new ArrayList<>();
        if ( newName != null && newName.length() > 0 )
        {
            args.add( "-n " + newName );
        }
        if ( copyit )
        {
            args.add( "-c" );
        }
        args.add( cloneName );
        String[] arr = args.toArray( new String[args.size()] );
        try
        {
            CommandResult commandResult = run( Command.PROMOTE, arr );
            if ( !commandResult.hasSucceeded() )
            {
                throw new ResourceHostException( String.format( "Could not promote container %s.", cloneName ) );
            }
        }
        catch ( CommandException ce )
        {
            LOG.error( "Command exception.", ce );
            throw new ResourceHostException( "General command exception on promoting container.", ce );
        }
        return true;
    }


    /**
     * Exports the template in the given server into a deb package.
     *
     * @param templateName the template name to be exported
     *
     * @return path to generated deb package
     */
    public String exportTemplate( String templateName ) throws ResourceHostException
    {
        try
        {
            CommandResult commandResult = run( Command.EXPORT, templateName );
            if ( !commandResult.hasSucceeded() )
            {
                throw new ResourceHostException(
                        String.format( "Could not export template %s on %s.", templateName, hostname ) );
            }
        }
        catch ( CommandException ce )
        {
            LOG.warn( "Error exporting template", ce );
        }
        return getExportedPackageFilePath( templateName );
    }


    /**
     * Gets a full Debian package name for a given template. Name does not have <tt>.deb</tt> extension.
     *
     * @param templateName the template name
     */
    public String getDebianPackageName( String templateName ) throws ResourceHostException
    {
        try
        {
            CommandResult commandResult = execute( Command.GET_DEB_PACKAGE_NAME.build( templateName ) );
            if ( commandResult.hasSucceeded() )
            {
                return commandResult.getStdOut();
            }
            else
            {
                return null;
            }
        }
        catch ( CommandException e )
        {
            throw new ResourceHostException( "Could not get deb package name.", e );
        }
    }


    public void setRegistry( final TemplateRegistry registry )
    {
        this.registry = registry;
    }


    public void setHostRegistry( final HostRegistry hostRegistry )
    {
        this.hostRegistry = hostRegistry;
    }


    private String getExportedPackageFilePath( String templateName ) throws ResourceHostException
    {
        String result = null;
        try
        {
            CommandResult dirCommandResult = execute( Command.SUBUTAI_TMPDIR.build() );
            if ( dirCommandResult.hasSucceeded() )
            {
                String dir = dirCommandResult.getStdOut();
                CommandResult packageNameCommandResult = execute( Command.GET_PACKAGE_NAME.build( templateName ) );
                if ( packageNameCommandResult.hasSucceeded() )
                {
                    result = Paths.get( dir, packageNameCommandResult.getStdOut() ).toString();
                }
            }
        }
        catch ( CommandException e )
        {
            throw new ResourceHostException( "Could not get exported package file path.", e );
        }

        if ( result == null )
        {
            throw new ResourceHostException(
                    String.format( "Could not get exported package file path of template %s", templateName ) );
        }
        return result;
    }


    /**
     * Gets package name for a given template. Package name is a name used in Apt commands. It is NOT a full Debian
     * package name of a template.
     */
    public String getPackageName( String templateName ) throws ResourceHostException
    {
        try
        {
            CommandResult commandResult = execute( Command.GET_PACKAGE_NAME.build( templateName ) );
            if ( commandResult.hasSucceeded() )
            {
                return commandResult.getStdOut();
            }
            else
            {
                return null;
            }
        }
        catch ( CommandException e )
        {
            throw new ResourceHostException( "Could not get package name.", e );
        }
    }


    protected CommandResult run( Command command, String... args ) throws CommandException
    {
        return execute( command.build( args ) );
    }


    public void addContainerHost( ContainerHost host )
    {
        Preconditions.checkNotNull( host, "Invalid container host" );

        ( ( ContainerHostEntity ) host ).setParent( this );

        synchronized ( containersHosts )
        {
            containersHosts.add( host );
        }
    }


    enum Command
    {
        LIST_TEMPLATES( "subutai list -t %s" ),
        CLONE( "subutai clone %s %s", 1, true ),
        DESTROY( "subutai destroy %s", DESTROY_TIMEOUT, true ),
        IMPORT( "subutai import %s" ),
        PROMOTE( "promote %s" ),
        EXPORT( "subutai export %s" ),
        SUBUTAI_TMPDIR( "echo $SUBUTAI_TMPDIR" ),
        GET_PACKAGE_NAME( ". /usr/share/subutai-cli/subutai/lib/deb_ops && get_package_name  %s" ),
        GET_DEB_PACKAGE_NAME(
                ". /etc/subutai/config && . /usr/share/subutai-cli/subutai/lib/deb_ops && get_debian_package_name  "
                        + "%s" ),
        ADD_SOURCE( "echo \"deb http://gw.intra.lan:9999/%1$s trusty main\" > /etc/apt/sources.list.d/%1$s.list " ),
        REMOVE_SOURCE( "rm /etc/apt/sources.list.d/%1$s.list " ),
        APT_GET_UPDATE( "apt-get update", 240 );

        String script;
        boolean daemon = false;
        int timeout = 120;


        Command( String script )
        {
            this.script = script;
        }


        Command( String script, int timeout )
        {
            this.script = script;
            this.timeout = timeout;
        }


        Command( String script, int timeout, boolean daemon )
        {
            this.script = script;
            this.timeout = timeout;
            this.daemon = daemon;
        }


        public RequestBuilder build( String... args )
        {
            String s = String.format( this.script, args );
            RequestBuilder rb = new RequestBuilder( s );
            rb.withTimeout( timeout );
            if ( daemon )
            {
                rb.daemon();
            }
            return rb;
        }
    }
}
