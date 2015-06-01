package org.sample.bug;

import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteAtomicSequence;
import org.apache.ignite.Ignition;
import org.gridkit.nanocloud.Cloud;
import org.gridkit.nanocloud.CloudFactory;
import org.gridkit.nanocloud.VX;
import org.gridkit.vicluster.ViNode;
import org.gridkit.vicluster.ViProps;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertTrue;

public class SequenceTest {
    @Test
    public void testDistributedSequence() throws Exception {

        final Cloud cluster = CloudFactory.createCloud();
        ViProps.at(cluster.node("**")).setLocalType();
        cluster.node("**").setProp("IGNITE_QUIET", "false");

        // start data node in separate JVM
        final ViNode dataNode = cluster.node("data-node");
        dataNode.exec(
                new Runnable() {
                    public void run() {
                        Ignition.start(SequenceTest.class.getClassLoader().getResource("gridgain-config-context.xml"));
                    }
                });

        // start first service node in separate JVM
        final ViNode serviceNode1 = cluster.node("service-node-1");
        //uncomment next line for debug
//        serviceNode1.x(VX.PROCESS).addJvmArg("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5006");
        final SequenceRemote sequenceFromNode1 = serviceNode1.exec(new Callable<SequenceRemote>() {
            public SequenceRemote call() throws Exception {
                Ignition.setClientMode(true);

                final Ignite ignite = Ignition.start(SequenceTest.class.getClassLoader().getResource("gridgain-config-context.xml"));
                final IgniteAtomicSequence sequence = ignite.atomicSequence("sequence", 0, true);

                return new SequenceRemote() {
                    public long incrementAndGet() {
                        final long sequenceValue = sequence.incrementAndGet();
                        System.err.println(" At service node 1 we got value=" + sequenceValue + " current time=" + new Date());
                        System.err.flush();
                        return sequenceValue;
                    }
                };
            }
        });

        // start second service node in separate JVM
        final ViNode serviceNode2 = cluster.node("service-node-2");
        //uncomment next line for debug
//        serviceNode2.x(VX.PROCESS).addJvmArg("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005");
        final SequenceRemote sequenceFromNode2 = serviceNode2.exec(new Callable<SequenceRemote>() {
            public SequenceRemote call() throws Exception {
                Ignition.setClientMode(true);

                final Ignite ignite = Ignition.start(SequenceTest.class.getClassLoader().getResource("gridgain-config-context.xml"));
                final IgniteAtomicSequence sequence = ignite.atomicSequence("sequence", 0, true);

                return new SequenceRemote() {
                    public long incrementAndGet() {
                        final long sequenceValue = sequence.incrementAndGet();
                        System.err.println(" At service node 2 we got value=" + sequenceValue + " current time=" + new Date());
                        System.err.flush();
                        return sequenceValue;
                    }
                };
            }
        });

        List<Long> sequenceValues = new ArrayList<Long>();

        for (int i = 0; i < 3; i++) {
            Thread.sleep(1000);
            sequenceValues.add(sequenceFromNode1.incrementAndGet());
            Thread.sleep(1000);
            sequenceValues.add(sequenceFromNode2.incrementAndGet());
        }

        for (int i = 1; i < sequenceValues.size(); i++) {
            assertTrue("sequence values should grow, but values is " + sequenceValues,
                    sequenceValues.get(i - 1) < sequenceValues.get(i));
        }
    }
}
