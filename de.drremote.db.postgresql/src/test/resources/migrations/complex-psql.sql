\set generated_id 7
\if true
CREATE TABLE ${table} (id INTEGER PRIMARY KEY, label TEXT NOT NULL);

\copy ${table} (id, label) FROM stdin WITH (FORMAT csv)
1,first
2,second
\.

CREATE FUNCTION adjusted_value(value INTEGER) RETURNS INTEGER
LANGUAGE plpgsql AS $function$
BEGIN
    RETURN value + 10;
END;
$function$;

DO $block$
BEGIN
    INSERT INTO ${table} VALUES (3, 'inside; block');
END;
$block$;

SELECT 'INSERT INTO ${table} VALUES (' || :generated_id || ', ''generated'')' \gexec
\endif
