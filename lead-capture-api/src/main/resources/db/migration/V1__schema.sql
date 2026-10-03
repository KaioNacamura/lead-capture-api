CREATE TABLE organizations (
  id           uuid PRIMARY KEY,
  name         text NOT NULL,
  -- Guardamos só o SHA-256 da chave de API, nunca a chave.
  api_key_hash char(64) NOT NULL UNIQUE
);

CREATE TABLE ingestion_events (
  id              bigserial PRIMARY KEY,
  organization_id uuid        NOT NULL REFERENCES organizations (id),
  event_id        text        NOT NULL,
  payload         jsonb       NOT NULL,
  status          text        NOT NULL DEFAULT 'pending'
                  CHECK (status IN ('pending', 'processing', 'done', 'dead')),
  attempts        integer     NOT NULL DEFAULT 0,
  available_at    timestamptz NOT NULL DEFAULT now(),
  locked_at       timestamptz,
  processed_at    timestamptz,
  last_error      text,
  created_at      timestamptz NOT NULL DEFAULT now(),
  -- Reenviar o mesmo eventId da mesma empresa não cria outro evento.
  UNIQUE (organization_id, event_id)
);

-- Índice parcial: o worker só procura eventos que ainda não terminaram.
CREATE INDEX ingestion_events_open_idx
  ON ingestion_events (id)
  WHERE status IN ('pending', 'processing');

CREATE TABLE leads (
  id                 bigserial PRIMARY KEY,
  organization_id    uuid        NOT NULL REFERENCES organizations (id),
  ingestion_event_id bigint      NOT NULL UNIQUE REFERENCES ingestion_events (id),
  email              text        NOT NULL,
  name               text,
  source             text,
  created_at         timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX leads_org_created_idx ON leads (organization_id, created_at DESC);
