ALTER TABLE tag ADD color CHAR(6) CHECK (color ~ '^[0-9a-f]{6}$')
