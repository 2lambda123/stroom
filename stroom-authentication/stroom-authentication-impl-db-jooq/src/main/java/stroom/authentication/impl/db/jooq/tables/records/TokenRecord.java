/*
 * This file is generated by jOOQ.
 */
package stroom.authentication.impl.db.jooq.tables.records;


import javax.annotation.processing.Generated;

import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.Record12;
import org.jooq.Row12;
import org.jooq.impl.UpdatableRecordImpl;

import stroom.authentication.impl.db.jooq.tables.Token;


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
public class TokenRecord extends UpdatableRecordImpl<TokenRecord> implements Record12<Integer, Integer, Long, String, Long, String, Integer, Integer, String, Long, String, Boolean> {

    private static final long serialVersionUID = -1472208913;

    /**
     * Setter for <code>stroom.token.id</code>.
     */
    public void setId(Integer value) {
        set(0, value);
    }

    /**
     * Getter for <code>stroom.token.id</code>.
     */
    public Integer getId() {
        return (Integer) get(0);
    }

    /**
     * Setter for <code>stroom.token.version</code>.
     */
    public void setVersion(Integer value) {
        set(1, value);
    }

    /**
     * Getter for <code>stroom.token.version</code>.
     */
    public Integer getVersion() {
        return (Integer) get(1);
    }

    /**
     * Setter for <code>stroom.token.create_time_ms</code>.
     */
    public void setCreateTimeMs(Long value) {
        set(2, value);
    }

    /**
     * Getter for <code>stroom.token.create_time_ms</code>.
     */
    public Long getCreateTimeMs() {
        return (Long) get(2);
    }

    /**
     * Setter for <code>stroom.token.create_user</code>.
     */
    public void setCreateUser(String value) {
        set(3, value);
    }

    /**
     * Getter for <code>stroom.token.create_user</code>.
     */
    public String getCreateUser() {
        return (String) get(3);
    }

    /**
     * Setter for <code>stroom.token.update_time_ms</code>.
     */
    public void setUpdateTimeMs(Long value) {
        set(4, value);
    }

    /**
     * Getter for <code>stroom.token.update_time_ms</code>.
     */
    public Long getUpdateTimeMs() {
        return (Long) get(4);
    }

    /**
     * Setter for <code>stroom.token.update_user</code>.
     */
    public void setUpdateUser(String value) {
        set(5, value);
    }

    /**
     * Getter for <code>stroom.token.update_user</code>.
     */
    public String getUpdateUser() {
        return (String) get(5);
    }

    /**
     * Setter for <code>stroom.token.fk_account_id</code>.
     */
    public void setFkAccountId(Integer value) {
        set(6, value);
    }

    /**
     * Getter for <code>stroom.token.fk_account_id</code>.
     */
    public Integer getFkAccountId() {
        return (Integer) get(6);
    }

    /**
     * Setter for <code>stroom.token.fk_token_type_id</code>.
     */
    public void setFkTokenTypeId(Integer value) {
        set(7, value);
    }

    /**
     * Getter for <code>stroom.token.fk_token_type_id</code>.
     */
    public Integer getFkTokenTypeId() {
        return (Integer) get(7);
    }

    /**
     * Setter for <code>stroom.token.data</code>.
     */
    public void setData(String value) {
        set(8, value);
    }

    /**
     * Getter for <code>stroom.token.data</code>.
     */
    public String getData() {
        return (String) get(8);
    }

    /**
     * Setter for <code>stroom.token.expires_on_ms</code>.
     */
    public void setExpiresOnMs(Long value) {
        set(9, value);
    }

    /**
     * Getter for <code>stroom.token.expires_on_ms</code>.
     */
    public Long getExpiresOnMs() {
        return (Long) get(9);
    }

    /**
     * Setter for <code>stroom.token.comments</code>.
     */
    public void setComments(String value) {
        set(10, value);
    }

    /**
     * Getter for <code>stroom.token.comments</code>.
     */
    public String getComments() {
        return (String) get(10);
    }

    /**
     * Setter for <code>stroom.token.enabled</code>.
     */
    public void setEnabled(Boolean value) {
        set(11, value);
    }

    /**
     * Getter for <code>stroom.token.enabled</code>.
     */
    public Boolean getEnabled() {
        return (Boolean) get(11);
    }

    // -------------------------------------------------------------------------
    // Primary key information
    // -------------------------------------------------------------------------

    @Override
    public Record1<Integer> key() {
        return (Record1) super.key();
    }

    // -------------------------------------------------------------------------
    // Record12 type implementation
    // -------------------------------------------------------------------------

    @Override
    public Row12<Integer, Integer, Long, String, Long, String, Integer, Integer, String, Long, String, Boolean> fieldsRow() {
        return (Row12) super.fieldsRow();
    }

