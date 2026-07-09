-- Migrate existing MANAGER users to ADMIN role
UPDATE users SET role = 'ADMIN' WHERE role = 'MANAGER';

-- Update the role check constraint to allow only ADMIN and WORKER
ALTER TABLE users DROP CONSTRAINT chk_users_role;
ALTER TABLE users ADD CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'WORKER'));
