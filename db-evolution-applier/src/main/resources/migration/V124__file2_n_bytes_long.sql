-- Originally, file2 blobs supported max 2^31-1 bytes -- because the number of
-- bytes was stored as int. That led to overflow when storing large files; and
-- overflow led to failed database queries which ultimately killed the worker.
--
-- Support large files.
--
-- Tested on production: a query on a fresh table with identical data took 70s.
ALTER TABLE file2
ALTER COLUMN blob_n_bytes TYPE BIGINT;
