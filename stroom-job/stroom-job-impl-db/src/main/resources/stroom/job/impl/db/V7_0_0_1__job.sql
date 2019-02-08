--
-- Create the job tables
--
USE stroom;

CREATE TABLE IF NOT EXISTS job (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  description           varchar(255) DEFAULT NULL,
  enabled               bit(1) NOT NULL,
  version               int(11) NOT NULL,
  PRIMARY KEY           (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;


CREATE TABLE IF NOT EXISTS job_node (
  id                    int(11) NOT NULL AUTO_INCREMENT,
  node_name             varchar(255) NOT NULL,
  job_type              tinyint(4) NOT NULL,
  enabled               bit(1) NOT NULL,
  task_limit            int(11) NOT NULL,
  job_id                int(11) NOT NULL,
  schedule              varchar(255) NOT NULL,
  version               int(11) NOT NULL,
  PRIMARY KEY           (id),
  CONSTRAINT job_id FOREIGN KEY (job_id) REFERENCES job (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

--
-- Copy data into the job tables
--
DROP PROCEDURE IF EXISTS copy_job;
DELIMITER //
CREATE PROCEDURE copy_job ()
BEGIN
  -- TODO update auto-increment, see V7_0_0_1__config.sql as an example
    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'JB' > 0) THEN
        INSERT INTO job (id, description, enabled, version)
        SELECT ID, NAME, ENBL, 1
        FROM JB;
    END IF;

    IF (SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES where TABLE_NAME = 'JB_ND' > 0) THEN
        INSERT INTO job_node (id, node_name, job_type, enabled, task_limit, job_id, schedule, version)
        SELECT ID, FK_ND_ID, JB_TP, ENBL, TASK_LMT, FK_JB_ID, SCHEDULE, 1
        FROM JB_ND;
    END IF;
END//
DELIMITER ;
CALL copy_job();
DROP PROCEDURE copy_job;