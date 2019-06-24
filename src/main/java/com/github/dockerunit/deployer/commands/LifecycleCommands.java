package com.github.dockerunit.deployer.commands;

import com.github.dockerunit.core.ServiceContext;
import com.github.dockerunit.core.ServiceInstance;
import com.github.dockerunit.core.discovery.DiscoveryProvider;
import com.github.dockerunit.core.internal.ServiceContextBuilder;
import com.github.dockerunit.core.internal.UsageDescriptor;
import com.github.dockerunit.core.internal.reflect.UsageDescriptorBuilder;
import com.github.dockerunit.core.internal.service.DefaultServiceContext;
import com.github.dockerunit.deployer.DockerUnitSetup;
import com.github.dockerunit.deployer.ServiceContextProvider;
import com.github.dockerunit.deployer.SvcClassLoader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;

import javax.annotation.PostConstruct;
import java.util.HashSet;

@ShellComponent
public class LifecycleCommands {


    @Autowired
    private DiscoveryProvider discoveryProvider;

    @Autowired
    private ServiceContextBuilder contextBuilder;

    @Autowired
    private UsageDescriptorBuilder descriptorBuilder;


    private boolean running = false;


    @ShellMethod(value = "Shuts down the running services and the discovery provider.", key = {"shutdown", "halt"})
    public void shutdown() {
        ServiceContext context = ServiceContextProvider.getSvcContext();
        ServiceContext discoveryContext = ServiceContextProvider.getDiscoveryContext();
        if (context != null) {
            ServiceContext cleared = contextBuilder.clearContext(context);
            discoveryProvider.clearRegistry(cleared, new DefaultServiceContext(new HashSet<>()));
        }

        if (discoveryContext != null) {
            contextBuilder.clearContext(discoveryContext);
        }

        running = false;
    }


    @ShellMethod(value = "Starts the discovery provider and the services.", key = {"start", "run", "wake-up"})
    @PostConstruct
    public void start() {
        if(running) {
            System.out.println("Already running dude.");
            return;
        }
        UsageDescriptor descriptor = descriptorBuilder.buildDescriptor(SvcClassLoader.getSvcClass());
        UsageDescriptor discoveryProviderDescriptor = descriptorBuilder.buildDescriptor(discoveryProvider.getDiscoveryConfig());

        // Build discovery context
        ServiceContext discoveryContext = contextBuilder.buildContext(discoveryProviderDescriptor);
        ServiceContextProvider.setDiscoveryContext(discoveryContext);
        if (!discoveryContext.checkStatus(ServiceInstance.Status.STARTED)) {
            throw new RuntimeException(discoveryContext.getFormattedErrors());
        }

        ServiceContext svcContext = new DockerUnitSetup(contextBuilder, discoveryProvider).setup(descriptor);
        ServiceContextProvider.setSvcContext(svcContext);
        if (!svcContext.checkStatus(ServiceInstance.Status.DISCOVERED)) {
            throw new RuntimeException(svcContext.getFormattedErrors());
        }

        running = true;
    }

    @ShellMethod(value = "Restarts all services and the discovery provider.", key = {"restart", "reboot"})
    public void restart() {
        shutdown();
        start();
    }

}
