/**
 *
 * SIROCCO
 * Copyright (C) 2013 France Telecom
 * Contact: sirocco@ow2.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 */

package org.ow2.sirocco.cloudmanager.connector.openstack;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.codec.binary.Base64;
import org.ow2.sirocco.cloudmanager.connector.api.ConnectorException;
import org.ow2.sirocco.cloudmanager.connector.api.ProviderTarget;
import org.ow2.sirocco.cloudmanager.connector.api.ResourceNotFoundException;
import org.ow2.sirocco.cloudmanager.model.cimi.Address;
import org.ow2.sirocco.cloudmanager.model.cimi.DiskTemplate;
import org.ow2.sirocco.cloudmanager.model.cimi.ForwardingGroup;
import org.ow2.sirocco.cloudmanager.model.cimi.ForwardingGroupCreate;
import org.ow2.sirocco.cloudmanager.model.cimi.ForwardingGroupNetwork;
import org.ow2.sirocco.cloudmanager.model.cimi.Machine;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineConfiguration;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineCreate;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineDisk;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineImage;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineImage.Type;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineNetworkInterface;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineNetworkInterfaceAddress;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineTemplateNetworkInterface;
import org.ow2.sirocco.cloudmanager.model.cimi.MachineVolume;
import org.ow2.sirocco.cloudmanager.model.cimi.Network;
import org.ow2.sirocco.cloudmanager.model.cimi.NetworkCreate;
import org.ow2.sirocco.cloudmanager.model.cimi.Subnet;
import org.ow2.sirocco.cloudmanager.model.cimi.SubnetConfig;
import org.ow2.sirocco.cloudmanager.model.cimi.Volume;
import org.ow2.sirocco.cloudmanager.model.cimi.VolumeConfiguration;
import org.ow2.sirocco.cloudmanager.model.cimi.VolumeCreate;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.CloudProviderAccount;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.CloudProviderLocation;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.PlacementHint;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.ProviderMapping;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.Quota;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.SecurityGroup;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.SecurityGroupCreate;
import org.ow2.sirocco.cloudmanager.model.cimi.extension.SecurityGroupRule;
import org.ow2.sirocco.cloudmanager.model.utils.ResourceType;
import org.ow2.sirocco.cloudmanager.model.utils.Unit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.woorea.openstack.base.client.OpenStackResponseException;
import com.woorea.openstack.base.client.OpenStackSimpleTokenProvider;
import com.woorea.openstack.keystone.Keystone;
import com.woorea.openstack.keystone.model.Access;
import com.woorea.openstack.keystone.model.authentication.UsernamePassword;
import com.woorea.openstack.keystone.utils.KeystoneUtils;
import com.woorea.openstack.nova.Nova;
import com.woorea.openstack.nova.api.extensions.FloatingIpPoolsExtension;
import com.woorea.openstack.nova.model.Flavor;
import com.woorea.openstack.nova.model.FloatingIp;
import com.woorea.openstack.nova.model.FloatingIpPools;
import com.woorea.openstack.nova.model.FloatingIps;
import com.woorea.openstack.nova.model.Image;
import com.woorea.openstack.nova.model.Images;
import com.woorea.openstack.nova.model.KeyPair;
import com.woorea.openstack.nova.model.Limits.AbsoluteLimit;
import com.woorea.openstack.nova.model.SchedulerHints;
import com.woorea.openstack.nova.model.Server;
import com.woorea.openstack.nova.model.Server.Addresses;
import com.woorea.openstack.nova.model.ServerForCreate;
import com.woorea.openstack.nova.model.ServerForCreateWithSchedulerHints;
import com.woorea.openstack.nova.model.VolumeAttachment;
import com.woorea.openstack.nova.model.VolumeAttachments;
import com.woorea.openstack.nova.model.VolumeForCreate;
import com.woorea.openstack.quantum.Quantum;
import com.woorea.openstack.quantum.model.NetworkForCreate;
import com.woorea.openstack.quantum.model.SubnetForCreate;

public class OpenStackCloudProvider {
    private static Logger logger = LoggerFactory.getLogger(OpenStackCloudProvider.class);

    private static int DEFAULT_RESOURCE_STATE_CHANGE_WAIT_TIME_IN_SECONDS = 240;

    private CloudProviderAccount cloudProviderAccount;

    private CloudProviderLocation cloudProviderLocation;

    private Map<String, String> keyPairMap = new HashMap<String, String>();

    private String tenantName;

    // private String novaEndPointName;

    private Nova novaClient;

    private Quantum quantum;

    private final Calendar expirationDate;

    public OpenStackCloudProvider(final ProviderTarget target) throws ConnectorException {
        this.cloudProviderAccount = target.getAccount();
        this.cloudProviderLocation = target.getLocation();

        Map<String, String> properties = this.cloudProviderAccount.getProperties();
        if (properties == null || properties.get("tenantName") == null) {
            throw new ConnectorException("No access to properties: tenantName");
        }
        this.tenantName = properties.get("tenantName");
        OpenStackCloudProvider.logger.info("connect user=" + this.cloudProviderAccount.getLogin() + " to tenant="
            + this.tenantName + " at KEYSTONE_AUTH_URL=" + this.cloudProviderAccount.getCloudProvider().getEndpoint());

        Keystone keystone = new Keystone(this.cloudProviderAccount.getCloudProvider().getEndpoint());
        Access access;
        try {
            access = keystone
                .tokens()
                .authenticate(
                    new UsernamePassword(this.cloudProviderAccount.getLogin(), this.cloudProviderAccount.getPassword()))
                .withTenantName(this.tenantName).execute();
        } catch (OpenStackResponseException e) {
            if (e.getStatus() == 401) {
                throw new ConnectorException("Unauthorized: authentication has failed\nCannot connect user="
                    + this.cloudProviderAccount.getLogin() + " to tenant=" + this.tenantName + " at KEYSTONE_AUTH_URL="
                    + this.cloudProviderAccount.getCloudProvider().getEndpoint() + "\ncause=" + e.getStatus() + ", message="
                    + e.getMessage(), e);
            } else if (e.getStatus() == 404) {
                throw new ConnectorException("The requested resource could not be found at KEYSTONE_AUTH_URL="
                    + this.cloudProviderAccount.getCloudProvider().getEndpoint() + "\nCannot connect user="
                    + this.cloudProviderAccount.getLogin() + " to tenant=" + this.tenantName + "\ncause=" + e.getStatus()
                    + ", message=" + e.getMessage(), e);
            } else {
                throw new ConnectorException("\nCannot connect user=" + this.cloudProviderAccount.getLogin() + " to tenant="
                    + this.tenantName + " at KEYSTONE_AUTH_URL=" + this.cloudProviderAccount.getCloudProvider().getEndpoint()
                    + "\ncause=" + e.getStatus() + ", message=" + e.getMessage(), e);
            }
        } catch (Exception e) {
            throw new ConnectorException("\nCannot connect user=" + this.cloudProviderAccount.getLogin() + " to tenant="
                + this.tenantName + " at KEYSTONE_AUTH_URL=" + this.cloudProviderAccount.getCloudProvider().getEndpoint(), e);
        }

        // use the token in the following requests
        keystone.token(access.getToken().getId());

        this.expirationDate = access.getToken().getExpires();

        /*java.lang.System.out.println("1="
            + KeystoneUtils.findEndpointURL(access.getServiceCatalog(), "compute", null, "public"));*/

        /*this.novaClient = new Nova("http://10.192.133.101:8774/v2".concat("/").concat(access.getToken().getTenant().getId()));*/
        this.novaClient = new Nova(KeystoneUtils.findEndpointURL(access.getServiceCatalog(), "compute", null, "public"));
        this.novaClient.token(access.getToken().getId());

        try {
            /*this.quantum = new Quantum(KeystoneUtils.findEndpointURL(access.getServiceCatalog(), "network", null, "public"));*/
            this.quantum = new Quantum(KeystoneUtils.findEndpointURL(access.getServiceCatalog(), "network", null, "public")
                .concat("v2.0/"));
            this.quantum.setTokenProvider(new OpenStackSimpleTokenProvider(access.getToken().getId()));
            // this.quantum.token(access.getToken().getId());
        } catch (RuntimeException e) {
            OpenStackCloudProvider.logger
                .info("### Neutron is not available in the Service Catalog. Message=" + e.getMessage());
            // throw new ConnectorException("message=" + e.getMessage(), e);
        }

        // check how to trace REST call (On/Off)
        /*novaClient.enableLogging(Logger.getLogger("nova"), 100 * 1024);*/

        /*Servers servers = novaClient.servers().list(true).execute();
        for(Server server : servers) {
            System.out.println("--- server:" + server);
        }*/

        /*com.woorea.openstack.quantum.model.Networks networks = this.quantum.networks().list().execute();
        for (com.woorea.openstack.quantum.model.Network network : networks) {
            System.out.println("--- network: " + network);
        }*/

        /*Flavors flavors = novaClient.flavors().list(true).execute();
        for(Flavor flavor : flavors) {
            System.out.println(flavor);
        }*/

        /*Images images = novaClient.images().list(true).execute();
        for(Image image : images) {
            System.out.println(image);
        }*/

    }

