package org.safehaus.subutai.plugin.cassandra.impl;


import org.junit.Before;
import org.junit.Test;
import org.safehaus.subutai.common.protocol.AbstractOperationHandler;
import org.safehaus.subutai.common.tracker.OperationState;
import org.safehaus.subutai.core.command.api.CommandRunner;
import org.safehaus.subutai.core.container.api.container.ContainerManager;
import org.safehaus.subutai.plugin.cassandra.api.CassandraClusterConfig;
import org.safehaus.subutai.plugin.cassandra.impl.handler.CheckClusterHandler;
import org.safehaus.subutai.plugin.common.PluginDao;
import org.safehaus.subutai.plugin.common.mock.TrackerMock;

import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


public class CheckClusterHandlerTest
{

    CassandraImpl cassandraMock;


    @Before
    public void setup()
    {
        cassandraMock = mock( CassandraImpl.class );
        when( cassandraMock.getCommandRunner() ).thenReturn( mock( CommandRunner.class ) );
        when( cassandraMock.getTracker() ).thenReturn( new TrackerMock() );
        when( cassandraMock.getContainerManager() ).thenReturn( mock( ContainerManager.class ) );
        when( cassandraMock.getCluster( anyString() ) ).thenReturn( null );
        when( cassandraMock.getPluginDAO() ).thenReturn( mock( PluginDao.class ) );
        when( cassandraMock.getPluginDAO().getInfo( CassandraClusterConfig.PRODUCT_KEY.toLowerCase(), "Cassandra",
                CassandraClusterConfig.class ) ).thenReturn( null );
    }


    @Test
    public void testWithoutCluster()
    {

        AbstractOperationHandler operationHandler = new CheckClusterHandler( cassandraMock, "test-cluster" );
        operationHandler.run();

        assertTrue( operationHandler.getTrackerOperation().getLog().contains( "not exist" ) );
        assertEquals( operationHandler.getTrackerOperation().getState(), OperationState.FAILED );
    }
}
