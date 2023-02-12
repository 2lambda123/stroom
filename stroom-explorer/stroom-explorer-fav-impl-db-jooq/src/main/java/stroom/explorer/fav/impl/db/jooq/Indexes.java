/*
 * This file is generated by jOOQ.
 */
package stroom.explorer.fav.impl.db.jooq;


import org.jooq.Index;
import org.jooq.OrderField;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;

import stroom.explorer.fav.impl.db.jooq.tables.ExplorerFavourite;


/**
 * A class modelling indexes of tables in stroom.
 */
@SuppressWarnings({ "all", "unchecked", "rawtypes" })
public class Indexes {

    // -------------------------------------------------------------------------
    // INDEX definitions
    // -------------------------------------------------------------------------

    public static final Index EXPLORER_FAVOURITE_EXPLORER_FAVOURITE_FK_DOC_TYPE_DOC_UUID = Internal.createIndex(DSL.name("explorer_favourite_fk_doc_type_doc_uuid"), ExplorerFavourite.EXPLORER_FAVOURITE, new OrderField[] { ExplorerFavourite.EXPLORER_FAVOURITE.DOC_TYPE, ExplorerFavourite.EXPLORER_FAVOURITE.DOC_UUID }, false);
    public static final Index EXPLORER_FAVOURITE_EXPLORER_FAVOURITE_FK_USER_UUID = Internal.createIndex(DSL.name("explorer_favourite_fk_user_uuid"), ExplorerFavourite.EXPLORER_FAVOURITE, new OrderField[] { ExplorerFavourite.EXPLORER_FAVOURITE.USER_UUID }, false);
}
