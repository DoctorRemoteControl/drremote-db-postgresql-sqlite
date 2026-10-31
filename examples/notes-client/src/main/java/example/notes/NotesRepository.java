package example.notes;

import de.drremote.db.sqlite.api.DatabaseReady;
import de.drremote.db.sqlite.api.TransactionManager;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import java.util.List;

@Component(service = NotesRepository.class)
public final class NotesRepository {
    @Reference private TransactionManager transactions;
    @Reference(target = "(drremote.db.plugin=notes)") private DatabaseReady ready;

    public void add(String text) {
        transactions.inTransaction(db -> {
            db.getQueryRunner().update("INSERT INTO notes(text) VALUES (?)", text);
            return null;
        });
    }

    public List<String> list() {
        return transactions.inTransaction(db -> db.getQueryRunner().query(
            "SELECT text FROM notes ORDER BY id", row -> row.getString("text")));
    }
}
