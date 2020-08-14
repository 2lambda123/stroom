-- A set of SQL queries to run before migrating from v6 to v7
-- Run with the mysql --table arg to get formatted output
-- e.g.
-- docker exec -i stroom-all-dbs mysql --table -h"localhost" -P"3307" -u"stroomuser" -p"stroompassword1" stroom < v7_db_pre_migration_checks.sql > v7_db_pre_migration_checks.out


\! echo 'Find orphaned USR_GRP_USR records, that will not be migrated';

SELECT *
FROM USR_GRP_USR
WHERE NOT EXISTS (
    SELECT NULL
    FROM USR
    WHERE UUID = USR_UUID)
OR NOT EXISTS (
    SELECT NULL
    FROM USR
    WHERE UUID = GRP_UUID)
ORDER BY ID;


\! echo 'Find orphaned DOC_PERM records, that will not be migrated';

SELECT *
FROM DOC_PERM
WHERE NOT EXISTS (
    SELECT NULL
    FROM USR
    WHERE UUID = USR_UUID)
ORDER BY ID;

\! echo 'Find orphaned DOC_PERM records, that will not be migrated';

SELECT *
FROM APP_PERM ap
JOIN PERM p ON (p.ID = ap.FK_PERM_ID)
WHERE NOT EXISTS (
    SELECT NULL
    FROM USR
    WHERE UUID = ap.USR_UUID)
ORDER BY ap.ID;

\! echo 'Find IDX/IDX_VOL records that would violate new primary key';

SELECT 
  iv.FK_VOL_ID, 
  i.UUID, 
  COUNT(*)
FROM IDX i
INNER JOIN IDX_VOL iv on i.ID = iv.FK_IDX_ID
GROUP BY
  iv.FK_VOL_ID, 
  i.UUID
HAVING COUNT(*) > 1;

\! echo 'Find IDX/IDX_VOL records that would violate NOT NULL conditions in new query table';

SELECT
  *
FROM
  QUERY
WHERE NAME IS NULL
OR DASH_UUID IS NULL
OR QUERY_ID IS NULL;

\! echo 'Find IDX_SHRD records incorrectly linked to PUBLIC volumes. Should be zero.';

SELECT COUNT(*)
FROM OLD_IDX_SHRD s
JOIN OLD_VOL v ON s.FK_VOL_ID = v.ID
WHERE v.VOL_TP = 0;

\! echo 'Finished';