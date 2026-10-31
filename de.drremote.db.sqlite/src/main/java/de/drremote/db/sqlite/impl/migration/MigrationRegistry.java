package de.drremote.db.sqlite.impl.migration;

import de.drremote.db.sqlite.api.DatabaseReady;
import de.drremote.db.sqlite.api.MigrationService;
import de.drremote.db.sqlite.api.PluginMigrationDescriptor;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicy;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

@Component(immediate = true)
public final class MigrationRegistry {

    private final List<PluginMigrationDescriptor> pending = new ArrayList<>();
    private final Map<PluginMigrationDescriptor, ServiceRegistration<DatabaseReady>> registrations =
        new IdentityHashMap<>();

    private volatile MigrationService migrationService;
    private BundleContext context;

    @Reference
    void bindMigrationService(MigrationService migrationService) {
        this.migrationService = migrationService;
    }

    @Reference(
        service = PluginMigrationDescriptor.class,
        cardinality = ReferenceCardinality.MULTIPLE,
        policy = ReferencePolicy.DYNAMIC
    )
    synchronized void addMigrationDescriptor(PluginMigrationDescriptor descriptor) {
        if (context == null) {
            pending.add(descriptor);
            return;
        }
        migrateAndPublish(descriptor);
    }

    synchronized void removeMigrationDescriptor(PluginMigrationDescriptor descriptor) {
        pending.removeIf(candidate -> candidate == descriptor);
        ServiceRegistration<DatabaseReady> registration = registrations.remove(descriptor);
        if (registration != null) {
            registration.unregister();
        }
    }

    @Activate
    synchronized void activate(BundleContext context) {
        this.context = context;
        List<PluginMigrationDescriptor> descriptors = List.copyOf(pending);
        pending.clear();
        descriptors.forEach(this::migrateAndPublish);
    }

    @Deactivate
    synchronized void deactivate() {
        registrations.values().forEach(ServiceRegistration::unregister);
        registrations.clear();
        context = null;
    }

    private void migrateAndPublish(PluginMigrationDescriptor descriptor) {
        if (registrations.containsKey(descriptor)) {
            return;
        }

        String pluginName = requireText(descriptor.getPluginName(), "plugin name");
        migrationService.migratePlugin(descriptor);

        DatabaseReady ready = new ReadyService(pluginName);
        Dictionary<String, Object> properties = new Hashtable<>();
        properties.put(DatabaseReady.PROPERTY_KIND, ready.getDatabaseKind());
        properties.put(DatabaseReady.PROPERTY_PLUGIN, ready.getPluginName());
        properties.put(DatabaseReady.PROPERTY_SCHEMA, ready.getSchemaName());
        registrations.put(
            descriptor,
            context.registerService(DatabaseReady.class, ready, properties)
        );
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Migration descriptor " + label + " must not be blank");
        }
        return value.trim();
    }

    private record ReadyService(String pluginName) implements DatabaseReady {
        @Override
        public String getDatabaseKind() {
            return "sqlite";
        }

        @Override
        public String getPluginName() {
            return pluginName;
        }

        @Override
        public String getSchemaName() {
            return "main";
        }
    }
}
