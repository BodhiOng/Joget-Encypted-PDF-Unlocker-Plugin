package org.joget.sample;

import java.util.ArrayList;
import java.util.Collection;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

    protected Collection<ServiceRegistration<?>> registrationList;

    @Override
    public void start(BundleContext context) {
        registrationList = new ArrayList<>();

        // Register PDF Password Unlocker plugin
        registrationList.add(context.registerService(PdfPasswordUnlocker.class.getName(), new PdfPasswordUnlocker(), null));
    }

    @Override
    public void stop(BundleContext context) {
        for (ServiceRegistration<?> registration : registrationList) {
            registration.unregister();
        }
    }
}