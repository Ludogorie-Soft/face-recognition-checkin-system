CREATE TABLE companies (
    id                  UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(255) NOT NULL,
    address             VARCHAR(500),
    phone               VARCHAR(50),
    email               VARCHAR(255),
    registration_number VARCHAR(50),
    mol                 VARCHAR(255),
    active              BOOLEAN      NOT NULL DEFAULT true,
    created_at          TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE company_sites (
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    site_id    UUID NOT NULL REFERENCES sites(id)    ON DELETE CASCADE,
    PRIMARY KEY (company_id, site_id)
);

CREATE TABLE company_workers (
    company_id UUID NOT NULL REFERENCES companies(id) ON DELETE CASCADE,
    worker_id  UUID NOT NULL REFERENCES users(id)    ON DELETE CASCADE,
    PRIMARY KEY (company_id, worker_id)
);

ALTER TABLE users DROP COLUMN IF EXISTS company;
