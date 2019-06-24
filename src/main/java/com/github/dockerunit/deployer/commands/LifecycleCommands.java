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
        ServiceContext discoveryContext = ServiceContextProvider.getDiscoveryContext();
        if (context != null) {
            ServiceContext cleared = contextBuilder.clearContext(context);
            ServiceContext clearedRegistryContext = discoveryProvider.clearRegistry(cleared, new DefaultServiceContext(new HashSet<>()));
            ServiceContextProvider.setSvcContext(clearedRegistryContext);
        }

        if (discoveryContext != null) {
            ServiceContext clearedDiscoveryContext = contextBuilder.clearContext(discoveryContext);
            ServiceContextProvider.setDiscoveryContext(clearedDiscoveryContext);
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


    @ShellMethod(value = "Exits the DUDe shell.", key = {"exit", "quit"})
    public void quit(@ShellOption(value = {"-f", "--force"}) boolean force) {
        if(force || askYesNo("Shutdown running containers?")) {
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
