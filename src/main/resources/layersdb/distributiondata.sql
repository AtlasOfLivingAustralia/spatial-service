-- Table: public.distributiondata

-- DROP TABLE public.distributiondata;

CREATE TABLE public.distributiondata
(
    gid integer NOT NULL DEFAULT nextval('distributiondata_gid_seq'::regclass),
    spcode numeric,
    scientific character varying(254) COLLATE pg_catalog."default",
    authority_ character varying(254) COLLATE pg_catalog."default",
    common_nam character varying(254) COLLATE pg_catalog."default",
    family character varying(254) COLLATE pg_catalog."default",
    genus_name character varying(254) COLLATE pg_catalog."default",
    specific_n character varying(254) COLLATE pg_catalog."default",
    min_depth numeric,
    max_depth numeric,
    pelagic_fl numeric,
    metadata_u character varying(254) COLLATE pg_catalog."default",
    the_geom geometry,
    wmsurl character varying COLLATE pg_catalog."default",
    lsid character varying COLLATE pg_catalog."default",
    geom_idx integer,
    type character(1) COLLATE pg_catalog."default",
    checklist_name character varying COLLATE pg_catalog."default",
    notes text COLLATE pg_catalog."default",
    estuarine_fl boolean,
    coastal_fl boolean,
    desmersal_fl boolean,
    group_name text COLLATE pg_catalog."default",
    genus_exemplar boolean,
    family_exemplar boolean,
    caab_species_number text COLLATE pg_catalog."default",
    caab_species_url text COLLATE pg_catalog."default",
    caab_family_number text COLLATE pg_catalog."default",
    caab_family_url text COLLATE pg_catalog."default",
    metadata_uuid text COLLATE pg_catalog."default",
    family_lsid text COLLATE pg_catalog."default",
    genus_lsid text COLLATE pg_catalog."default",
    bounding_box geometry,
    data_resource_uid character varying COLLATE pg_catalog."default",
    original_scientific_name character varying COLLATE pg_catalog."default",
    image_quality character(1) COLLATE pg_catalog."default",
    the_geom_orig geometry,
    endemic boolean DEFAULT false,
    CONSTRAINT distributiondata_pkey PRIMARY KEY (gid),
    CONSTRAINT distributiondata_spcode_key UNIQUE (spcode),
    CONSTRAINT distributiondata_the_geom_check CHECK (st_ndims(the_geom) = 2),
    CONSTRAINT distributiondata_the_geom_check1 CHECK (geometrytype(the_geom) = 'MULTIPOLYGON'::text OR the_geom IS NULL),
    CONSTRAINT distributiondata_the_geom_check3 CHECK (st_srid(the_geom) = 4326)
)
WITH (
    OIDS = FALSE
)
TABLESPACE pg_default;

ALTER TABLE public.distributiondata
    OWNER to postgres;

-- Index: distributiondata_idx

-- DROP INDEX public.distributiondata_idx;

CREATE INDEX distributiondata_idx
    ON public.distributiondata USING btree
    (geom_idx)
    TABLESPACE pg_default;

-- Index: distributiondata_the_geom_idx

-- DROP INDEX public.distributiondata_the_geom_idx;

CREATE INDEX distributiondata_the_geom_idx
    ON public.distributiondata USING gist
    (the_geom)
    TABLESPACE pg_default;