    @Override
    public Row12<Integer, Integer, Long, String, Long, String, Integer, Integer, String, Long, String, Boolean> valuesRow() {
        return (Row12) super.valuesRow();
    }

    @Override
    public Field<Integer> field1() {
        return Token.TOKEN.ID;
    }

    @Override
    public Field<Integer> field2() {
        return Token.TOKEN.VERSION;
    }

    @Override
    public Field<Long> field3() {
        return Token.TOKEN.CREATE_TIME_MS;
    }

    @Override
    public Field<String> field4() {
        return Token.TOKEN.CREATE_USER;
    }

    @Override
    public Field<Long> field5() {
        return Token.TOKEN.UPDATE_TIME_MS;
    }

    @Override
    public Field<String> field6() {
        return Token.TOKEN.UPDATE_USER;
    }

    @Override
    public Field<Integer> field7() {
        return Token.TOKEN.FK_ACCOUNT_ID;
    }

    @Override
    public Field<Integer> field8() {
        return Token.TOKEN.FK_TOKEN_TYPE_ID;
    }

    @Override
    public Field<String> field9() {
        return Token.TOKEN.DATA;
    }

    @Override
    public Field<Long> field10() {
        return Token.TOKEN.EXPIRES_ON_MS;
    }

    @Override
    public Field<String> field11() {
        return Token.TOKEN.COMMENTS;
    }

    @Override
    public Field<Boolean> field12() {
        return Token.TOKEN.ENABLED;
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
    public Integer component7() {
        return getFkAccountId();
    }

    @Override
    public Integer component8() {
        return getFkTokenTypeId();
    }

    @Override
    public String component9() {
        return getData();
    }

    @Override
    public Long component10() {
        return getExpiresOnMs();
    }

    @Override
    public String component11() {
        return getComments();
    }

    @Override
    public Boolean component12() {
        return getEnabled();
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
    public Integer value7() {
        return getFkAccountId();
    }

    @Override
    public Integer value8() {
        return getFkTokenTypeId();
    }

    @Override
    public String value9() {
        return getData();
    }

    @Override
    public Long value10() {
        return getExpiresOnMs();
    }

    @Override
    public String value11() {
        return getComments();
    }

    @Override
    public Boolean value12() {
        return getEnabled();
    }

    @Override
    public TokenRecord value1(Integer value) {
        setId(value);
        return this;
    }

    @Override
    public TokenRecord value2(Integer value) {
        setVersion(value);
        return this;
    }

    @Override
    public TokenRecord value3(Long value) {
        setCreateTimeMs(value);
        return this;
    }

    @Override
    public TokenRecord value4(String value) {
        setCreateUser(value);
        return this;
    }

    @Override
    public TokenRecord value5(Long value) {
        setUpdateTimeMs(value);
        return this;
    }

    @Override
    public TokenRecord value6(String value) {
        setUpdateUser(value);
        return this;
    }

    @Override
    public TokenRecord value7(Integer value) {
        setFkAccountId(value);
        return this;
    }

    @Override
    public TokenRecord value8(Integer value) {
        setFkTokenTypeId(value);
        return this;
    }

    @Override
    public TokenRecord value9(String value) {
        setData(value);
        return this;
    }

    @Override
    public TokenRecord value10(Long value) {
        setExpiresOnMs(value);
        return this;
    }

    @Override
    public TokenRecord value11(String value) {
        setComments(value);
        return this;
    }

    @Override
    public TokenRecord value12(Boolean value) {
        setEnabled(value);
        return this;
    }

    @Override
    public TokenRecord values(Integer value1, Integer value2, Long value3, String value4, Long value5, String value6, Integer value7, Integer value8, String value9, Long value10, String value11, Boolean value12) {
        value1(value1);
        value2(value2);
        value3(value3);
        value4(value4);
        value5(value5);
        value6(value6);
        value7(value7);
        value8(value8);
        value9(value9);
        value10(value10);
        value11(value11);
        value12(value12);
        return this;
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    /**
     * Create a detached TokenRecord
     */
    public TokenRecord() {
        super(Token.TOKEN);
    }

    /**
     * Create a detached, initialised TokenRecord
     */
    public TokenRecord(Integer id, Integer version, Long createTimeMs, String createUser, Long updateTimeMs, String updateUser, Integer fkAccountId, Integer fkTokenTypeId, String data, Long expiresOnMs, String comments, Boolean enabled) {
        super(Token.TOKEN);

        set(0, id);
        set(1, version);
        set(2, createTimeMs);
        set(3, createUser);
        set(4, updateTimeMs);
        set(5, updateUser);
        set(6, fkAccountId);
        set(7, fkTokenTypeId);
        set(8, data);
        set(9, expiresOnMs);
        set(10, comments);
        set(11, enabled);
    }
}
