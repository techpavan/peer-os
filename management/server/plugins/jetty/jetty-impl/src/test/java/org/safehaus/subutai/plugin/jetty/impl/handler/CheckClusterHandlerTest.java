package org.safehaus.subutai.plugin.jetty.impl.handler;


import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.Before;
import org.junit.Test;
import org.safehaus.subutai.common.protocol.Agent;
import org.safehaus.subutai.core.command.api.CommandRunner;
import org.safehaus.subutai.core.command.api.command.Command;
import org.safehaus.subutai.core.command.api.command.RequestBuilder;
import org.safehaus.subutai.core.tracker.api.Tracker;
import org.safehaus.subutai.plugin.common.PluginDAO;
import org.safehaus.subutai.plugin.common.mock.CommandMock;
import org.safehaus.subutai.plugin.common.mock.CommandRunnerMock;
import org.safehaus.subutai.plugin.common.mock.ProductOperationMock;
import org.safehaus.subutai.plugin.common.mock.TrackerMock;
import org.safehaus.subutai.plugin.jetty.api.JettyConfig;
import org.safehaus.subutai.plugin.jetty.impl.Commands;
import org.safehaus.subutai.plugin.jetty.impl.JettyImpl;

import com.google.common.collect.Sets;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anySet;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


public class CheckClusterHandlerTest
{
    private String clusterName = "testClusterName";
    JettyImpl manager = new JettyImpl();

    CheckClusterHandler handler;


    @Before
    public void setUp()
    {
        manager.setTracker( mock(Tracker.class) );
        manager.setCommandRunner( mock( CommandRunner.class ) );
        manager.setPluginDAO( mock( PluginDAO.class ) );
        manager.setCommands( new Commands( manager.getCommandRunner() ));

        doReturn( new ProductOperationMock() )
                .when( manager.getTracker() )
                .createProductOperation( anyString(), any(String.class) );

        handler = new CheckClusterHandler( manager, clusterName );

        assertThat( "handler not null", handler != null );
    }


    @Test
    public void testRun()
    {
        JettyConfig jettyConfig = new JettyConfig();
        jettyConfig.setClusterName( clusterName );

        CommandMock checkCommand = new CommandMock();
        checkCommand.setSucceeded( true );
        when (manager.getCommandRunner().createCommand(any( RequestBuilder.class), anySet())).thenReturn( checkCommand );

        when( manager.getPluginDAO().getInfo( JettyConfig.PRODUCT_KEY.toLowerCase(), jettyConfig.getClusterName(),
                JettyConfig.class ) ).thenReturn( jettyConfig );

        ExecutorService executor = Executors.newCachedThreadPool();
        executor.execute( handler );

        verify( manager.getCommandRunner() ).runCommand( isA( CommandMock.class ) );
    }
}
