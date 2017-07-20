-- An index the worker uses to find incomplete jobs.
-- 
-- The number of trees in this index should always be tiny, or zero.

CREATE INDEX ON tree (id) WHERE progress <> 1.0;
