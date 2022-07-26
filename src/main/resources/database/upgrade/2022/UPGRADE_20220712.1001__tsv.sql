
CREATE OR REPLACE FUNCTION items_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
            setweight(to_tsvector('title_stem', new.name), 'A') ||
            setweight(to_tsvector(coalesce(new.keywords,'')), 'B') ||
            setweight(to_tsvector('title_stem', coalesce(new.summary,'')), 'C') ||
            setweight(to_tsvector('title_stem', coalesce(new.description_text,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

UPDATE items SET tsv =
    setweight(to_tsvector('title_stem', name), 'A') ||
    setweight(to_tsvector(coalesce(keywords,'')), 'B') ||
    setweight(to_tsvector('title_stem', coalesce(summary,'')), 'C') ||
    setweight(to_tsvector('title_stem', coalesce(description_text,'')), 'D');

CREATE OR REPLACE FUNCTION blog_posts_tsv_trigger() RETURNS trigger AS $$
begin
  new.tsv :=
            setweight(to_tsvector('title_stem', new.title), 'A') ||
            setweight(to_tsvector(coalesce(new.keywords,'')), 'B') ||
            setweight(to_tsvector('title_stem', coalesce(new.summary,'')), 'C') ||
            setweight(to_tsvector('title_stem', coalesce(new.body_text,'')), 'D');
  return new;
end
$$ LANGUAGE plpgsql;

UPDATE blog_posts SET tsv =
    setweight(to_tsvector('title_stem', title), 'A') ||
    setweight(to_tsvector(coalesce(keywords,'')), 'B') ||
    setweight(to_tsvector('title_stem', coalesce(summary,'')), 'C') ||
    setweight(to_tsvector('title_stem', coalesce(body_text,'')), 'D');