    public Calendar getExpirationDate() {
        return this.expirationDate;
    }

    public CloudProviderAccount getCloudProviderAccount() {
        return this.cloudProviderAccount;
    }

    public CloudProviderLocation getCloudProviderLocation() {
        return this.cloudProviderLocation;
    }

    //
    // Compute Service
    //

    /* proposed mapping: see http://docs.openstack.org/api/openstack-compute/2/content/List_Servers-d1e2078.html */
    public Machine.State fromServerStatusToMachineState(final String novaStatus) {
        if (novaStatus.equalsIgnoreCase("ACTIVE") || novaStatus.equalsIgnoreCase("PASSWORD")
            || novaStatus.equalsIgnoreCase("REBUILD") || novaStatus.equalsIgnoreCase("MIGRATING")
            || novaStatus.equalsIgnoreCase("RESIZE") || novaStatus.equalsIgnoreCase("REBOOT")
            || novaStatus.equalsIgnoreCase("HARD_REBOOT")) {
            return Machine.State.STARTED;
        } else if (novaStatus.equalsIgnoreCase("BUILD")) {
            return Machine.State.CREATING;
        } else if (novaStatus.equalsIgnoreCase("DELETED")) {
            return Machine.State.DELETED;
        } else if (novaStatus.equalsIgnoreCase("SUSPENDED")) {
            return Machine.State.SUSPENDED;
        } else if (novaStatus.equalsIgnoreCase("PAUSED")) {
            return Machine.State.PAUSED;
        } else if (novaStatus.equalsIgnoreCase("SHUTOFF")) {
            return Machine.State.STOPPED;
        } else if (novaStatus.equalsIgnoreCase("ERROR")) {
            return Machine.State.ERROR;
        } else {
            return Machine.State.UNKNOWN;
        }
    }

    private void fromServerToMachine(final String serverId, final Machine machine) throws ConnectorException {
        Server server = this.novaClient.servers().show(serverId).execute(); /*get a fresh server*/

        machine.setProviderAssignedId(serverId);
        machine.setName(server.getName());
        machine.setState(this.fromServerStatusToMachineState(server.getStatus()));

        // HW
        // Flavor flavor = server.getFlavor();
        // doesn't work (lazy instantiation)
        Flavor flavor = this.novaClient.flavors().show(server.getFlavor().getId()).execute();
        /*logger.info("flavor: " + flavor);*/

        machine.setCpu(new Integer(flavor.getVcpus()));
        machine.setMemory(flavor.getRam() * 1024);
        List<MachineDisk> machineDisks = new ArrayList<MachineDisk>();
        MachineDisk machineDisk = new MachineDisk();
        machineDisk.setCapacity(new Integer(flavor.getDisk()) * 1000 * 1000);
        machineDisks.add(machineDisk);
        if (flavor.getEphemeral() > 0) {
            MachineDisk machineEphemeralDisk = new MachineDisk();
            machineEphemeralDisk.setCapacity(flavor.getEphemeral() * 1000 * 1000);
            machineDisks.add(machineEphemeralDisk);

        }
        machine.setDisks(machineDisks);

        // Network
        List<MachineNetworkInterface> nics = new ArrayList<MachineNetworkInterface>();
        machine.setNetworkInterfaces(nics);
        /*OpenStackCloudProvider.logger.info("-- " + server.getAddresses().getAddresses().keySet());*/
        for (String networkName : server.getAddresses().getAddresses().keySet()) {
            Network cimiNetwork = null;
            if (this.quantum != null) {
                cimiNetwork = this.getNetworkByName(networkName);
            }
            // Nic
            MachineNetworkInterface machineNetworkInterface = new MachineNetworkInterface();
            // machineNetworkInterface.setName(networkName);
            machineNetworkInterface.setAddresses(new ArrayList<MachineNetworkInterfaceAddress>());
            machineNetworkInterface.setNetwork(cimiNetwork);
            machineNetworkInterface.setState(MachineNetworkInterface.InterfaceState.ACTIVE);
            // Addresses
            Collection<Addresses.Address> addresses = server.getAddresses().getAddresses().get(networkName);
            Iterator<Addresses.Address> iterator = addresses.iterator();
            while (iterator.hasNext()) {
                this.addAddress(iterator.next(), cimiNetwork, machineNetworkInterface);
            }
            if (machineNetworkInterface.getAddresses().size() > 0) {
                nics.add(machineNetworkInterface);
            }
        }

        // Volume
        List<MachineVolume> machineVolumes = new ArrayList<MachineVolume>();
        machine.setVolumes(machineVolumes);
        VolumeAttachments volumeAttachments = this.novaClient.servers().listVolumeAttachments(serverId).execute();
        Iterator<VolumeAttachment> iterator = volumeAttachments.iterator();
        while (iterator.hasNext()) {
            VolumeAttachment volumeAttachment = iterator.next();
            MachineVolume machineVolume = new MachineVolume();
            Volume volume = this.getVolume(volumeAttachment.getVolumeId());
            machineVolume.setVolume(volume);
            machineVolume.setProviderAssignedId(volumeAttachment.getId());
            com.woorea.openstack.nova.model.Volume novaVolume = this.novaClient.volumes().show(volumeAttachment.getVolumeId())
                .execute();
            machineVolume.setState(this.fromNovaVolumeStatusToCimiMachineVolumeState(novaVolume.getStatus()));
            machineVolume.setInitialLocation(volumeAttachment.getDevice());
            machineVolumes.add(machineVolume);
        }

        // Security Group
        List<SecurityGroup> securityGroups = new ArrayList<SecurityGroup>();
        machine.setSecurityGroups(securityGroups);
        // does not work: provides only the security group Name
        /*for (com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup : server.getSecurityGroups()) {
            securityGroups.add(this.getSecurityGroup(openStackSecurityGroup.getId()));
        }*/
        com.woorea.openstack.nova.model.SecurityGroups openstackSecurityGroups = this.novaClient.securityGroups()
            .listByServer(serverId).execute();
        for (com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup : openstackSecurityGroups) {
            final SecurityGroup securityGroup = new SecurityGroup();
            this.fromOpenstackSecurityGroupToCimiSecurityGroup(openStackSecurityGroup, securityGroup);
            securityGroups.add(securityGroup);
        }
    }

