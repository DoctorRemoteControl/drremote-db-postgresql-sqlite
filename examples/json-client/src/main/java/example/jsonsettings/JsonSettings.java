package example.jsonsettings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.drremote.db.json.api.JsonQueries;
import de.drremote.db.sqlite.api.DatabaseReady;
import de.drremote.db.sqlite.api.TransactionManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.Objects;

@Component(service = JsonSettings.class)
public final class JsonSettings {
    @Reference private TransactionManager transactions;
    @Reference(target = "(drremote.db.plugin=json-settings)") private DatabaseReady ready;

    public void save(String id, JsonNode document) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(document, "document");
        transactions.inTransaction(db -> {
            JsonQueries.on(db.getConnection()).update(
                "INSERT INTO json_settings(id, payload) VALUES (?, ?) "
                    + "ON CONFLICT(id) DO UPDATE SET payload = excluded.payload", id, document);
            return null;
        });
    }

    public ObjectNode load(String id) {
        return transactions.inTransaction(db -> JsonQueries.on(db.getConnection())
            .withJsonColumns("payload")
            .queryOne("SELECT id, payload FROM json_settings WHERE id = ?", id));
    }

    public ArrayNode list() {
        return transactions.inTransaction(db -> JsonQueries.on(db.getConnection())
            .withJsonColumns("payload")
            .query("SELECT id, payload FROM json_settings ORDER BY id"));
    }
}
