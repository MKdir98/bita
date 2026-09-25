package ir.bita.esm.route;

import org.hibernate.boot.model.TypeContributions;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.SqlTypes;
import org.hibernate.type.descriptor.sql.internal.DdlTypeImpl;

/**
 * H2's PostgreSQL-compatibility mode (needed so entities' {@code columnDefinition = "jsonb"}
 * resolves, matching the real Postgres schema) rejects the plain {@code TINYINT} type
 * Hibernate/Envers emits for {@code byte}-sized columns (e.g. {@code users_aud.revtype}) with
 * "Unknown data type: TINYINT" — Postgres itself has no TINYINT. This dialect keeps H2's
 * PostgreSQL mode (and therefore JSONB) and only remaps that one DDL type to SMALLINT, which
 * both H2 and Postgres accept.
 */
public class TestFriendlyH2Dialect extends H2Dialect {

    @Override
    public void contributeTypes(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        super.contributeTypes(typeContributions, serviceRegistry);
        typeContributions.getTypeConfiguration().getDdlTypeRegistry()
                .addDescriptor(new DdlTypeImpl(SqlTypes.TINYINT, "smallint", this));
    }
}