    public Machine createMachine(final MachineCreate machineCreate) throws ConnectorException, InterruptedException {
        OpenStackCloudProvider.logger.info("creating Machine for " + this.cloudProviderAccount.getLogin());

        ServerForCreate serverForCreate = new ServerForCreate();

        // name
        String serverName = null;
        if (machineCreate.getName() != null) {
            serverName = machineCreate.getName() + "-" + UUID.randomUUID();
        } else {
            serverName = "sirocco-" + UUID.randomUUID();
        }
        serverForCreate.setName(serverName);

        // flavor
        String flavorId = this.findSuitableFlavor(machineCreate.getMachineTemplate().getMachineConfig());
        if (flavorId == null) {
            throw new ConnectorException("Cannot find Nova flavor matching machineConfig");
        }
        serverForCreate.setFlavorRef(flavorId);

        // image
        ProviderMapping mapping = ProviderMapping.find(machineCreate.getMachineTemplate().getMachineImage(),
            this.cloudProviderAccount, this.cloudProviderLocation);
        if (mapping == null) {
            throw new ConnectorException("Cannot find imageId for image "
                + machineCreate.getMachineTemplate().getMachineImage().getName());
        }
        serverForCreate.setImageRef(mapping.getProviderAssignedId());

        // key pair
        String keyPairName = null;
        if (machineCreate.getMachineTemplate().getCredential() != null) {
            // String publicKey = new
            // String(machineCreate.getMachineTemplate().getCredential().getPublicKey());
            String publicKey = machineCreate.getMachineTemplate().getCredential().getPublicKey();
            keyPairName = this.getKeyPair(publicKey);
        }
        if (keyPairName != null) {
            serverForCreate.setKeyName(keyPairName);
        }

        // security group
        /*serverForCreate.getSecurityGroups().add(new ServerForCreate.SecurityGroup("default")); // default security group*/
        for (String securityGroupUuid : machineCreate.getMachineTemplate().getSecurityGroupUuids()) {
            SecurityGroup securityGroup = this.getSecurityGroup(securityGroupUuid);
            serverForCreate.getSecurityGroups().add(new ServerForCreate.SecurityGroup(securityGroup.getName()));
        }

        // network
        List<ServerForCreate.Network> networks = serverForCreate.getNetworks();
        // boolean allocateFloatingIp = false;
        if (machineCreate.getMachineTemplate().getNetworkInterfaces() != null) {
            for (MachineTemplateNetworkInterface nic : machineCreate.getMachineTemplate().getNetworkInterfaces()) {
                /*NB: nic template could refer either to a Network resource xor a SystemNetworkName
                In practice templates (generated by Sirocco) should refer to a Network resource when using an OpenStack connector*/
                networks.add(new ServerForCreate.Network(nic.getNetwork().getProviderAssignedId(), null, null));
                /*if (nic.getNetwork().getNetworkType() == Network.Type.PUBLIC) {
                    allocateFloatingIp = true;
                }*/
            }
        }

        // user data
        String userData = machineCreate.getMachineTemplate().getUserData();
        if (userData != null) {
            byte[] encoded = Base64.encodeBase64(userData.getBytes());
            userData = new String(encoded);
            serverForCreate.setUserData(userData);
        }

        // scheduler hints
        SchedulerHints schedulerHints = null;

        PlacementHint placementHint = machineCreate.getMachineTemplate().getPlacementHint();
        if (placementHint != null) {
            if (placementHint.getPlacementConstraint().equals(PlacementHint.AFFINITY_CONSTRAINT)) {
                schedulerHints = new SchedulerHints();
                schedulerHints.setSameHosts(placementHint.getMachineIds());
            } else if (placementHint.getPlacementConstraint().equals(PlacementHint.ANTI_AFFINITY_CONSTRAINT)) {
                schedulerHints = new SchedulerHints();
                schedulerHints.setDifferentHosts(placementHint.getMachineIds());
            }
        }

        // get the server
        Server server;

        if (schedulerHints == null) {
            server = this.novaClient.servers().boot(serverForCreate).execute();
        } else {
            ServerForCreateWithSchedulerHints serverForCreateWithSchedulerHints = new ServerForCreateWithSchedulerHints(
                serverForCreate, schedulerHints);
            server = this.novaClient.servers().boot(serverForCreateWithSchedulerHints).execute();
        }
        Machine machine = new Machine();
        try {
            server = this.novaClient.servers().show(server.getId()).execute(); /*get detailed information about the server*/

            // public IP
            /*if (allocateFloatingIp) {
                this.addFloatingIPToMachine(server.getId());
            }*/

            this.fromServerToMachine(server.getId(), machine);
        } catch (OpenStackResponseException ex) {
            this.cleanUpGhostServer(server);
            throw (ex);
        } /*catch (InterruptedException ex) {
            this.cleanUpGhostServer(server);
            throw (ex);
          }*/
        return machine;
    }

    private void cleanUpGhostServer(final Server server) {
        try {
            if (server != null) {
                this.deleteMachine(server.getId());
            }
        } catch (OpenStackResponseException e) {
        }
    }

    public Machine getMachine(final String machineId) throws ConnectorException {
        final Machine machine = new Machine();
        this.fromServerToMachine(machineId, machine);
        return machine;
    }

    public Machine.State getMachineState(final String machineId) {
        // Server server = getServer(machineId);
        Server server = this.novaClient.servers().show(machineId).execute();
        return this.fromServerStatusToMachineState(server.getStatus());
    }

    public void deleteMachine(final String machineId) {
        // this.freeFloatingIpsFromServer(machineId);
        this.novaClient.servers().delete(machineId).execute();
    }

    public void restartMachine(final String machineId, final boolean force) throws ConnectorException {
        this.novaClient.servers().reboot(machineId, force ? "HARD" : "SOFT").execute();
        /*TODO Pull4Request: woorea support of reboot */
        // throw new ConnectorException("unsupported operation");
    }

    public void addVolumeToMachine(final String machineId, final MachineVolume machineVolume) throws ConnectorException {
        String device = machineVolume.getInitialLocation();
        /*if (device == null) {
            throw new ConnectorException("device not specified");
        }*/
        this.novaClient.servers().attachVolume(machineId, machineVolume.getVolume().getProviderAssignedId(), device).execute();
    }

    public void removeVolumeFromMachine(final String machineId, final MachineVolume machineVolume) {
        this.novaClient.servers().detachVolume(machineId, machineVolume.getProviderAssignedId()).execute();
    }

    public void startMachine(final String machineId) throws ConnectorException {
        this.novaClient.servers().start(machineId).execute();
    }

    public void stopMachine(final String machineId, final boolean force) throws ConnectorException {
        /* The param force is ignored  
         * When set to "true", the Provider should forcefully stop the Machine, as opposed to a value of "false," 
         * which indicates that the Provider should attempt to gracefully stop the Machine
         * */
        this.novaClient.servers().stop(machineId).execute();
    }

