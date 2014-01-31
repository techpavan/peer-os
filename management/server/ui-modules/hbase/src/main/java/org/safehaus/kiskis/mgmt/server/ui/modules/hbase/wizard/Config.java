/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.safehaus.kiskis.mgmt.server.ui.modules.hbase.wizard;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.safehaus.kiskis.mgmt.shared.protocol.Agent;

/**
 *
 * @author dilshat
 */
public class Config implements Serializable {

    private UUID uuid;
    Set<Agent> agents;
    Set<Agent> master;
    Set<Agent> region;
    Set<Agent> quorum;
    Set<Agent> backupMasters;
    String domainInfo;

    public Config() {
        this.uuid = java.util.UUID.fromString(new com.eaio.uuid.UUID().toString());
    }

    public UUID getUuid() {
        return uuid;
    }

    public void reset() {
        this.agents = null;
        this.master = null;
        this.region = null;
        this.quorum = null;
        this.backupMasters = null;
        this.domainInfo = "";
    }

    public Set<Agent> getAgents() {
        return agents;
    }

    public Set<UUID> getAgentsSet() {
        Set<UUID> set = new HashSet<UUID>();
        for (Agent agent : agents) {
            set.add(agent.getUuid());
        }
        return set;
    }

    public void setAgents(Set<Agent> agents) {
        this.agents = agents;
    }

    public Set<Agent> getMaster() {
        return master;
    }

    public Set<UUID> getMasterUUIDset() {
        Set<UUID> set = new HashSet<UUID>();
        for (Agent agent : master) {
            set.add(agent.getUuid());
        }
        return set;
    }

    public void setMaster(Set<Agent> master) {
        this.master = master;
    }

    public Set<Agent> getRegion() {
        return region;
    }

    public Set<UUID> getRegionSet() {
        Set<UUID> set = new HashSet<UUID>();
        for (Agent agent : region) {
            set.add(agent.getUuid());
        }
        return set;
    }

    public void setRegion(Set<Agent> region) {
        this.region = region;
    }

    public Set<Agent> getQuorum() {
        return quorum;
    }

    public Set<UUID> getQuorumSet() {
        Set<UUID> set = new HashSet<UUID>();
        for (Agent agent : quorum) {
            set.add(agent.getUuid());
        }
        return set;
    }

    public void setQuorum(Set<Agent> quorum) {
        this.quorum = quorum;
    }

    public Set<Agent> getBackupMasters() {
        return backupMasters;
    }

    public Set<UUID> getBackupMastersSet() {
        Set<UUID> set = new HashSet<UUID>();
        for (Agent agent : backupMasters) {
            set.add(agent.getUuid());
        }
        return set;
    }

    public void setBackupMasters(Set<Agent> backupMasters) {
        this.backupMasters = backupMasters;
    }

    public String getDomainInfo() {
        return domainInfo;
    }

    public void setDomainInfo(String domainInfo) {
        this.domainInfo = domainInfo;
    }

}
