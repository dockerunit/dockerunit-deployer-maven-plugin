package com.github.dockerunit.deployer.commands;

import com.github.dockerunit.core.Service;
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
import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.ExitRequest;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.standard.commands.Quit;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@ShellComponent
public class LifecycleCommands implements Quit.Command {


    @Autowired
    private DiscoveryProvider discoveryProvider;

    @Autowired
    private ServiceContextBuilder contextBuilder;

    @Autowired
    private UsageDescriptorBuilder descriptorBuilder;

    @Autowired
    @Lazy
    private LineReader lineReader;

    private boolean running = false;


    @ShellMethod(value = "Shuts down the running services and the discovery provider.", key = {"shutdown", "halt", "stop"})
    public void shutdown() {
        ServiceContext context = ServiceContextProvider.getSvcContext();
        if (context != null) {
            ServiceContext cleared = contextBuilder.clearContext(context);
            ServiceContext clearedRegistryContext = discoveryProvider.clearRegistry(cleared, new DefaultServiceContext(new HashSet<>()));
            ServiceContextProvider.setSvcContext(clearedRegistryContext);
        }

        stopDiscovery();

        running = false;
    }

    private void stopDiscovery() {
        ServiceContext discoveryContext = ServiceContextProvider.getDiscoveryContext();
        if (discoveryContext != null) {
            ServiceContext clearedDiscoveryContext = contextBuilder.clearContext(discoveryContext);
            ServiceContextProvider.setDiscoveryContext(clearedDiscoveryContext);
        }
    }


    @ShellMethod(value = "Starts the discovery provider and the services.", key = {"start", "run", "wake-up"})
    @PostConstruct
    public void start() {
        if (running) {
            System.out.println("Already running dude.");
            return;
        }

        startDiscovery();

        UsageDescriptor descriptor = descriptorBuilder.buildDescriptor(SvcClassLoader.getSvcClass());
        ServiceContext svcContext = new DockerUnitSetup(contextBuilder, discoveryProvider).setup(descriptor);
        ServiceContextProvider.setSvcContext(svcContext);
        if (!svcContext.checkStatus(ServiceInstance.Status.DISCOVERED)) {
            throw new RuntimeException(svcContext.getFormattedErrors());
        }

        running = true;
    }

    private void startDiscovery() {
        UsageDescriptor discoveryProviderDescriptor = descriptorBuilder.buildDescriptor(discoveryProvider.getDiscoveryConfig());

        ServiceContext discoveryContext = contextBuilder.buildContext(discoveryProviderDescriptor);
        ServiceContextProvider.setDiscoveryContext(discoveryContext);
        if (!discoveryContext.checkStatus(ServiceInstance.Status.STARTED)) {
            throw new RuntimeException(discoveryContext.getFormattedErrors());
        }
    }

    @ShellMethod(value = "Restarts all services and the discovery provider.", key = {"restart", "reboot"})
    public void restart(@ShellOption(value = "--svc", defaultValue = ShellOption.NULL) String svc) {
        if (svc == null) {
            System.out.println("Shutting down all services...");
            shutdown();
            System.out.println("Restarting all services...");
            start();
            return;
        }

        ServiceContext svcContext = ServiceContextProvider.getSvcContext();
        Service s = svcContext.getService(svc);
        if (s == null) {
            System.out.println(String.format("Could not find service %s dude.", svc));
            return;
        }

        shutSvcDown(s);
        stopDiscovery();
        startDiscovery();
        startSvc(s);

    }

    private void startSvc(Service s) {
        ServiceContext context = contextBuilder.buildServiceContext(s.getDescriptor());
        ServiceContext postDiscoveryContext = discoveryProvider.populateRegistry(
                ServiceContextProvider.getSvcContext().merge(context));
        ServiceContextProvider.setSvcContext(postDiscoveryContext);
    }

    private void shutSvcDown(Service svc) {
        Set<Service> toBeRemoved = new HashSet<>();
        toBeRemoved.add(svc);
        ServiceContext context = contextBuilder.clearContext(new DefaultServiceContext(toBeRemoved));
        ServiceContextProvider.setSvcContext(new DefaultServiceContext(ServiceContextProvider
                .getSvcContext()
                .getServices()
                .stream()
                .filter(s -> !s.getName().equals(svc.getName()))
                .collect(Collectors.toSet())));
    }


    @ShellMethod(value = "Exits the DUDe shell.", key = {"exit", "quit"})
    public void quit(@ShellOption(value = {"-f", "--force"}) boolean force) {
        if (force || askYesNo("Shutdown running containers?")) {
            shutdown();
        }
        throw new ExitRequest();
    }


    private boolean askYesNo(String question) {
        while (true) {
            String backup = ask(String.format("%s (y/n): ", question));
            if ("y".equals(backup)) {
                return true;
            }
            if ("n".equals(backup)) {
                return false;
            }
        }
    }

    private String ask(String question) {
        question = "\n" + question + "> ";
        return lineReader.readLine(question);
    }

}
