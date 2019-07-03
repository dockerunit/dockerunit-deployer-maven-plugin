package com.github.dockerunit.deployer.commands;

import com.github.dockerunit.core.Service;
import com.github.dockerunit.core.ServiceContext;
import com.github.dockerunit.core.ServiceInstance;
import com.github.dockerunit.core.discovery.DiscoveryProvider;
import com.github.dockerunit.core.internal.ServiceContextBuilder;
import com.github.dockerunit.core.internal.ServiceDescriptor;
import com.github.dockerunit.core.internal.UsageDescriptor;
import com.github.dockerunit.core.internal.reflect.DefaultServiceDescriptor;
import com.github.dockerunit.core.internal.reflect.UsageDescriptorBuilder;
import com.github.dockerunit.core.internal.service.DefaultServiceContext;
import com.github.dockerunit.deployer.DockerUnitSetup;
import com.github.dockerunit.deployer.ServiceContextProvider;
import com.github.dockerunit.deployer.SvcClassLoader;
import org.hibernate.validator.constraints.NotEmpty;
import org.jline.reader.LineReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.shell.ExitRequest;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.standard.commands.Quit;

import javax.annotation.PostConstruct;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

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
        startSvc(s.getDescriptor());

    }

    @ShellMethod(value = "Exits the DUDe shell.", key = {"exit", "quit"})
    public void quit(@ShellOption(value = {"-f", "--force"}) boolean force) {
        if (force || askYesNo("Shutdown running containers?")) {
            shutdown();
        }
        throw new ExitRequest();
    }

    @ShellMethod(value = "Scales the specified service up/down to the desired number of replicas", key = {"scale"})
    public void scale(@ShellOption("--replicas") @Min(value = 1, message = "You can scale services down to 1 instance")
                      @Max(value = 10, message = "You can scale services up to 10 instances") int replicas,
                      @NotNull @NotEmpty String svc) {
        ServiceContext svcContext = ServiceContextProvider.getSvcContext();
        Service s = svcContext.getService(svc);
        if (s == null) {
            System.out.println(String.format("Could not find service %s dude.", svc));
            return;
        }

        if(s.getInstances().size() > replicas) {
            scaleDown(s, replicas);
        } else if(s.getInstances().size() < replicas) {
            scaleUp(s, replicas);
        } else {
            System.out.println(String.format("Nothing to be done dude. %s has already %d running instances.", svc, replicas));
        }


    }

    private void stopDiscovery() {
        ServiceContext discoveryContext = ServiceContextProvider.getDiscoveryContext();
        if (discoveryContext != null) {
            ServiceContext clearedDiscoveryContext = contextBuilder.clearContext(discoveryContext);
            ServiceContextProvider.setDiscoveryContext(clearedDiscoveryContext);
        }
    }

    private void startSvc(ServiceDescriptor sd) {
        ServiceContext context = contextBuilder.buildServiceContext(sd);
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



    private void scaleUp(Service s, int replicas) {
        boolean randomise = s.getDescriptor().getContainerName() != null
                && !s.getDescriptor().getContainerName().isEmpty();

        ServiceDescriptor newDescriptor = newDescriptor(s.getDescriptor(),
                replicas - s.getInstances().size(),
                randomise);
        System.out.print(String.format("Scaling %s up to %d instances. Hold on a sec ... ", s.getName(), replicas));
        startSvc(newDescriptor);
        System.out.println("DONE");
    }

    private void scaleDown(Service s, int replicas) {
        List<ServiceInstance> asList = s.getInstances().stream().collect(Collectors.toList());
        Set<ServiceInstance> killableInstances = IntStream
                .range(0, s.getInstances().size() - replicas)
                .mapToObj(i -> asList.get(i))
                .collect(Collectors.toSet());


        Service toBeCleaned = new Service(s.getName(), killableInstances,
                newDescriptor(s.getDescriptor(), killableInstances.size(), false));

        contextBuilder.clearContext(
                new DefaultServiceContext(Stream.of(toBeCleaned).collect(Collectors.toSet())));
        ServiceContextProvider.setSvcContext(cleanContext(toBeCleaned));
    }

    private ServiceContext cleanContext(Service toBeCleaned) {
        Set<Service> currentServices = ServiceContextProvider.getSvcContext().getServices();

        Set<Service> newServices = currentServices.stream()
                .map(service -> {
                    if(service.getName().equals(toBeCleaned.getName())) {
                        return new Service(service.getName(),
                                service.getInstances()
                                    .stream()
                                    .filter(si -> !toBeCleaned.getInstances().contains(si))
                                    .collect(Collectors.toSet()),
                                toBeCleaned.getDescriptor());
                    }
                    return service;
                })
                .collect(Collectors.toSet());
        return new DefaultServiceContext(newServices);
    }

    private ServiceDescriptor newDescriptor(ServiceDescriptor sd, int instances, boolean randomiseContainerName) {
        return DefaultServiceDescriptor.builder()
                .containerName(randomiseContainerName? randomise(sd.getContainerName()) : sd.getContainerName())
                .customisationHook(sd.getCustomisationHook())
                .instance(sd.getInstance())
                .options(sd.getOptions())
                .priority(sd.getPriority())
                .svcDefinition(sd.getSvcDefinition())
                .replicas(instances)
                .build();
    }

    private String randomise(String containerName) {
        if(containerName == null) {
            containerName = "";
        }
        byte[] bytes = new byte[4];
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(bytes);
        return containerName.concat("-")
                .concat(Base64.getEncoder().encodeToString(bytes)
                .replaceAll("=", "0")
                .replaceAll("\\+", "1")
                .replaceAll("/", "2"));
    }

}
