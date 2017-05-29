-- Too many connections? That's probably an error, so fail fast.
ALTER SYSTEM SET max_connections = '25';

-- Give user queries more memory -- some queries may speed up.
ALTER SYSTEM SET work_mem = '16MB';

-- Cost: in dev mode, a COMMIT may not actually fsync until 600ms later
-- Benefit: tests and dev are waaaaaay faster
ALTER SYSTEM SET synchronous_commit = 'off';

-- Log to stderr when a query takes longer than we want.
--
-- Cost: There are some queries we _know_ take a long time, like large-object
-- writes. So this adds a bit of noise.
ALTER SYSTEM SET log_min_duration_statement = '750ms';
