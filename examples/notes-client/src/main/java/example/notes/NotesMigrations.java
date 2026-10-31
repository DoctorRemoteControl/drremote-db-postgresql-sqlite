package example.notes;

import de.drremote.db.sqlite.api.Migration;
import de.drremote.db.sqlite.api.PluginMigrationDescriptor;
import org.osgi.service.component.annotations.Component;

import java.util.List;

@Component(service = PluginMigrationDescriptor.class)
public final class NotesMigrations implements PluginMigrationDescriptor {
    @Override public String getPluginName() { return "notes"; }

    @Override public List<Migration> getMigrations() {
        return List.of(new Migration() {
            @Override public int getVersion() { return 1; }
            @Override public String getDescription() { return "Create notes"; }
            @Override public String getSql() {
                return "CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT NOT NULL)";
            }
        });
    }
}
