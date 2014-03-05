package org.safehaus.kiskis.mgmt.server.ui.modules.lucene.common.command;

import org.safehaus.kiskis.mgmt.server.ui.modules.lucene.common.chain.Context;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;
import org.safehaus.kiskis.mgmt.shared.protocol.CommandFactory;
import org.safehaus.kiskis.mgmt.shared.protocol.Request;
import org.safehaus.kiskis.mgmt.shared.protocol.enums.OutputRedirection;
import org.safehaus.kiskis.mgmt.shared.protocol.enums.RequestType;

public class CommandBuilder {

    private static String source;
    private static int timeout = 30;

    public static void setSource(String _source) {
        source = _source;
    }

    public static void setTimeout(int _timeout) {
        timeout = _timeout;
    }

    public static Request getCommand(Context context, String commandLine) {

        Agent agent = context.get("agent");

        return CommandFactory.newRequest(
                RequestType.EXECUTE_REQUEST, // type
                agent.getUuid(), // agent uuid
                source, // source
                null, // task uuid
                1, // request sequence number
                "/", // cwd
                commandLine, // program
                OutputRedirection.RETURN, // std output redirection
                OutputRedirection.RETURN, // std error redirection
                null, // stdout capture file path
                null, // stderr capture file path
                "root", // runas
                null, // arg
                null, // env vars
                timeout // timeout
        );
    }
}
