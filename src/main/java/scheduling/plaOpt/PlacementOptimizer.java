package scheduling.plaOpt;

import configuration.XHost;
import configuration.XVM;
import gipad.configuration.configuration.*;
import gipad.plan.action.Migration;
import gipad.plan.action.VirtualMachineAction;
import gipad.plan.choco.SimpleReconfigurationProblem;
import org.apache.log4j.Logger;
import org.discovery.DiscoveryModel.model.*;
import org.discovery.DiscoveryModel.model.NetworkInterface;
import org.discovery.DiscoveryModel.model.Node;
import org.discovery.DiscoveryModel.model.Units;
import org.discovery.DiscoveryModel.model.VirtualMachine;
import org.discovery.DiscoveryModel.model.VirtualMachineStates;
import scheduling.Scheduler;
import simulation.CentralizedResolver;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: alebre
 * Date: 21/01/14
 * Time: 13:02
 * To change this template use File | Settings | File Templates.
 */
public class PlacementOptimizer implements Scheduler {

    private final List<gipad.configuration.configuration.Node> nodes;
    private final List<gipad.configuration.configuration.VirtualMachine> vms;
    private final List<gipad.configuration.configuration.Node> nodesOfVM;
    private final Logger logger;
    private SimpleReconfigurationProblem SRP;
    private Map<gipad.configuration.configuration.Node, String> nodeName;
    private Map<gipad.configuration.configuration.VirtualMachine, String> vmName;

    public PlacementOptimizer(Collection<XHost> hosts) {
        logger = Logger.getLogger(PlacementOptimizer.class);
        Object[] res = (Object[]) extractGIPADConfiguration(hosts);
        nodes = (List<gipad.configuration.configuration.Node>) res[0];
        vms = (List<gipad.configuration.configuration.VirtualMachine>) res[1];
        nodesOfVM = (List<gipad.configuration.configuration.Node>) res[2];
    }

    Object extractConfiguration(Collection<XHost> xhosts){

        ArrayList<Node> nodes = new ArrayList<Node>();
        Node  node = null;

        // Add nodes
        for (XHost tmpH:xhosts){

            //Hardware Specification
            ArrayList<Cpu> cpus = new ArrayList<Cpu>();
            cpus.add(new Cpu(tmpH.getNbCores(), tmpH.getCPUCapacity()/tmpH.getNbCores()));

            ArrayList<NetworkInterface> nets =   new ArrayList<NetworkInterface>();
            nets.add(new NetworkInterface("eth0", tmpH.getNetBW()   * Units.GIGA()));

                    HardwareSpecification nodeHardwareSpecification = new HardwareSpecification(
                     cpus,
                     nets,
                    // StorageDevice are not yet implemented within the Simgrid framework
                    new ArrayList<StorageDevice>() {{
                        add(new StorageDevice("hd0", 512 * Units.GIGA()));
                    }},

                    new Memory(tmpH.getMemSize() * Units.MEGA())
            );

            Location nodeLocation = new Location(tmpH.getIP(), 3000);
            ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();
            node = new Node(tmpH.getName(), nodeHardwareSpecification, nodeLocation, vms);

            for(XVM tmpVM:tmpH.getRunnings()) {
                ArrayList<Cpu> cpusVM = new ArrayList<Cpu>();
                Cpu tmpCpu = new Cpu((int)tmpVM.getCoreNumber(), 100);
                tmpCpu.setUsage(tmpVM.getCPUDemand());

//                tmpVM.getLoad();
                cpusVM.add(tmpCpu);

                ArrayList<NetworkInterface> netsVM = new ArrayList<NetworkInterface>();
                nets.add(new NetworkInterface("eth0", tmpVM.getNetBW() * Units.MEGA()));

                HardwareSpecification vmHardwareSpecification = new HardwareSpecification(
                        cpusVM,
                        netsVM,
                        // Not used see above
                        new ArrayList<StorageDevice>() {{
                            add(new StorageDevice("hd0", 100 * Units.GIGA()));
                        }},
                        new Memory(tmpVM.getMemSize()* Units.MEGA())
                );


                // TODO 1./ Jonathan should add networkSpecification for a VM.
                // TODO 2./ Jonathan should encaspulates networkSpecification into HardwareSpecification (net should appear at
                // the same level than CPU/mem/...
                node.addVm(new VirtualMachine(tmpVM.getName(),  new VirtualMachineStates.Running(), vmHardwareSpecification));

            }
            nodes.add(node);
        }

        return nodes;
    }

