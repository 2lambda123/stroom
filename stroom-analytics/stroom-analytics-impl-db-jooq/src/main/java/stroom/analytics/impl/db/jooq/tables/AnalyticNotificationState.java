/*
 * This file is generated by jOOQ.
 */
package stroom.analytics.impl.db.jooq.tables;


import java.util.Arrays;
import java.util.List;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row3;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.TableOptions;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import stroom.analytics.impl.db.jooq.Keys;
import stroom.analytics.impl.db.jooq.Stroom;
import stroom.analytics.impl.db.jooq.tables.records.AnalyticNotificationStateRecord;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class AnalyticNotificationState extends TableImpl<AnalyticNotificationStateRecord> {

    private static final long serialVersionUID = 1L;

    /**
     * The reference instance of <code>stroom.analytic_notification_state</code>
     */
    public static final AnalyticNotificationState ANALYTIC_NOTIFICATION_STATE = new AnalyticNotificationState();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<AnalyticNotificationStateRecord> getRecordType() {
        return AnalyticNotificationStateRecord.class;
    }

    /**
     * The column
     * <code>stroom.analytic_notification_state.fk_analytic_notification_uuid</code>.
     */
    public final TableField<AnalyticNotificationStateRecord, String> FK_ANALYTIC_NOTIFICATION_UUID = createField(DSL.name("fk_analytic_notification_uuid"), SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column
     * <code>stroom.analytic_notification_state.last_execution_time</code>.
     */
    public final TableField<AnalyticNotificationStateRecord, Long> LAST_EXECUTION_TIME = createField(DSL.name("last_execution_time"), SQLDataType.BIGINT, this, "");

    /**
     * The column <code>stroom.analytic_notification_state.message</code>.
     */
    public final TableField<AnalyticNotificationStateRecord, String> MESSAGE = createField(DSL.name("message"), SQLDataType.CLOB, this, "");

    private AnalyticNotificationState(Name alias, Table<AnalyticNotificationStateRecord> aliased) {
        this(alias, aliased, null);
    }

    private AnalyticNotificationState(Name alias, Table<AnalyticNotificationStateRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""), TableOptions.table());
    }

    /**
     * Create an aliased <code>stroom.analytic_notification_state</code> table
     * reference
     */
    public AnalyticNotificationState(String alias) {
        this(DSL.name(alias), ANALYTIC_NOTIFICATION_STATE);
    }

    /**
     * Create an aliased <code>stroom.analytic_notification_state</code> table
     * reference
     */
    public AnalyticNotificationState(Name alias) {
        this(alias, ANALYTIC_NOTIFICATION_STATE);
    }

    /**
     * Create a <code>stroom.analytic_notification_state</code> table reference
     */
    public AnalyticNotificationState() {
        this(DSL.name("analytic_notification_state"), null);
    }

    public <O extends Record> AnalyticNotificationState(Table<O> child, ForeignKey<O, AnalyticNotificationStateRecord> key) {
        super(child, key, ANALYTIC_NOTIFICATION_STATE);
    }

    @Override
    public Schema getSchema() {
        return aliased() ? null : Stroom.STROOM;
    }

    @Override
    public UniqueKey<AnalyticNotificationStateRecord> getPrimaryKey() {
        return Keys.KEY_ANALYTIC_NOTIFICATION_STATE_PRIMARY;
    }

    @Override
    public List<ForeignKey<AnalyticNotificationStateRecord, ?>> getReferences() {
        return Arrays.asList(Keys.FK_ANALYTIC_NOTIFICATION_UUID);
    }

    private transient AnalyticNotification _analyticNotification;

    /**
     * Get the implicit join path to the
     * <code>stroom.analytic_notification</code> table.
     */
    public AnalyticNotification analyticNotification() {
        if (_analyticNotification == null)
            _analyticNotification = new AnalyticNotification(this, Keys.FK_ANALYTIC_NOTIFICATION_UUID);

        return _analyticNotification;
    }

    @Override
    public AnalyticNotificationState as(String alias) {
        return new AnalyticNotificationState(DSL.name(alias), this);
    }

    @Override
    public AnalyticNotificationState as(Name alias) {
        return new AnalyticNotificationState(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public AnalyticNotificationState rename(String name) {
        return new AnalyticNotificationState(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public AnalyticNotificationState rename(Name name) {
        return new AnalyticNotificationState(name, null);
    }

    // -------------------------------------------------------------------------
    // Row3 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row3<String, Long, String> fieldsRow() {
        return (Row3) super.fieldsRow();
    }
}