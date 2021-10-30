package net.woggioni.osgi.simple.bootstrapper.application;

import lombok.SneakyThrows;
import lombok.extern.java.Log;
import net.woggioni.osgi.simple.bootstrapper.api.Application;
import net.woggioni.osgi.simple.bootstrapper.api.FrameworkService;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ServiceScope;

import java.util.Objects;
import java.util.logging.Level;

@Log
@Component(scope = ServiceScope.SINGLETON)
public class ApplicationRunner {

    private final Application application;

    @Activate
    @SneakyThrows
    public ApplicationRunner(@Reference ServiceReference<Application> ref, BundleContext bundleContext, ComponentContext componentContext) {
        application = bundleContext.getService(ref);
        String componentName = (String) ref.getProperty("component.name");
        ServiceReference<FrameworkService> frameworkServiceReference = bundleContext.getServiceReference(FrameworkService.class);
        FrameworkService frameworkService = bundleContext.getService(frameworkServiceReference);
        String mainApplicationComponentName = frameworkService.getMainApplicationComponentName();
        if(mainApplicationComponentName == null || Objects.equals(mainApplicationComponentName, componentName)) {
            Application application = bundleContext.getService(ref);
            try {
                frameworkService.setExitCode(application.run(frameworkService.getArgs()));
            } catch(Exception ex) {
                log.log(Level.SEVERE, ex, ex::getMessage);
                frameworkService.setExitCode(1);
            } finally {
                bundleContext.getBundle(0).stop();
                bundleContext.ungetService(ref);
            }
        }
    }
}