    Object extractGIPADConfiguration(Collection<XHost> xhosts){
        /**
         * generate random bandwith for each vm between 1 and 3
         */
        Random r = new Random(0);
        int bw;
//        gipad.configuration.configuration.Node
        List<gipad.configuration.configuration.Node> nodes = new ArrayList<gipad.configuration.configuration.Node>();
        List<gipad.configuration.configuration.Node> nodesOfVM = new ArrayList<gipad.configuration.configuration.Node>();
        List<gipad.configuration.configuration.VirtualMachine> vms = new ArrayList<gipad.configuration.configuration.VirtualMachine>();
        gipad.configuration.configuration.Node node;
        gipad.configuration.configuration.VirtualMachine vm;

        nodeName = new HashMap<gipad.configuration.configuration.Node, String>();
        vmName = new HashMap<gipad.configuration.configuration.VirtualMachine, String>();

        // Add nodes
        for (XHost tmpH:xhosts){
            logger.info("Carateristique");
            logger.info("Men : " + tmpH.getMemSize());
            logger.info("CPU : " +  tmpH.getCPUCapacity());
            logger.info("Net : " +    tmpH.getNetBW());

            node = new gipad.configuration.configuration.Node(
                    tmpH.getMemSize(),
                    tmpH.getCPUCapacity(),
                    tmpH.getNetBW(),
                    tmpH.getNetBW(),
                    null);

            nodeName.put(node, tmpH.getName());

            for(XVM tmpVM:tmpH.getRunnings()) {
                bw = 1 + r.nextInt(3);

                logger.info("  VM");
                logger.info("  Men : " + tmpVM.getMemSize());
                logger.info("  CPU : " + (int)tmpVM.getCPUDemand());
                logger.info("  Net : " + bw);
                logger.info("  Id : " + node.getId());

                vm = new gipad.configuration.configuration.VirtualMachine(
                        tmpVM.getMemSize(),
                        tmpVM.getMemSize(),
                        0,
                        (int)tmpVM.getCPUDemand(),
                        bw,
                        bw,
                        bw,
                        bw,
                        node.getId(),
                        null);
                vms.add(vm);
                nodesOfVM.add(node);

                vmName.put(vm, tmpVM.getName());
            }
            nodes.add(node);
        }

        return new Object[] {nodes, vms, nodesOfVM};
    }

    @Override
    public ComputingState computeReconfigurationPlan() {
        ComputingState res = ComputingState.SUCCESS;
        SimpleConfiguration src = new SimpleConfiguration();
        for (gipad.configuration.configuration.Node node : nodes)
            src.addOnline(node);
        for (int i = 0; i < vms.size(); i++)
            src.setRunOn(vms.get(i), nodesOfVM.get(i));
        SRP = new SimpleReconfigurationProblem(src);
        SRP.execute();
//        SRP.createSolver();
//        SRP.buildModel();
//        SRP.configureSearch();
//        SRP.solve();
        if (SRP.getSolver().getMeasures().getSolutionCount() != 1)
            res = ComputingState.RECONFIGURATION_FAILED;
        logger.info(SRP.getSolver().getMeasures());
        return res;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int getReconfigurationPlanCost() {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void applyReconfigurationPlan() {
        Migration migration;
        for (gipad.configuration.configuration.VirtualMachine vm : SRP.getVirtualMachines()) {
            logger.debug("++++++" + vm);
            migration = (Migration) SRP.getAssociatedAction(vm);
            logger.debug("******" + vm);
            if (migration.getHost() != migration.getDestination()) {
                logger.info("===================migration");
                logger.debug(migration);
                logger.debug(vm);
                logger.debug(vmName.get(vm));
                logger.debug(migration.getHost());
                logger.debug(nodeName.get(migration.getHost()));
                logger.debug(migration.getDestination());
                logger.debug(nodeName.get(migration.getDestination()));
                CentralizedResolver.relocateVM(vmName.get(vm), nodeName.get(migration.getHost()), nodeName.get(migration.getDestination()));
            }
//            CentralizedResolver.relocateVM()
        }
    }
}