    private void addAddress(final Addresses.Address openStackAddress, final Network cimiNetwork,
        final MachineNetworkInterface nic) {
        Address cimiAddress = new Address();
        this.fromOpenStackAddressToCimiAddress(openStackAddress, cimiAddress); /* cimiAddress.setResource(machine)! */
        MachineNetworkInterfaceAddress entry = new MachineNetworkInterfaceAddress();
        entry.setAddress(cimiAddress);
        nic.getAddresses().add(entry);
    }

    /*public Server getServer(String machineId) throws ConnectorException {
        try {
            return novaClient.servers().show(machineId).execute();
        } catch (OpenStackResponseException e) {
            System.out.println("- " + e.getMessage() 
                    + ", " + e.getStatus()
                    + ", " + e.getLocalizedMessage()
                    + ", " + e.getCause()
                    );
            if (e.getStatus() == 404){
                throw new ResourceNotFoundException(e);             
            }
            else{
                throw new ConnectorException(e);                
            }
        }
    }*/

    private String findSuitableFlavor(final MachineConfiguration machineConfig) {
        for (Flavor flavor : this.novaClient.flavors().list(true).execute()) {
            long memoryInKBytes = machineConfig.getMemory();
            long flavorMemoryInKBytes = flavor.getRam() * 1024;
            /*System.out.println(
                    "memoryInKBytes=" + memoryInKBytes 
                    + ", flavorMemoryInKBytes=" + flavorMemoryInKBytes
                    );*/
            if (memoryInKBytes == flavorMemoryInKBytes) {
                Integer flavorCpu = new Integer(flavor.getVcpus());
                /*if (machineConfig.getCpu() == flavor.getVcpus()) {
                  System.out.println(
                        "Cpu()=" + machineConfig.getCpu() 
                        + ", flavorCpu=" + flavorCpu
                        );*/
                if (machineConfig.getCpu().intValue() == flavorCpu.intValue()) {
                    /*System.out.println(
                            "machineConfig.getDisks().size()=" + machineConfig.getDisks().size()
                            );*/
                    /*if (machineConfig.getDisks().size() == 0) { 
                        return flavor.getId();
                    }
                    else */
                    if (machineConfig.getDisks().size() == 1 && flavor.getEphemeral() == 0) {
                        long diskSizeInKBytes = machineConfig.getDisks().get(0).getCapacity();
                        long flavorDiskSizeInKBytes = Long.parseLong(flavor.getDisk()) * 1000 * 1000;
                        if (diskSizeInKBytes == flavorDiskSizeInKBytes) {
                            return flavor.getId();
                        }
                    } else if (machineConfig.getDisks().size() == 2 && flavor.getEphemeral() > 0) {
                        long diskSizeInKBytes = machineConfig.getDisks().get(0).getCapacity();
                        long flavorDiskSizeInKBytes = Long.parseLong(flavor.getDisk()) * 1000 * 1000;
                        if (diskSizeInKBytes == flavorDiskSizeInKBytes) {
                            diskSizeInKBytes = machineConfig.getDisks().get(1).getCapacity();
                            flavorDiskSizeInKBytes = flavor.getEphemeral().longValue() * 1000 * 1000;
                            if (diskSizeInKBytes == flavorDiskSizeInKBytes) {
                                return flavor.getId();
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    public List<MachineConfiguration> getMachineConfigs() {
        List<MachineConfiguration> result = new ArrayList<>();
        for (Flavor flavor : this.novaClient.flavors().list(true).execute()) {
            MachineConfiguration machineConfig = new MachineConfiguration();
            machineConfig.setName(flavor.getName());
            machineConfig.setCpu(Integer.parseInt(flavor.getVcpus()));
            machineConfig.setMemory(flavor.getRam() * 1024);
            List<DiskTemplate> disks = new ArrayList<>();
            DiskTemplate disk = new DiskTemplate();
            disk.setCapacity(Integer.parseInt(flavor.getDisk()) * 1000 * 1000);
            disks.add(disk);
            if (flavor.getEphemeral() > 0) {
                disk = new DiskTemplate();
                disk.setCapacity(flavor.getEphemeral() * 1000 * 1000);
                disks.add(disk);
            }
            machineConfig.setDisks(disks);

            ProviderMapping providerMapping = new ProviderMapping();
            providerMapping.setProviderAssignedId(flavor.getId());
            providerMapping.setProviderAccount(this.cloudProviderAccount);
            machineConfig.setProviderMappings(Collections.singletonList(providerMapping));
            result.add(machineConfig);
        }
        return result;
    }

    private String getKeyPair(final String publicKey) {
        String keyPairName = OpenStackCloudProvider.this.keyPairMap.get(publicKey);
        if (keyPairName != null) {
            return keyPairName;
        }

        for (KeyPair keyPair : this.novaClient.keyPairs().list().execute()) {
            if (keyPair.getPublicKey().equals(publicKey)) {
                OpenStackCloudProvider.this.keyPairMap.put(publicKey, keyPair.getName());
                return keyPair.getName();
            }
        }

        KeyPair newKeyPair = this.novaClient.keyPairs().create("keypair-" + UUID.randomUUID().toString(), publicKey).execute();
        OpenStackCloudProvider.this.keyPairMap.put(publicKey, newKeyPair.getName());
        return newKeyPair.getName();
    }

    private String addFloatingIPToMachine(final String serverId) throws InterruptedException {
        int waitTimeInSeconds = OpenStackCloudProvider.DEFAULT_RESOURCE_STATE_CHANGE_WAIT_TIME_IN_SECONDS;
        do {
            Server server = this.novaClient.servers().show(serverId).execute();
            if (!server.getStatus().equalsIgnoreCase("BUILD")) {
                break;
            }
            Thread.sleep(1000);
        } while (waitTimeInSeconds-- > 0);

        // assumption: first FloatingIpPools is used to allocate floating IP
        FloatingIpPoolsExtension floatingIpPoolsExtension = new FloatingIpPoolsExtension(this.novaClient);
        FloatingIpPools pools = floatingIpPoolsExtension.list().execute();
        FloatingIp floatingIp = this.novaClient.floatingIps().allocate(pools.getList().get(0).getName()).execute();
        OpenStackCloudProvider.logger.info("Allocating floating IP " + floatingIp.getIp());
        this.novaClient.servers().associateFloatingIp(serverId, floatingIp.getIp()).execute();

        // Check if it is safe not to wait that the floating IP shows up in the
        // server detail
        /*do {
            Server server = novaClient.servers().show(serverId).execute();  
            if (this.findIpAddressOnServer(server, floatingIp.getIp())) {
                logger.info("Floating IP " + floatingIp.getIp() + " attached to server " + serverId);
                break;
            }
            Thread.sleep(1000);
        } while (waitTimeInSeconds-- > 0);*/

        return floatingIp.getIp();
    }

    private boolean findIpAddressOnServer(final Server server, final String ip) {
        for (String networkType : server.getAddresses().getAddresses().keySet()) {
            Collection<Addresses.Address> addresses = server.getAddresses().getAddresses().get(networkType);
            for (Addresses.Address address : addresses) {
                if (address.getAddr().equals(ip)) {
                    return true;
                }
            }
        }
        return false;
    }

    private void freeFloatingIpsFromServer(final String serverId) {
        for (FloatingIp floatingIp : this.novaClient.floatingIps().list().execute()) {
            if (floatingIp.getInstanceId() != null && floatingIp.getInstanceId().equals(serverId)) {
                OpenStackCloudProvider.logger.info("Releasing floating IP " + floatingIp.getIp() + " from server " + serverId);
                this.novaClient.servers().disassociateFloatingIp(serverId, floatingIp.getIp()).execute();
                this.novaClient.floatingIps().deallocate(floatingIp.getId()).execute();
            }
        }
    }

    //
    // Volume
    //

    /* proposed mapping: see http://docs.openstack.org/api/openstack-block-storage/2.0/content/Volumes.html */
    private Volume.State fromNovaVolumeStatusToCimiVolumeState(final String novaStatus) {
        if (novaStatus.equalsIgnoreCase("AVAILABLE")) {
            return Volume.State.AVAILABLE;
        } else if (novaStatus.equalsIgnoreCase("IN-USE")) {
            return Volume.State.AVAILABLE;
        } else if (novaStatus.equalsIgnoreCase("CREATING")) {
            return Volume.State.CREATING;
        } else if (novaStatus.equalsIgnoreCase("DELETING")) {
            return Volume.State.DELETING;
        } else {
            return Volume.State.ERROR; // CIMI mapping!
        }
    }

    /* proposed mapping: see http://docs.openstack.org/api/openstack-block-storage/2.0/content/Volumes.html */
    private MachineVolume.State fromNovaVolumeStatusToCimiMachineVolumeState(final String novaStatus) {
        if (novaStatus.equalsIgnoreCase("AVAILABLE")) {
            return MachineVolume.State.ATTACHED;
        } else if (novaStatus.equalsIgnoreCase("IN-USE")) {
            return MachineVolume.State.ATTACHED;
        } else if (novaStatus.equalsIgnoreCase("ATTACHING")) {
            return MachineVolume.State.ATTACHING;
        } else if (novaStatus.equalsIgnoreCase("DETACHING")) {
            // undocumented state!
            return MachineVolume.State.DETACHING;
        } else {
            return MachineVolume.State.ERROR; // CIMI mapping!
        }
    }

    private void fromNovaVolumeToCimiVolume(final String volumeId, final Volume cimiVolume) {
        com.woorea.openstack.nova.model.Volume novaVolume = this.novaClient.volumes().show(volumeId).execute();

        cimiVolume.setName(novaVolume.getName());
        cimiVolume.setDescription(novaVolume.getDescription());
        cimiVolume.setProviderAssignedId(novaVolume.getId());
        // cimiVolume.setState(this.getVolumeState(volumeId));
        cimiVolume.setState(this.fromNovaVolumeStatusToCimiVolumeState(novaVolume.getStatus()));
        cimiVolume.setCapacity(novaVolume.getSize() * 1000 * 1000); /*GB to KB*/
    }

    public Volume createVolume(final VolumeCreate volumeCreate) throws ConnectorException {
        OpenStackCloudProvider.logger.info("creating Volume for " + this.cloudProviderAccount.getLogin());

        VolumeForCreate volumeForCreate = new VolumeForCreate();

        String volumeName = null;
        if (volumeCreate.getName() != null) {
            volumeName = volumeCreate.getName() + "-" + UUID.randomUUID();
        } else {
            volumeName = "sirocco-" + UUID.randomUUID();
        }
        volumeForCreate.setName(volumeName);
        volumeForCreate.setDescription(volumeCreate.getDescription());

        VolumeConfiguration volumeConfig = volumeCreate.getVolumeTemplate().getVolumeConfig();
        int sizeInGB = volumeConfig.getCapacity() / (1000 * 1000);
        volumeForCreate.setSize(new Integer(sizeInGB));

        com.woorea.openstack.nova.model.Volume novaVolume = this.novaClient.volumes().create(volumeForCreate).execute();

        final Volume cimiVolume = new Volume();
        this.fromNovaVolumeToCimiVolume(novaVolume.getId(), cimiVolume);
        return cimiVolume;
    }

    public Volume getVolume(final String volumeId) {
        final Volume volume = new Volume();
        this.fromNovaVolumeToCimiVolume(volumeId, volume);
        return volume;
    }

    public Volume.State getVolumeState(final String volumeId) {
        com.woorea.openstack.nova.model.Volume novaVolume = this.novaClient.volumes().show(volumeId).execute();
        return this.fromNovaVolumeStatusToCimiVolumeState(novaVolume.getStatus());
    }

    public void deleteVolume(final String volumeId) {
        this.novaClient.volumes().delete(volumeId).execute();
    }

    //
    // Network
    //

    /* proposed mapping: see http://docs.openstack.org/api/openstack-network/2.0/content/Concepts-d1e369.html */
    private Network.State fromNovaNetworkStatusToCimiNetworkState(final String novaStatus) {
        if (novaStatus.equalsIgnoreCase("ACTIVE")) {
            return Network.State.STARTED;
        } else if (novaStatus.equalsIgnoreCase("BUILD")) {
            return Network.State.CREATING;
        } else {
            return Network.State.ERROR; // CIMI mapping!
        }
    }

    private void fromNovaNetworkToCimiNetwork(final String networkId, final Network cimiNetwork) throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("Neutron is not available in the Service Catalog");
        }

        com.woorea.openstack.quantum.model.Network openStackNetwork = this.quantum.networks().show(networkId).execute();

        cimiNetwork.setName(openStackNetwork.getName());
        cimiNetwork.setProviderAssignedId(openStackNetwork.getId());
        cimiNetwork.setState(this.fromNovaNetworkStatusToCimiNetworkState(openStackNetwork.getStatus()));
        /*if (openStackNetwork.getName().equals(this.cimiPublicNetworkName)) {
            cimiNetwork.setNetworkType(Network.Type.PUBLIC);
        } else {
            cimiNetwork.setNetworkType(Network.Type.PRIVATE);
        }*/

        List<Subnet> subnets = new ArrayList<Subnet>();
        cimiNetwork.setSubnets(subnets);
        // 2 options
        for (String openStackSubnetId : openStackNetwork.getSubnets()) {
            com.woorea.openstack.quantum.model.Subnet openStackSubnet = this.quantum.subnets().show(openStackSubnetId)
                .execute();
            Subnet subnet = new Subnet();
            subnet.setCidr(openStackSubnet.getCidr());
            subnet.setEnableDhcp(openStackSubnet.isEnableDHCP());
            subnet.setName(openStackSubnet.getName());
            subnet.setProviderAssignedId(openStackSubnet.getId());
            subnet.setState(Subnet.State.AVAILABLE);
            if (openStackSubnet.getIpversion().toString().equalsIgnoreCase("4")) {
                subnet.setProtocol("IPv4");
            } else if (openStackSubnet.getIpversion().toString().equalsIgnoreCase("6")) {
                subnet.setProtocol("IPv6");
            } else {
                subnet.setProtocol(openStackSubnet.getIpversion().toString());
            }
            subnets.add(subnet);
        }
        /*for (com.woorea.openstack.quantum.model.Subnet openStackSubnet : this.quantum.subnets().list().execute()) {
            if (openStackSubnet.getNetworkId() == openStackNetwork.getId()) {
                Subnet subnet = new Subnet();
                subnet.setCidr(openStackSubnet.getCidr());
                subnet.setEnableDhcp(openStackSubnet.isEnableDHCP());
                subnet.setName(openStackSubnet.getName());
                subnet.setProviderAssignedId(openStackSubnet.getId());
                subnets.add(subnet);
            }
        }*/
    }

    private Network getNetworkByName(final String networkName) throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("Neutron is not available in the Service Catalog");
        }

        com.woorea.openstack.quantum.model.Networks openStackNetworks = this.quantum.networks().list().execute();
        for (com.woorea.openstack.quantum.model.Network openStackNetwork : openStackNetworks) {
            if (openStackNetwork.getName().equals(networkName)) {
                return this.getNetwork(openStackNetwork.getId());
            }
        }
        throw new ConnectorException("Cannot find network for network name:" + networkName);
    }

    public Network createNetwork(final NetworkCreate networkCreate) throws ConnectorException, InterruptedException {
        if (this.quantum == null) {
            throw new ConnectorException("Neutron is not available in the Service Catalog");
        }

        OpenStackCloudProvider.logger.info("creating Network for " + this.cloudProviderAccount.getLogin());

        if (networkCreate.getNetworkTemplate().getNetworkConfig().getSubnets().size() == 0) {
            throw new ConnectorException("Cannot find subnet configuration");
        }

        NetworkForCreate networkForCreate = new NetworkForCreate();

        String networkName = null;
        if (networkCreate.getName() != null) {
            networkName = networkCreate.getName() + "-" + UUID.randomUUID();
        } else {
            networkName = "sirocco-" + UUID.randomUUID();
        }
        networkForCreate.setName(networkName);
        networkForCreate.setAdminStateUp(true); /* set the administrative status of the network to UP */

        com.woorea.openstack.quantum.model.Network openStackNetwork = this.quantum.networks().create(networkForCreate)
            .execute();
        final Network cimiNetwork = new Network();

        try {
            do {
                openStackNetwork = this.quantum.networks().show(openStackNetwork.getId()).execute();
                if (openStackNetwork.getStatus().equalsIgnoreCase("ACTIVE")) {
                    break;
                }
                Thread.sleep(1000);
            } while (OpenStackCloudProvider.DEFAULT_RESOURCE_STATE_CHANGE_WAIT_TIME_IN_SECONDS-- > 0);

            for (SubnetConfig subnetConfig : networkCreate.getNetworkTemplate().getNetworkConfig().getSubnets()) {
                SubnetForCreate subnetForCreate = new SubnetForCreate();
                subnetForCreate.setName(subnetConfig.getName());
                subnetForCreate.setNetworkId(openStackNetwork.getId());
                subnetForCreate.setCidr(subnetConfig.getCidr());
                if (subnetConfig.getProtocol().equalsIgnoreCase("IPv4")) {
                    subnetForCreate.setIpVersion(4);
                } else if (subnetConfig.getProtocol().equalsIgnoreCase("IPv6")) {
                    subnetForCreate.setIpVersion(6);
                } else {
                    // subnetForCreate.setIpVersion(subnet.getProtocol());
                    throw new ConnectorException("Invalid input for ip_version. Reason: " + subnetConfig.getProtocol()
                        + " is not in [IPv4, IPv6].");
                }
                /* FIXME
                 * - Woorea bug: SubnetForCreate: EnableDhcp not supported (pull4request TBD)
                 */
                // subnetForCreate.setEnableDhcp(subnet.isEnableDhcp());

                this.quantum.subnets().create(subnetForCreate).execute();
            }

            this.fromNovaNetworkToCimiNetwork(openStackNetwork.getId(), cimiNetwork);
        } catch (ConnectorException ex) {
            this.cleanUpGhostNetwork(openStackNetwork);
            throw (ex);
        } catch (OpenStackResponseException ex) {
            this.cleanUpGhostNetwork(openStackNetwork);
            throw (ex);
        } catch (InterruptedException ex) {
            this.cleanUpGhostNetwork(openStackNetwork);
            throw (ex);
        }
        return cimiNetwork;
    }

    private void cleanUpGhostNetwork(final com.woorea.openstack.quantum.model.Network openStackNetwork)
        throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("Neutron is not available in the Service Catalog");
        }

        try {
            if (openStackNetwork != null) {
                this.deleteNetwork(openStackNetwork.getId());
            }
        } catch (OpenStackResponseException e) {
        }
    }

    public Network getNetwork(final String networkId) throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("Neutron is not available in the Service Catalog");
        }

