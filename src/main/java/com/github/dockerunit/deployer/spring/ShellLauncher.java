package com.github.dockerunit.deployer.spring;

import com.github.dockerunit.core.ServiceContext;
import com.github.dockerunit.core.ServiceInstance;
import com.github.dockerunit.core.discovery.DiscoveryProvider;
import com.github.dockerunit.core.discovery.DiscoveryProviderFactory;
import com.github.dockerunit.core.internal.ServiceContextBuilder;
import com.github.dockerunit.core.internal.ServiceContextBuilderFactory;
import com.github.dockerunit.core.internal.UsageDescriptor;
import com.github.dockerunit.core.internal.reflect.DependencyDescriptorBuilderFactory;
import com.github.dockerunit.core.internal.reflect.UsageDescriptorBuilder;
import com.github.dockerunit.deployer.DockerUnitSetup;
import com.github.dockerunit.deployer.PluginRunner;
import org.apache.maven.plugin.MojoExecutionException;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStyle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.shell.jline.PromptProvider;

import java.util.ServiceLoader;
import java.util.logging.Logger;
import java.util.stream.StreamSupport;

@SpringBootApplication
@ComponentScan("com.github.dockerunit.deployer.commands")
public class ShellLauncher {


    private static final Logger logger = Logger.getLogger(ShellLauncher.class.getSimpleName());

    private static DiscoveryProvider discoveryProvider;

    private static final UsageDescriptorBuilder descriptorBuilder = DependencyDescriptorBuilderFactory.create();
    private static final ServiceContextBuilder contextBuilder = ServiceContextBuilderFactory.create();
    private static ServiceContext discoveryContext;
    private static ServiceContext svcContext;


    public static void run(String[] args) throws Exception {
        initDiscovery();
        doSetup(PluginRunner.getSvcClass());
        ConfigurableApplicationContext context = SpringApplication.run(ShellLauncher.class, args);
    }

    @Bean
    public PromptProvider myPromptProvider() {
        return () -> new AttributedString("dude-shell:>", AttributedStyle.DEFAULT.foreground(AttributedStyle.YELLOW));
    }

    @Bean
    @Qualifier("discovery")
    public ServiceContext provideDiscoveryContext() {
        return discoveryContext;
    }

    @Bean
    @Qualifier("services")
    public ServiceContext provideSvcContext() {
        return svcContext;
    }

    private static void initDiscovery() throws MojoExecutionException {
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
                .orElseThrow(() ->  new MojoExecutionException("No discovery provider factory found. Aborting."));

    }

    private static void doSetup(final Class<?> clazz) {
        UsageDescriptor descriptor = descriptorBuilder.buildDescriptor(clazz);
        UsageDescriptor discoveryProviderDescriptor = descriptorBuilder.buildDescriptor(discoveryProvider.getDiscoveryConfig());

        // Build discovery context
        discoveryContext = contextBuilder.buildContext(discoveryProviderDescriptor);
        if (!discoveryContext.checkStatus(ServiceInstance.Status.STARTED)) {
            throw new RuntimeException(discoveryContext.getFormattedErrors());
        }

        svcContext = new DockerUnitSetup(contextBuilder, discoveryProvider).setup(descriptor);

        if (!svcContext.checkStatus(ServiceInstance.Status.DISCOVERED)) {
            throw new RuntimeException(svcContext.getFormattedErrors());
        }

    }

}
