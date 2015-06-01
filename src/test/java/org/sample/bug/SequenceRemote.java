package org.sample.bug;

import java.rmi.Remote;

public interface SequenceRemote extends Remote {
    long incrementAndGet();
}
