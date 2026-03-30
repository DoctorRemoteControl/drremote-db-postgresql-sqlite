package de.drremote.db.sqlite.impl;

import de.drremote.db.sqlite.api.DbCore;
import org.osgi.service.component.annotations.*;

import java.util.logging.Logger;

@Component(
    service = DbCore.class,
    immediate = true
)
public final class DbCoreComponent implements DbCore {

    private static final Logger LOG = Logger.getLogger(DbCoreComponent.class.getName());

    private volatile boolean ready = false;

    @Activate
    void activate() {
        ready = true;
        LOG.info("Core activated");
    }

    @Deactivate
    void deactivate() {
        ready = false;
        LOG.info("Core deactivated");
    }

    @Override
    public boolean isReady() {
        return ready;
    }
}