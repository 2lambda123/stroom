/*
 * This file is generated by jOOQ.
 */
package stroom.config.impl.db.jooq.tables.records;


import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record8;
import org.jooq.Row8;
import org.jooq.impl.UpdatableRecordImpl;

import stroom.config.impl.db.jooq.tables.Preferences;


/**
 * This class is generated by jOOQ.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class PreferencesRecord extends UpdatableRecordImpl<PreferencesRecord> implements Record8<Integer, Integer, Long, String, Long, String, String, String> {

    private static final long serialVersionUID = 1L;

    /**
     * Setter for <code>stroom.preferences.id</code>.
     */
    public void setId(Integer value) {
        set(0, value);
    }

    /**
     * Getter for <code>stroom.preferences.id</code>.
     */
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>stroom.preferences.version</code>.
     */
    public void setVersion(Integer value) {
        set(1, value);
    }

    /**
     * Getter for <code>stroom.preferences.version</code>.
     */
    public Integer getVersion() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>stroom.preferences.create_time_ms</code>.
     */
    public void setCreateTimeMs(Long value) {
        set(2, value);
    }

    /**
     * Getter for <code>stroom.preferences.create_time_ms</code>.
     */
    public Long getCreateTimeMs() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>stroom.preferences.create_user</code>.
     */
    public void setCreateUser(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>stroom.preferences.create_user</code>.
     */
    public String getCreateUser() {
        return (String) get(3);
    }

    /**
     * Setter for <code>stroom.preferences.update_time_ms</code>.
     */
    public void setUpdateTimeMs(Long value) {
        set(4, value);
    }

    /**
     * Getter for <code>stroom.preferences.update_time_ms</code>.
     */
    public Long getUpdateTimeMs() {
        return (Long) get(4);
    }

    /**
     * Setter for <code>stroom.preferences.update_user</code>.
     */
    public void setUpdateUser(String value) {
        set(5, value);
    }

    /**
     * Getter for <code>stroom.preferences.update_user</code>.
     */
    public String getUpdateUser() {
        return (String) get(5);
    }

    /**
     * Setter for <code>stroom.preferences.user_id</code>.
     */
    public void setUserId(String value) {
        set(6, value);
    }

    /**
     * Getter for <code>stroom.preferences.user_id</code>.
     */
    public String getUserId() {
        return (String) get(6);
    }

    /**
     * Setter for <code>stroom.preferences.dat</code>.
     */
    public void setDat(String value) {
        set(7, value);
    }

    /**
     * Getter for <code>stroom.preferences.dat</code>.
     */
    public String getDat() {
        return (String) get(7);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record8 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row8<Integer, Integer, Long, String, Long, String, String, String> fieldsRow() {
        return (Row8) super.fieldsRow();
    }

    @Override
    public Row8<Integer, Integer, Long, String, Long, String, String, String> valuesRow() {
        return (Row8) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return Preferences.PREFERENCES.ID;
    }

    @Override
    public Field<Integer> field2() {
        return Preferences.PREFERENCES.VERSION;
    }

    @Override
    public Field<Long> field3() {
        return Preferences.PREFERENCES.CREATE_TIME_MS;
    }

    @Override
    public Field<String> field4() {
        return Preferences.PREFERENCES.CREATE_USER;
    }

    @Override
    public Field<Long> field5() {
        return Preferences.PREFERENCES.UPDATE_TIME_MS;
    }

    @Override
    public Field<String> field6() {
        return Preferences.PREFERENCES.UPDATE_USER;
    }

    @Override
    public Field<String> field7() {
        return Preferences.PREFERENCES.USER_ID;
    }

    @Override
    public Field<String> field8() {
        return Preferences.PREFERENCES.DAT;
    }

    @Override
    public Integer component1() {
        return getId();
    }

    @Override
    public Integer component2() {
        return getVersion();
    }

    @Override
    public Long component3() {
        return getCreateTimeMs();
    }

    @Override
    public String component4() {
        return getCreateUser();
    }

    @Override
    public Long component5() {
        return getUpdateTimeMs();
    }

    @Override
    public String component6() {
        return getUpdateUser();
    }

    @Override
    public String component7() {
        return getUserId();
    }

    @Override
    public String component8() {
        return getDat();
    }

    @Override
    public Integer value1() {
        return getId();
    }

    @Override
    public Integer value2() {
        return getVersion();
    }

    @Override
    public Long value3() {
        return getCreateTimeMs();
    }

    @Override
    public String value4() {
        return getCreateUser();
    }

    @Override
    public Long value5() {
        return getUpdateTimeMs();
    }

    @Override
    public String value6() {
        return getUpdateUser();
    }

    @Override
    public String value7() {
        return getUserId();
    }

    @Override
    public String value8() {
        return getDat();
    }

    @Override
    public PreferencesRecord value1(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public PreferencesRecord value2(Integer value) {
        setVersion(value);
        return this;
    }

    @Override
    public PreferencesRecord value3(Long value) {
        setCreateTimeMs(value);
        return this;
    }

    @Override
    public PreferencesRecord value4(String value) {
        setCreateUser(value);
        return this;
    }

    @Override
    public PreferencesRecord value5(Long value) {
        setUpdateTimeMs(value);
        return this;
    }

    @Override
    public PreferencesRecord value6(String value) {
        setUpdateUser(value);
        return this;
    }

    @Override
    public PreferencesRecord value7(String value) {
        setUserId(value);
        return this;
    }

    @Override
    public PreferencesRecord value8(String value) {
        setDat(value);
        return this;
    }

    @Override
    public PreferencesRecord values(Integer value1, Integer value2, Long value3, String value4, Long value5, String value6, String value7, String value8) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached PreferencesRecord
     */
    public PreferencesRecord() {
        super(Preferences.PREFERENCES);
    }

    /**
     * Create a detached, initialised PreferencesRecord
     */
    public PreferencesRecord(Integer id, Integer version, Long createTimeMs, String createUser, Long updateTimeMs, String updateUser, String userId, String dat) {
        super(Preferences.PREFERENCES);

        setId(id);
        setVersion(version);
        setCreateTimeMs(createTimeMs);
        setCreateUser(createUser);
        setUpdateTimeMs(updateTimeMs);
        setUpdateUser(updateUser);
        setUserId(userId);
        setDat(dat);
    }
}