        final Network network = new Network();
        this.fromNovaNetworkToCimiNetwork(networkId, network);
        return network;
    }

    public Network.State getNetworkState(final String networkId) throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("Neutron is not available in the Service Catalog");
        }

        com.woorea.openstack.quantum.model.Network openStackNetwork = this.quantum.networks().show(networkId).execute();
        return this.fromNovaNetworkStatusToCimiNetworkState(openStackNetwork.getStatus());
    }

    public List<Network> getNetworks() throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("Neutron is not available in the Service Catalog");
        }

        ArrayList<Network> networks = new ArrayList<Network>();

        com.woorea.openstack.quantum.model.Networks openStackNetworks = this.quantum.networks().list().execute();
        for (com.woorea.openstack.quantum.model.Network openStackNetwork : openStackNetworks) {
            /*System.out.println("--- network: " + openStackNetwork);*/
            if (openStackNetwork.getRouterExternal().equalsIgnoreCase("true")) {
                continue;
            }
            networks.add(this.getNetwork(openStackNetwork.getId()));
        }
        return networks;
    }

    public void deleteNetwork(final String networkId) throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("Neutron is not available in the Service Catalog");
        }

        /* FIXME woorea Bug : err 409 ignored when trying to delete a network attached to servers */
        this.quantum.networks().delete(networkId).execute();
    }

    //
    // Network : Security Group
    //

    private void fromOpenstackSecurityGroupToCimiSecurityGroup(
        final com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup, final SecurityGroup cimiSecurityGroup)
        throws ResourceNotFoundException {

        cimiSecurityGroup.setName(openStackSecurityGroup.getName());
        cimiSecurityGroup.setDescription(openStackSecurityGroup.getDescription());
        cimiSecurityGroup.setProviderAssignedId(openStackSecurityGroup.getId());
        cimiSecurityGroup.setState(SecurityGroup.State.AVAILABLE);

        // Add Rules
        for (com.woorea.openstack.nova.model.SecurityGroup.Rule openStackRule : openStackSecurityGroup.getRules()) {
            SecurityGroupRule rule = new SecurityGroupRule();
            rule.setProviderAssignedId(openStackRule.getId());
            rule.setParentGroup(cimiSecurityGroup);
            rule.setIpProtocol(openStackRule.getIpProtocol());
            rule.setFromPort(openStackRule.getFromPort());
            rule.setToPort(openStackRule.getToPort());
            if (openStackRule.getGroup() == null || openStackRule.getGroup().getName() == null) {
                rule.setSourceIpRange(openStackRule.getIpRange().getCidr());
            } else {
                SecurityGroup sourceCimiSecurityGroup = new SecurityGroup();
                com.woorea.openstack.nova.model.SecurityGroup sourceOpenStackSecurityGroup = this
                    .getOpenstackSecurityGroupsByName(openStackRule.getGroup().getName());
                sourceCimiSecurityGroup.setProviderAssignedId(sourceOpenStackSecurityGroup.getId());
                rule.setSourceGroup(sourceCimiSecurityGroup);
            }

            cimiSecurityGroup.getRules().add(rule);
        }

    }

    private com.woorea.openstack.nova.model.SecurityGroup getOpenstackSecurityGroupsByName(final String securityGroupName)
        throws ResourceNotFoundException {
        com.woorea.openstack.nova.model.SecurityGroups openStackSecurityGroups = this.novaClient.securityGroups()
            .listSecurityGroups().execute();
        for (com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup : openStackSecurityGroups) {
            if (openStackSecurityGroup.getName().equals(securityGroupName)) {
                return openStackSecurityGroup;
            }
        }
        throw new ResourceNotFoundException("No security group with the name=" + securityGroupName);
    }

    public String createSecurityGroup(final SecurityGroupCreate create) throws ConnectorException {
        OpenStackCloudProvider.logger.info("creating SecurityGroup for " + this.cloudProviderAccount.getLogin());

        com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup = this.novaClient.securityGroups()
            .createSecurityGroup(create.getName(), create.getDescription()).execute();

        return openStackSecurityGroup.getId();
    }

    public SecurityGroup getSecurityGroup(final String groupId) throws ConnectorException {
        com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup = this.novaClient.securityGroups()
            .showSecurityGroup(groupId).execute();

        final SecurityGroup securityGroup = new SecurityGroup();
        this.fromOpenstackSecurityGroupToCimiSecurityGroup(openStackSecurityGroup, securityGroup);
        return securityGroup;
    }

    public List<SecurityGroup> getSecurityGroups() throws ConnectorException {
        ArrayList<SecurityGroup> securityGroups = new ArrayList<SecurityGroup>();

        com.woorea.openstack.nova.model.SecurityGroups openStackSecurityGroups = this.novaClient.securityGroups()
            .listSecurityGroups().execute();
        for (com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup : openStackSecurityGroups) {
            securityGroups.add(this.getSecurityGroup(openStackSecurityGroup.getId()));
        }
        return securityGroups;
    }

    public void deleteSecurityGroup(final String groupId) throws ConnectorException {
        OpenStackCloudProvider.logger.info("deleting SecurityGroup for " + this.cloudProviderAccount.getLogin());
        this.novaClient.securityGroups().deleteSecurityGroup(groupId).execute();
    }

    public String addRuleToSecurityGroup(final String groupId, final SecurityGroupRule rule) {
        com.woorea.openstack.nova.model.SecurityGroup.Rule openStackSecurityGroupRule;

        if (rule.getSourceGroup() == null) {
            openStackSecurityGroupRule = this.novaClient
                .securityGroups()
                .createSecurityGroupRule(groupId, rule.getIpProtocol(), rule.getFromPort(), rule.getToPort(),
                    rule.getSourceIpRange()).execute();
        } else {
            openStackSecurityGroupRule = this.novaClient
                .securityGroups()
                .createSecurityGroupRule(groupId, rule.getSourceGroup().getProviderAssignedId(), rule.getIpProtocol(),
                    rule.getFromPort(), rule.getToPort()).execute();
        }

        // return rule id
        return openStackSecurityGroupRule.getId();
    }

    public void deleteRuleFromSecurityGroup(final String groupId, final SecurityGroupRule rule) {
        this.novaClient.securityGroups().deleteSecurityGroupRule(rule.getProviderAssignedId()).execute();
    }

    public void addMachineToSecurityGroup(final String machineId, final String groupId) {
        com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup = this.novaClient.securityGroups()
            .showSecurityGroup(groupId).execute();
        this.novaClient.servers().addSecurityGroupAction(machineId, openStackSecurityGroup.getName()).execute();
    }

    public void removeMachineFromSecurityGroup(final String machineId, final String groupId) {
        com.woorea.openstack.nova.model.SecurityGroup openStackSecurityGroup = this.novaClient.securityGroups()
            .showSecurityGroup(groupId).execute();
        this.novaClient.servers().removeSecurityGroupAction(machineId, openStackSecurityGroup.getName()).execute();
    }

    //
    // Network : (floating IP) Address
    //

    private void fromFloatingIpToCimiAddress(final FloatingIp floatingIp, final Address cimiAddress) {

        cimiAddress.setProviderAssignedId(floatingIp.getId());
        cimiAddress.setIp(floatingIp.getIp());
        cimiAddress.setInternalIp(floatingIp.getFixedIp());
        cimiAddress.setState(Address.State.CREATED);
        if (floatingIp.getInstanceId() != null) {
            Machine machine = new Machine();
            machine.setProviderAssignedId(floatingIp.getInstanceId());
            cimiAddress.setResource(machine);
        }
        cimiAddress.setAllocation("dynamic"); /* static! */
        cimiAddress.setProtocol("IPv4"); /* static! */
    }

    private void fromOpenStackAddressToCimiAddress(final Addresses.Address openStackAddress, final Address cimiAddress) {

        /* TBC: ProviderAssignedId, InternalIp */
        cimiAddress.setIp(openStackAddress.getAddr());
        cimiAddress.setState(Address.State.CREATED);
        cimiAddress.setAllocation("dynamic"); /* static! */
        if (openStackAddress.getVersion().equalsIgnoreCase("4")) { /* cimi mapping 4/IPv4! */
            cimiAddress.setProtocol("IPv4");
        } else if (openStackAddress.getVersion().equalsIgnoreCase("6")) {
            cimiAddress.setProtocol("IPv6");
        } else {
            cimiAddress.setProtocol(openStackAddress.getVersion()); // default!
        }
    }

    public List<Address> getAddresses() throws ConnectorException {
        ArrayList<Address> addresses = new ArrayList<Address>();

        FloatingIps floatingIps = this.novaClient.floatingIps().list().execute();
        for (FloatingIp floatingIp : floatingIps) {
            Address cimiAddress = new Address();
            this.fromFloatingIpToCimiAddress(floatingIp, cimiAddress);
            addresses.add(cimiAddress);
        }
        return addresses;
    }

    public Address allocateAddress(final Map<String, String> properties) throws ConnectorException {
        /* assumption: first FloatingIpPools is used to allocate floating IP (TBC if pool is optional) */
        FloatingIpPoolsExtension floatingIpPoolsExtension = new FloatingIpPoolsExtension(this.novaClient);
        FloatingIpPools pools = floatingIpPoolsExtension.list().execute();
        FloatingIp floatingIp = this.novaClient.floatingIps().allocate(pools.getList().get(0).getName()).execute();
        OpenStackCloudProvider.logger.info("Allocating floating IP " + floatingIp.getIp());

        Address cimiAddress = new Address();
        this.fromFloatingIpToCimiAddress(floatingIp, cimiAddress);
        return cimiAddress;
    }

    public void deallocateAddress(final Address cimiAddress) throws ConnectorException {
        OpenStackCloudProvider.logger.info("Deallocating floating IP " + cimiAddress.getIp());
        this.novaClient.floatingIps().deallocate(cimiAddress.getProviderAssignedId()).execute();
    }

    public void addAddressToMachine(final String machineId, final Address cimiAddress) throws ConnectorException {
        OpenStackCloudProvider.logger.info("Adding floating IP " + cimiAddress.getIp() + " to server " + machineId);
        this.novaClient.servers().associateFloatingIp(machineId, cimiAddress.getIp()).execute();
    }

    public void removeAddressFromMachine(final String machineId, final Address cimiAddress) throws ConnectorException {
        OpenStackCloudProvider.logger.info("Removing floating IP " + cimiAddress.getIp() + " from server " + machineId);
        this.novaClient.servers().disassociateFloatingIp(machineId, cimiAddress.getIp()).execute();
    }

    //
    // Network : Forwarding group
    //

    /* FIXME 
     * Mapping : Sirocco FG / Bagpipe VPN profile 
     * - create FG (list of ntwk) / Bagpipe API calls (ntwk.associate(vpnProfile)
     * - delete FG (list of ntwk) / Bagpipe API calls (ntwk.dissociate(vpnProfile)
     * */
    public ForwardingGroup createForwardingGroup(final ForwardingGroupCreate forwardingGroupCreate) throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("### Neutron is not available in the Service Catalog");
        }

        OpenStackCloudProvider.logger.info("creating Fowarding Group for " + this.cloudProviderAccount.getLogin());

        ForwardingGroup forwardingGroup = new ForwardingGroup();
        forwardingGroup.setState(ForwardingGroup.State.AVAILABLE);
        List<ForwardingGroupNetwork> forwardingGroupnetworks = new ArrayList<ForwardingGroupNetwork>();
        forwardingGroup.setNetworks(forwardingGroupnetworks);

        Iterator<Network> iterator = forwardingGroupCreate.getForwardingGroupTemplate().getNetworks().iterator();
        Network sourceNetwork;
        com.woorea.openstack.quantum.model.Network sourceOpenStackNetwork;
        if (iterator.hasNext()) {
            sourceNetwork = iterator.next();
            sourceOpenStackNetwork = this.quantum.networks().show(sourceNetwork.getProviderAssignedId()).execute();
            ForwardingGroupNetwork forwardingGroupNetwork = new ForwardingGroupNetwork();
            forwardingGroupNetwork.setState(ForwardingGroupNetwork.State.AVAILABLE);
            forwardingGroupNetwork.setNetwork(sourceNetwork);
            forwardingGroupnetworks.add(forwardingGroupNetwork);

        } else {
            return forwardingGroup;
        }

        while (iterator.hasNext()) {
            Network targetNetwork = iterator.next();
            com.woorea.openstack.quantum.model.Network targetOpenStackNetwork = this.quantum.networks()
                .show(targetNetwork.getProviderAssignedId()).execute();

            // associate vpn profile
            this.quantum.networks()
                .associateVpnProfile(sourceOpenStackNetwork.getId(), targetOpenStackNetwork.getDefaultVpnProfile().getId())
                .execute();

            ForwardingGroupNetwork forwardingGroupNetwork = new ForwardingGroupNetwork();
            forwardingGroupNetwork.setState(ForwardingGroupNetwork.State.AVAILABLE);
            forwardingGroupNetwork.setNetwork(targetNetwork);
            forwardingGroupnetworks.add(forwardingGroupNetwork);

            sourceNetwork = targetNetwork;
            sourceOpenStackNetwork = targetOpenStackNetwork;
        }

        return forwardingGroup;
    }

    public void deleteForwardingGroup(final ForwardingGroup forwardingGroup) throws ConnectorException {
        if (this.quantum == null) {
            throw new ConnectorException("### Neutron is not available in the Service Catalog");
        }

        OpenStackCloudProvider.logger.info("deleting Fowarding Group for " + this.cloudProviderAccount.getLogin());

        Iterator<ForwardingGroupNetwork> iterator = forwardingGroup.getNetworks().iterator();
        Network sourceNetwork;
        com.woorea.openstack.quantum.model.Network sourceOpenStackNetwork;
        if (iterator.hasNext()) {
            sourceNetwork = iterator.next().getNetwork();
            sourceOpenStackNetwork = this.quantum.networks().show(sourceNetwork.getProviderAssignedId()).execute();

        } else {
            return;
        }

        while (iterator.hasNext()) {
            Network targetNetwork = iterator.next().getNetwork();
            com.woorea.openstack.quantum.model.Network targetOpenStackNetwork = this.quantum.networks()
                .show(targetNetwork.getProviderAssignedId()).execute();

            // dissociate vpn profile
            this.quantum.networks()
                .dissociateVpnProfile(sourceOpenStackNetwork.getId(), targetOpenStackNetwork.getDefaultVpnProfile().getId())
                .execute();

            sourceNetwork = targetNetwork;
            sourceOpenStackNetwork = targetOpenStackNetwork;
        }
    }

    //
    // Image
    //

    private MachineImage.State fromNovaImageStatusToCimiMachineImageState(final String novaStatus) {
        switch (novaStatus) {
        case "ACTIVE":
            return MachineImage.State.AVAILABLE;
        case "SAVING":
            return MachineImage.State.CREATING;
        case "ERROR":
            return MachineImage.State.ERROR;
        case "DELETED":
            return MachineImage.State.DELETED;
        default:
            return MachineImage.State.ERROR;
        }
    }

    public MachineImage getMachineImage(final String machineImageId) {
        MachineImage machineImage = new MachineImage();
        Image image = this.novaClient.images().show(machineImageId).execute();
        machineImage.setName(image.getName());
        machineImage.setState(this.fromNovaImageStatusToCimiMachineImageState(image.getStatus()));
        machineImage.setType(Type.IMAGE);
        ProviderMapping providerMapping = new ProviderMapping();
        providerMapping.setProviderAssignedId(image.getId());
        providerMapping.setProviderAccount(this.cloudProviderAccount);
        providerMapping.setProviderLocation(this.cloudProviderLocation);
        machineImage.setProviderMappings(Collections.singletonList(providerMapping));
        return machineImage;
    }

    public List<MachineImage> getMachineImages(final boolean returnPublicImages, final Map<String, String> searchCriteria) {
        List<MachineImage> result = new ArrayList<MachineImage>();
        Images images = this.novaClient.images().list(true).execute();
        for (Image image : images) {
            // no distinction between between images and snaphots in Havana
            MachineImage machineImage = this.getMachineImage(image.getId());
            result.add(machineImage);
        }
        return result;
    }

    public void deleteMachineImage(final String machineImageId) {
        this.novaClient.images().delete(machineImageId).execute();
    }

    public Quota getQuota() {
        AbsoluteLimit limits = this.novaClient.quotaSets().showUsedLimits().execute().getAbsolute();
        Quota quota = new Quota();
        List<Quota.Resource> resourceQuotas = new ArrayList<Quota.Resource>();
        quota.setResources(resourceQuotas);

        if (limits.getMaxTotalInstances() != null) {
            Quota.Resource vmCountQuota = new Quota.Resource(ResourceType.VIRTUAL_MACHINE, Unit.COUNT);
            vmCountQuota.setLimit(limits.getMaxTotalInstances());
            vmCountQuota.setUsed(limits.getTotalInstancesUsed());
            resourceQuotas.add(vmCountQuota);
        }

        if (limits.getMaxTotalRAMSize() != null) {
            Quota.Resource memoryQuota = new Quota.Resource(ResourceType.MEMORY, Unit.MEGABYTE);
            memoryQuota.setLimit(limits.getMaxTotalRAMSize());
            memoryQuota.setUsed(limits.getTotalRAMUsed());
            resourceQuotas.add(memoryQuota);
        }

        if (limits.getMaxTotalVolumeGigabytes() != null) {
            Quota.Resource storageQuota = new Quota.Resource(ResourceType.DISK_SPACE, Unit.GIGABYTE);
            storageQuota.setLimit(limits.getMaxTotalVolumeGigabytes());
            storageQuota.setUsed(limits.getTotalVolumeGigabytesUsed());
            resourceQuotas.add(storageQuota);
        }

        if (limits.getMaxTotalCores() != null) {
            Quota.Resource cpuCountQuota = new Quota.Resource(ResourceType.CPU, Unit.COUNT);
            cpuCountQuota.setLimit(limits.getMaxTotalCores());
            cpuCountQuota.setUsed(limits.getTotalCoresUsed());
            resourceQuotas.add(cpuCountQuota);
        }

        if (limits.getMaxTotalFloatingIps() != null) {
            Quota.Resource externalIpCountQuota = new Quota.Resource(ResourceType.EXTERNAL_IP, Unit.COUNT);
            externalIpCountQuota.setLimit(limits.getMaxTotalFloatingIps());
            externalIpCountQuota.setUsed(limits.getTotalFloatingIpsUsed());
            resourceQuotas.add(externalIpCountQuota);
        }
        return quota;
    }
}
