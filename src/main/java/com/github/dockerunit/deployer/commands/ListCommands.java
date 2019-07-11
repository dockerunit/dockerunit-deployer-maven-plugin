package com.github.dockerunit.deployer.commands;

import com.github.dockerunit.core.Service;
import com.github.dockerunit.core.ServiceContext;
import com.github.dockerunit.core.ServiceInstance;
import com.github.dockerunit.deployer.ServiceContextProvider;
import com.github.dockerunit.deployer.util.TableFactory;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;
import org.springframework.shell.table.Table;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@ShellComponent
public class ListCommands {



    @ShellMethod(value = "Lists the currently running services", key = {"get-svc", "list-svc"})
    public Table listServices(){
        ServiceContext svcContext = ServiceContextProvider.getSvcContext();
        List<Service> services = svcContext.getServices().stream().collect(Collectors.toList());
        String[][] data = new String[services.size()][2];

        for (int i = 0; i < services.size(); i++) {
                data[i][0] = String.format(" %s ", services.get(i).getName());
                data[i][1] = String.format(" %d ", services.get(i).getInstances().size());
        }

        return TableFactory.createTable(new String[] {"name", "replicas"}, data);
    }

    @ShellMethod(value = "Lists the currently running service instances", key = {"get-instances", "list-instances"})
    public Table listInstances(@ShellOption(value = "--svc", defaultValue = ShellOption.NULL) String svcName){
        ServiceContext svcContext = ServiceContextProvider.getSvcContext();
        List<Service> services = svcContext.getServices()
                .stream()
                .filter(s -> svcName == null ? true : svcName.equals(s.getName()))
                .collect(Collectors.toList());
        services.sort(Comparator.comparing(Service::getName));

        int allInstances = services.stream()
                .map(svc -> svc.getInstances())
                .flatMap(Collection::stream)
                .collect(Collectors.toList())
                .size();

        String[] tableHeader = {"svc", "container-name", "container-id", "gateway", "gateway-port", "container-ip", "container-port", "status"};

        String[][] data = new String[allInstances][tableHeader.length];

        int row = 0;
        for (int i = 0; i < services.size(); i++) {
            Service s = services.get(i);
            List<ServiceInstance> instances = s.getInstances().stream().collect(Collectors.toList());
            for(int j = 0; j < instances.size(); j++) {
                ServiceInstance si = instances.get(j);
                data[row] = new String[] {
                        String.format(" %s ", s.getName()),
                        String.format(" %s ", si.getContainerName().substring(1)),
                        String.format(" %s ", si.getContainerId().substring(0, 12)),
                        String.format(" %s ", si.getGatewayAddress()),
                        String.format(si.getGatewayPort() == 0 ? "%s" :" %d ", si.getGatewayPort() == 0 ? "N/A" : si.getGatewayPort()),
                        String.format(" %s ", si.getContainerIp()),
                        String.format(si.getContainerPort() == 0 ? "%s" :" %d ", si.getContainerPort() == 0 ? "N/A" : si.getContainerPort()),
                        String.format(" %s ", si.getStatus().toString())
                };
                row++;
            }
        }

        return TableFactory.createTable(tableHeader, data);
    }

}
