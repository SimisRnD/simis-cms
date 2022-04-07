-- Copyright 2022 SimIS Inc. (https://www.simiscms.com), Licensed under the Apache License, Version 2.0 (the "License").
-- Social Media

CREATE TABLE instagram_media (
  id BIGSERIAL PRIMARY KEY,
  graph_id VARCHAR(255) NOT NULL,
  permalink VARCHAR(255),
  media_type VARCHAR(100),
  media_url TEXT,
  caption TEXT,
  short_code VARCHAR(100),
  timestamp VARCHAR(30),
  created TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX instagram_graph_id_idx ON instagram_media(graph_id);
CREATE INDEX instagram_media_ty_idx ON instagram_media(media_type);
CREATE INDEX instagram_created_idx ON instagram_media(created);
