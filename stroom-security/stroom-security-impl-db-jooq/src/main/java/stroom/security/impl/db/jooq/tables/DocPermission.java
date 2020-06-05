/*
 * This file is generated by jOOQ.
 */
package stroom.security.impl.db.jooq.tables;


import java.util.Arrays;
import java.util.List;

import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.Index;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Row4;
import org.jooq.Schema;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.TableImpl;

import stroom.security.impl.db.jooq.Indexes;
import stroom.security.impl.db.jooq.Keys;
import stroom.security.impl.db.jooq.Stroom;
import stroom.security.impl.db.jooq.tables.records.DocPermissionRecord;


/**
 * This class is generated by jOOQ.
 */
@Generated(
    value = {
        "http://www.jooq.org",
        "jOOQ version:3.12.3"
    },
    comments = "This class is generated by jOOQ"
)
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class DocPermission extends TableImpl<DocPermissionRecord> {

    private static final long serialVersionUID = -2104594977;

    /**
     * The reference instance of <code>stroom.doc_permission</code>
     */
    public static final DocPermission DOC_PERMISSION = new DocPermission();

    /**
     * The class holding records for this type
     */
    @Override
    public Class<DocPermissionRecord> getRecordType() {
        return DocPermissionRecord.class;
    }

    /**
     * The column <code>stroom.doc_permission.id</code>.
     */
    public final TableField<DocPermissionRecord, Long> ID = createField(DSL.name("id"), org.jooq.impl.SQLDataType.BIGINT.nullable(false).identity(true), this, "");

    /**
     * The column <code>stroom.doc_permission.user_uuid</code>.
     */
    public final TableField<DocPermissionRecord, String> USER_UUID = createField(DSL.name("user_uuid"), org.jooq.impl.SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.doc_permission.doc_uuid</code>.
     */
    public final TableField<DocPermissionRecord, String> DOC_UUID = createField(DSL.name("doc_uuid"), org.jooq.impl.SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * The column <code>stroom.doc_permission.permission</code>.
     */
    public final TableField<DocPermissionRecord, String> PERMISSION = createField(DSL.name("permission"), org.jooq.impl.SQLDataType.VARCHAR(255).nullable(false), this, "");

    /**
     * Create a <code>stroom.doc_permission</code> table reference
     */
    public DocPermission() {
        this(DSL.name("doc_permission"), null);
    }

    /**
     * Create an aliased <code>stroom.doc_permission</code> table reference
     */
    public DocPermission(String alias) {
        this(DSL.name(alias), DOC_PERMISSION);
    }

    /**
     * Create an aliased <code>stroom.doc_permission</code> table reference
     */
    public DocPermission(Name alias) {
        this(alias, DOC_PERMISSION);
    }

    private DocPermission(Name alias, Table<DocPermissionRecord> aliased) {
        this(alias, aliased, null);
    }

    private DocPermission(Name alias, Table<DocPermissionRecord> aliased, Field<?>[] parameters) {
        super(alias, null, aliased, parameters, DSL.comment(""));
    }

    public <O extends Record> DocPermission(Table<O> child, ForeignKey<O, DocPermissionRecord> key) {
        super(child, key, DOC_PERMISSION);
    }

    @Override
    public Schema getSchema() {
        return Stroom.STROOM;
    }

    @Override
    public List<Index> getIndexes() {
        return Arrays.<Index>asList(Indexes.DOC_PERMISSION_DOC_PERMISSION_DOC_UUID, Indexes.DOC_PERMISSION_DOC_PERMISSION_FK_USER_UUID, Indexes.DOC_PERMISSION_DOC_PERMISSION_FK_USER_UUID_DOC_UUID_PERMISSION_IDX, Indexes.DOC_PERMISSION_PRIMARY);
    }

    @Override
    public Identity<DocPermissionRecord, Long> getIdentity() {
        return Keys.IDENTITY_DOC_PERMISSION;
    }

    @Override
    public UniqueKey<DocPermissionRecord> getPrimaryKey() {
        return Keys.KEY_DOC_PERMISSION_PRIMARY;
    }

    @Override
    public List<UniqueKey<DocPermissionRecord>> getKeys() {
        return Arrays.<UniqueKey<DocPermissionRecord>>asList(Keys.KEY_DOC_PERMISSION_PRIMARY, Keys.KEY_DOC_PERMISSION_DOC_PERMISSION_FK_USER_UUID_DOC_UUID_PERMISSION_IDX);
    }

    @Override
    public List<ForeignKey<DocPermissionRecord, ?>> getReferences() {
        return Arrays.<ForeignKey<DocPermissionRecord, ?>>asList(Keys.DOC_PERMISSION_FK_USER_UUID);
    }

    public StroomUser stroomUser() {
        return new StroomUser(this, Keys.DOC_PERMISSION_FK_USER_UUID);
    }

    @Override
    public DocPermission as(String alias) {
        return new DocPermission(DSL.name(alias), this);
    }

    @Override
    public DocPermission as(Name alias) {
        return new DocPermission(alias, this);
    }

    /**
     * Rename this table
     */
    @Override
    public DocPermission rename(String name) {
        return new DocPermission(DSL.name(name), null);
    }

    /**
     * Rename this table
     */
    @Override
    public DocPermission rename(Name name) {
        return new DocPermission(name, null);
    }

    // -------------------------------------------------------------------------
    // Row4 type methods
    // -------------------------------------------------------------------------

    @Override
    public Row4<Long, String, String, String> fieldsRow() {
        return (Row4) super.fieldsRow();
    }
}
