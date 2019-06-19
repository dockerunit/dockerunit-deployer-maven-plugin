package com.github.dockerunit.deployer;

import com.github.dockerunit.core.ServiceContext;

public class ServiceContextProvider {

    private static ServiceContext svcContext;

    private static ServiceContext discoveryContext;

    public static synchronized ServiceContext getSvcContext() {
        return svcContext;
    }

    public static synchronized void setSvcContext(ServiceContext svcContext) {
        ServiceContextProvider.svcContext = svcContext;
    }

    public static synchronized ServiceContext getDiscoveryContext() {
        return discoveryContext;
    }

    public static synchronized void setDiscoveryContext(ServiceContext discoveryContext) {
        ServiceContextProvider.discoveryContext = discoveryContext;
    }
}
