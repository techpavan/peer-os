/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.kiskis.mgmt.impl.oozie;

import org.safehaus.kiskis.mgmt.api.commandrunner.Command;
import org.safehaus.kiskis.mgmt.api.commandrunner.CommandsSingleton;
import org.safehaus.kiskis.mgmt.api.commandrunner.RequestBuilder;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;
import org.safehaus.kiskis.mgmt.shared.protocol.enums.OutputRedirection;
import java.util.Set;

/**
 * @author dilshat
 */
public class Commands extends CommandsSingleton {

    public static Command getInstallServerCommand(Set<Agent> agents) {

        return createCommand(
                new RequestBuilder(
                        "sleep 10; apt-get --force-yes --assume-yes install ksks-oozie-server")
                        .withTimeout(90).withStdOutRedirection(OutputRedirection.NO),
                agents
        );

    }

    public static Command getInstallClientCommand(Set<Agent> agents) {

        return createCommand(
                new RequestBuilder(
                        "sleep 10; apt-get --force-yes --assume-yes install ksks-oozie-clients")
                        .withTimeout(90).withStdOutRedirection(OutputRedirection.NO),
                agents
        );

    }

    public static Command getStartServerCommand(Set<Agent> agents) {
        return createCommand(
                new RequestBuilder(
                        "service oozie-server start")
                ,
                agents
        );
    }

    public static Command getStopServerCommand(Set<Agent> agents) {
        return createCommand(
                new RequestBuilder(
                        "service oozie-server stop")
                ,
                agents
        );
    }

    public static Command getStatusServerCommand(Set<Agent> agents) {
        return createCommand(
                new RequestBuilder(
                        "service oozie-server status")
                ,
                agents
        );
    }

    public static Command getConfigureRootHostsCommand(Set<Agent> agents, String param) {

        return createCommand(
                new RequestBuilder(
                        String.format(". /etc/profile && $HADOOP_HOME/bin/hadoop-property.sh add core-site.xml hadoop.proxyuser.root.hosts %s", param))
                ,
                agents
        );
    }

    public static Command getConfigureRootGroupsCommand(Set<Agent> agents) {

        return createCommand(
                new RequestBuilder(
                        String.format(". /etc/profile && $HADOOP_HOME/bin/hadoop-property.sh add core-site.xml hadoop.proxyuser.root.groups '\\*' "))
                ,
                agents
        );
    }

    public static Command getUninstallServerCommand(Set<Agent> agents) {
        return createCommand(
                new RequestBuilder(
                        "apt-get --force-yes --assume-yes purge ksks-oozie-server")
                        .withTimeout(90).withStdOutRedirection(OutputRedirection.NO),
                agents
        );
    }

    public static Command getUninstallClientsCommand(Set<Agent> agents) {
        return createCommand(
                new RequestBuilder(
                        "sleep 10; apt-get --force-yes --assume-yes purge ksks-oozie-client")
                        .withTimeout(90).withStdOutRedirection(OutputRedirection.NO),
                agents
        );
    }
}
