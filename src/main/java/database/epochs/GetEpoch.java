package database.epochs;

import database.GetConnection;
import database.generated.Tables;
import database.generated.tables.Epochs;
import nyu.Term;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;

public final class GetEpoch {

  private static Logger logger =
      LoggerFactory.getLogger("database.epochs.GetEpoch");

  public static int getEpoch(Connection conn, Term term) {
    DSLContext context = DSL.using(conn, GetConnection.DIALECT);
    Epochs EPOCHS = Tables.EPOCHS;
    return context
        .insertInto(EPOCHS, EPOCHS.STARTED_AT, EPOCHS.COMPLETED_AT,
                    EPOCHS.TERM_ID)
        .values(Timestamp.from(Instant.now()), null, term.getId())
        .returning(EPOCHS.ID)
        .fetchOne()
        .getValue(EPOCHS.ID);
  }
}
