package example.jsonsettings;

import de.drremote.db.sqlite.api.Migration;
import de.drremote.db.sqlite.api.PluginMigrationDescriptor;
import org.osgi.service.component.annotations.Component;

import java.util.List;

@Component(service = PluginMigrationDescriptor.class)
public final class SettingsMigrations implements PluginMigrationDescriptor {
    @Override public String getPluginName() { return "json-settings"; }

    @Override public List<Migration> getMigrations() {
        return List.of(new Migration() {
            public int getVersion() { return 1; }
            public String getDescription() { return "Create JSON settings"; }
            public String getSql() {
                return "CREATE TABLE json_settings (id TEXT PRIMARY KEY, payload TEXT NOT NULL CHECK(json_valid(payload)))";
            }
        });
    }
}
