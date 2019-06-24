package com.github.dockerunit.deployer.spring;

import com.github.dockerunit.core.discovery.DiscoveryProvider;
import com.github.dockerunit.core.discovery.DiscoveryProviderFactory;
import com.github.dockerunit.core.internal.ServiceContextBuilder;
import com.github.dockerunit.core.internal.ServiceContextBuilderFactory;
import com.github.dockerunit.core.internal.reflect.DependencyDescriptorBuilderFactory;
import com.github.dockerunit.core.internal.reflect.UsageDescriptorBuilder;
import com.github.dockerunit.deployer.SvcClassLoader;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.shell.jline.PromptProvider;

import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@SpringBootApplication
@ComponentScan("com.github.dockerunit.deployer.commands")
public class ShellLauncher {


    private static final Logger logger = Logger.getLogger(ShellLauncher.class.getSimpleName());

    private static final String CLASSPATH_OPTION = "--classpath";


    private static DiscoveryProvider discoveryProvider;

    private static final UsageDescriptorBuilder descriptorBuilder = DependencyDescriptorBuilderFactory.create();
    private static final ServiceContextBuilder contextBuilder = ServiceContextBuilderFactory.create();


    public static void run(String[] args) throws Exception {
        initDiscovery();
        Class<?> svcClass = SvcClassLoader.loadSvcClass(args[0], Arrays.asList(Arrays.copyOfRange(args, 1, args.length))
                .stream()
                .filter(arg -> !arg.equals(CLASSPATH_OPTION))
                .collect(Collectors.toList()));

        SpringApplication.run(ShellLauncher.class, args);
    }

    @Bean
    public PromptProvider myPromptProvider() {
        return () -> new AttributedString("dude-shell:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
    }

    @Bean
    public DiscoveryProvider getDiscoveryProvider() {
        return discoveryProvider;
    }

    @Bean
    public ServiceContextBuilder getContextBuilder() {
        return contextBuilder;
    }

    @Bean
    public UsageDescriptorBuilder getDescriptorBuilder() {
        return descriptorBuilder;
    }

    private static void initDiscovery() {
        ServiceLoader<DiscoveryProviderFactory> loader = ServiceLoader.load(DiscoveryProviderFactory.class);

        discoveryProvider = StreamSupport.stream(loader.spliterator(), false)
                .peek(impl -> logger.info(
                        "Found discovery provider factory of type " + impl.getClass().getSimpleName()))
                .findFirst()
                .map(impl -> {
                    logger.info("Using discovery provider factory " + impl.getClass().getSimpleName());
                    return impl;
                })
                .map(DiscoveryProviderFactory::getProvider)
                .orElseThrow(() -> new RuntimeException("No discovery provider factory found. Aborting."));

    }

}
