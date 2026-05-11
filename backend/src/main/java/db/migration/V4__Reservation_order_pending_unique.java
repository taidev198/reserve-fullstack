package db.migration;

import java.sql.Statement;
import java.util.Locale;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

/**
 * Partial unique index is PostgreSQL-specific. H2 (used in tests) does not support
 * {@code CREATE UNIQUE INDEX ... WHERE ...}; skip there and rely on application-level guards.
 */
public class V4__Reservation_order_pending_unique extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        String product = context.getConnection().getMetaData().getDatabaseProductName();
        if (product == null || !product.toLowerCase(Locale.ROOT).contains("postgres")) {
            return;
        }
        try (Statement stmt = context.getConnection().createStatement()) {
            stmt.execute(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS uq_reservations_order_id_pending
                        ON reservations(order_id)
                        WHERE status = 'PENDING'
                    """);
        }
    }
}
