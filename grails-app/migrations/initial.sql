
SET statement_timeout = 0;
SET lock_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = off;
SET check_function_bodies = false;
SET client_min_messages = warning;
SET escape_string_warning = off;

SET search_path = public, pg_catalog;

SET default_with_oids = false;

-- Enable PostGIS (includes raster)
CREATE EXTENSION postgis;
-- Enable Topology
CREATE EXTENSION postgis_topology;

--- SEQUENCES
CREATE SEQUENCE objects_id_seq
    INCREMENT 1
  MINVALUE 1
  MAXVALUE 9223372036854775807
  START 1
  CACHE 1;

--- SEQUENCES
CREATE SEQUENCE layers_id_seq
    INCREMENT 1
  MINVALUE 10000
  MAXVALUE 9223372036854775807
  START 10000
  CACHE 1;

CREATE TABLE IF NOT EXISTS task
(
    id serial NOT NULL,
    parent_id serial,
    name character varying(40),
    json character varying(256),
    email character varying(256),
    user_id character varying(256),
    session_id character varying(256),
    url character varying(256),
    message character varying(256),
    error character varying(256),
    slave character varying(256),
    tag character varying(256),
    status integer,
    created timestamp without time zone,
    finished timestamp without time zone,
    size integer,
    started time without time zone,
    CONSTRAINT task_pk PRIMARY KEY (id)
);

--
-- TOC entry 412 (class 1259 OID 4316544)
-- Name: distributiondata; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE distributiondata (
                                  gid serial NOT NULL,
                                  spcode numeric,
                                  scientific character varying(254),
                                  authority_ character varying(254),
                                  common_nam character varying(254),
                                  family character varying(254),
                                  genus_name character varying(254),
                                  specific_n character varying(254),
                                  min_depth numeric,
                                  max_depth numeric,
                                  pelagic_fl numeric,
                                  metadata_u character varying(254),
                                  the_geom geometry,
                                  wmsurl character varying,
                                  lsid character varying,
                                  geom_idx integer,
                                  type character(1),
                                  checklist_name character varying,
                                  notes text,
                                  estuarine_fl boolean,
                                  coastal_fl boolean,
                                  desmersal_fl boolean,
                                  group_name text,
                                  genus_exemplar boolean,
                                  family_exemplar boolean,
                                  caab_species_number text,
                                  caab_species_url text,
                                  caab_family_number text,
                                  caab_family_url text,
                                  metadata_uuid text,
                                  family_lsid text,
                                  genus_lsid text,
                                  bounding_box geometry,
                                  data_resource_uid character varying,
                                  original_scientific_name character varying,
                                  image_quality character(1),
                                  the_geom_orig geometry,
                                  endemic boolean DEFAULT false,
                                  CONSTRAINT distributiondata_the_geom_check CHECK ((st_ndims(the_geom) = 2)),
                                  CONSTRAINT distributiondata_the_geom_check1 CHECK (((geometrytype(the_geom) = 'MULTIPOLYGON'::text) OR (the_geom IS NULL))),
                                  CONSTRAINT distributiondata_the_geom_check3 CHECK ((st_srid(the_geom) = 4326))
);




--
-- TOC entry 413 (class 1259 OID 4316571)
-- Name: distributionshapes; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE distributionshapes (
                                    id serial NOT NULL,
                                    the_geom geometry,
                                    name character varying(256),
                                    pid character varying,
                                    area_km double precision,
                                    the_geom_orig geometry
);


--
-- TOC entry 374 (class 1259 OID 2017703)
-- Name: fields; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE fields (
                        name character varying(256),
                        id character varying(20) NOT NULL,
                        "desc" character varying(256),
                        type character(1),
                        spid character varying(256),
                        sid character varying(256),
                        sname character varying(256),
                        sdesc character varying(256),
                        indb boolean DEFAULT false,
                        enabled boolean DEFAULT false,
                        last_update timestamp without time zone DEFAULT now(),
                        namesearch boolean DEFAULT false,
                        defaultlayer boolean,
                        "intersect" boolean DEFAULT false,
                        layerbranch boolean DEFAULT false,
                        analysis boolean DEFAULT true,
                        addtomap boolean DEFAULT true
);


--
-- TOC entry 377 (class 1259 OID 2017726)
-- Name: layerpids; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE layerpids (
                           id character varying(256),
                           type integer,
                           "unique" character varying(255),
                           path character varying(500),
                           pid character varying(256),
                           metadata character varying(500),
                           the_geom geometry
);


--
-- TOC entry 3567 (class 0 OID 0)
-- Dependencies: 377
-- Name: TABLE layerpids; Type: COMMENT; Schema: public; Owner: -
--

COMMENT ON TABLE layerpids IS 'Values for "type" column are:
1 = Contextual
2 = Environmental
3 = Checklist
4 = Shapefile
5 = Distribution';


--
-- TOC entry 378 (class 1259 OID 2017732)
-- Name: layers; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE layers (
                        id integer DEFAULT nextval('layers_id_seq'::regclass) NOT NULL,
                        name character varying(150),
                        description text,
                        type character varying(20),
                        source character varying(150),
                        path character varying(500),
                        extents character varying(100),
                        minlatitude numeric(18,5),
                        minlongitude numeric(18,5),
                        maxlatitude numeric(18,5),
                        maxlongitude numeric(18,5),
                        notes text,
                        enabled boolean,
                        displayname character varying(150),
                        displaypath character varying(500),
                        scale character varying(20),
                        environmentalvaluemin character varying(30),
                        environmentalvaluemax character varying(30),
                        environmentalvalueunits character varying(150),
                        lookuptablepath character varying(300),
                        metadatapath character varying(300),
                        classification1 character varying(150),
                        classification2 character varying(150),
                        uid character varying(50),
                        mddatest character varying(30),
                        citation_date character varying(30),
                        datalang character varying(5),
                        mdhrlv character varying(5),
                        respparty_role character varying(30),
                        licence_level character varying,
                        licence_link character varying(300),
                        licence_notes character varying(1024),
                        source_link character varying(300),
                        path_orig character varying,
                        path_1km character varying(256),
                        path_250m character varying(256),
                        pid character varying,
                        keywords character varying,
                        domain character varying(100),
                        dt_added timestamp without time zone DEFAULT now()
);


--
-- TOC entry 384 (class 1259 OID 2017763)
-- Name: obj_names; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE obj_names (
                           id serial NOT NULL,
                           name character varying
);


--
-- TOC entry 386 (class 1259 OID 2017772)
-- Name: objects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE objects (
                         pid character varying(256) NOT NULL,
                         id character varying(256) DEFAULT nextval('objects_id_seq'::regclass),
                         "desc" character varying(256),
                         name character varying,
                         fid character varying(8),
                         the_geom geometry,
                         name_id integer,
                         namesearch boolean DEFAULT false,
                         bbox character varying(200),
                         area_km double precision
);


--
-- TOC entry 454 (class 1259 OID 5444422)
-- Name: points_of_interest; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE points_of_interest (
                                    id integer NOT NULL,
                                    object_id character varying(256),
                                    name character varying NOT NULL,
                                    type character varying NOT NULL,
                                    latitude double precision NOT NULL,
                                    longitude double precision NOT NULL,
                                    bearing double precision,
                                    user_id character varying,
                                    description character varying,
                                    focal_length_millimetres double precision,
                                    the_geom geometry
);


--
-- TOC entry 453 (class 1259 OID 5444420)
-- Name: points_of_interest_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE points_of_interest_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 3568 (class 0 OID 0)
-- Dependencies: 453
-- Name: points_of_interest_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE points_of_interest_id_seq OWNED BY points_of_interest.id;


--
-- TOC entry 388 (class 1259 OID 2017786)
-- Name: tabulation; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE tabulation (
                            fid1 character varying,
                            pid1 character varying,
                            fid2 character varying,
                            pid2 character varying,
                            the_geom geometry,
                            area double precision,
                            occurrences integer DEFAULT 0,
                            species integer DEFAULT 0,
                            speciest1 integer DEFAULT 0,
                            speciest2 integer DEFAULT 0
);


--
-- TOC entry 458 (class 1259 OID 5446192)
-- Name: ud_data_x; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ud_data_x (
                           ud_header_id integer,
                           ref character varying(256),
                           data_type character varying(20),
                           data bytea
);


--
-- TOC entry 457 (class 1259 OID 5446181)
-- Name: ud_header; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE ud_header (
                           upload_dt timestamp without time zone,
                           lastuse_dt timestamp without time zone,
                           user_id character varying(256),
                           analysis_id character varying(256),
                           metadata text,
                           description character varying(256),
                           ud_header_id serial NOT NULL,
                           data_size integer,
                           record_type character varying(20),
                           mark_for_deletion_dt timestamp without time zone,
                           data_path character varying(256)
);


--
-- TOC entry 452 (class 1259 OID 5440972)
-- Name: uploaded_objects_metadata; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE uploaded_objects_metadata (
                                           pid character varying(256) NOT NULL,
                                           user_id text,
                                           time_last_updated timestamp with time zone,
                                           id integer NOT NULL
);


--
-- TOC entry 451 (class 1259 OID 5440970)
-- Name: uploaded_objects_metadata_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE uploaded_objects_metadata_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 3569 (class 0 OID 0)
-- Dependencies: 451
-- Name: uploaded_objects_metadata_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE uploaded_objects_metadata_id_seq OWNED BY uploaded_objects_metadata.id;


--
-- TOC entry 3406 (class 2604 OID 5444425)
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY points_of_interest ALTER COLUMN id SET DEFAULT nextval('points_of_interest_id_seq'::regclass);


--
-- TOC entry 3405 (class 2604 OID 5440975)
-- Name: id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY uploaded_objects_metadata ALTER COLUMN id SET DEFAULT nextval('uploaded_objects_metadata_id_seq'::regclass);


--
-- TOC entry 3432 (class 2606 OID 4316555)
-- Name: distributiondata_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY distributiondata
    ADD CONSTRAINT distributiondata_pkey PRIMARY KEY (gid);


--
-- TOC entry 3434 (class 2606 OID 4316557)
-- Name: distributiondata_spcode_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY distributiondata
    ADD CONSTRAINT distributiondata_spcode_key UNIQUE (spcode);


--
-- TOC entry 3439 (class 2606 OID 4323944)
-- Name: distributionshapes_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY distributionshapes
    ADD CONSTRAINT distributionshapes_pkey PRIMARY KEY (id);


--
-- TOC entry 3415 (class 2606 OID 2709689)
-- Name: obj_names_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY obj_names
    ADD CONSTRAINT obj_names_id_pk PRIMARY KEY (id);


--
-- TOC entry 3417 (class 2606 OID 2709691)
-- Name: obj_names_name_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY obj_names
    ADD CONSTRAINT obj_names_name_unique UNIQUE (name);


--
-- TOC entry 3423 (class 2606 OID 2709693)
-- Name: objects_pid_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY objects
    ADD CONSTRAINT objects_pid_pk PRIMARY KEY (pid);


--
-- TOC entry 3411 (class 2606 OID 3626751)
-- Name: pk_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY fields
    ADD CONSTRAINT pk_id PRIMARY KEY (id);


--
-- TOC entry 3413 (class 2606 OID 2709697)
-- Name: pk_layers_id; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY layers
    ADD CONSTRAINT pk_layers_id PRIMARY KEY (id);


--
-- TOC entry 3443 (class 2606 OID 5444430)
-- Name: pk_points_of_interest; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY points_of_interest
    ADD CONSTRAINT pk_points_of_interest PRIMARY KEY (id);


--
-- TOC entry 3441 (class 2606 OID 5440980)
-- Name: pk_uploaded_objects_metadata; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY uploaded_objects_metadata
    ADD CONSTRAINT pk_uploaded_objects_metadata PRIMARY KEY (id);


--
-- TOC entry 3429 (class 2606 OID 4073323)
-- Name: tabulation_unqiue_constraint; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY tabulation
    ADD CONSTRAINT tabulation_unqiue_constraint UNIQUE (fid1, pid1, fid2, pid2);


--
-- TOC entry 3445 (class 2606 OID 5446189)
-- Name: ud_header_id_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ud_header
    ADD CONSTRAINT ud_header_id_pk PRIMARY KEY (ud_header_id);


--
-- TOC entry 3447 (class 2606 OID 5446191)
-- Name: ud_header_unique; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ud_header
    ADD CONSTRAINT ud_header_unique UNIQUE (user_id, analysis_id);


--
-- TOC entry 3430 (class 1259 OID 4316558)
-- Name: distributiondata_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX distributiondata_idx ON distributiondata USING btree (geom_idx);


--
-- TOC entry 3437 (class 1259 OID 4365714)
-- Name: distributiondata_the_geom; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX distributiondata_the_geom ON distributionshapes USING gist (the_geom);


--
-- TOC entry 3435 (class 1259 OID 4316559)
-- Name: distributiondata_the_geom_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX distributiondata_the_geom_idx ON distributiondata USING gist (the_geom);


--
-- TOC entry 3409 (class 1259 OID 3626749)
-- Name: idx_fields_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_fields_id ON fields USING btree (id);


--
-- TOC entry 3418 (class 1259 OID 5468709)
-- Name: idx_objects_name_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_objects_name_id ON objects USING btree (name_id);


--
-- TOC entry 3419 (class 1259 OID 4088824)
-- Name: objects_fid_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX objects_fid_idx ON objects USING btree (fid);


--
-- TOC entry 3420 (class 1259 OID 2709774)
-- Name: objects_geom_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX objects_geom_idx ON objects USING gist (the_geom);


--
-- TOC entry 3421 (class 1259 OID 5468710)
-- Name: objects_namesearch_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX objects_namesearch_idx ON objects USING btree (namesearch);


--
-- TOC entry 3424 (class 1259 OID 2709776)
-- Name: tabulation_fid1_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tabulation_fid1_idx ON tabulation USING btree (fid1);


--
-- TOC entry 3425 (class 1259 OID 2709777)
-- Name: tabulation_fid2_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tabulation_fid2_idx ON tabulation USING btree (fid2);


--
-- TOC entry 3426 (class 1259 OID 2709778)
-- Name: tabulation_pid1_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tabulation_pid1_idx ON tabulation USING btree (pid1);


--
-- TOC entry 3427 (class 1259 OID 2709779)
-- Name: tabulation_pid2_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX tabulation_pid2_idx ON tabulation USING btree (pid2);

--
-- TOC entry 3473 (class 2606 OID 5444431)
-- Name: fk_points_of_interest_object_pid; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY points_of_interest
    ADD CONSTRAINT fk_points_of_interest_object_pid FOREIGN KEY (object_id) REFERENCES objects(pid);


--
-- TOC entry 3472 (class 2606 OID 5440981)
-- Name: fk_uploaded_objects_metadata; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY uploaded_objects_metadata
    ADD CONSTRAINT fk_uploaded_objects_metadata FOREIGN KEY (pid) REFERENCES objects(pid);


--
-- TOC entry 3474 (class 2606 OID 5446198)
-- Name: ud_data_x_header_id; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY ud_data_x
    ADD CONSTRAINT ud_data_x_header_id FOREIGN KEY (ud_header_id) REFERENCES ud_header(ud_header_id);


-- Completed on 2015-01-21 12:30:50 AEST

--
-- PostgreSQL database dump complete
--

CREATE TABLE distributions AS
SELECT d.gid, d.spcode, d.scientific, d.authority_, d.common_nam, d.family, d.genus_name,
       d.specific_n, d.min_depth, d.max_depth, d.pelagic_fl, d.estuarine_fl, d.coastal_fl, d.desmersal_fl,
       d.metadata_u, d.wmsurl, d.lsid, d.family_lsid, d.genus_lsid, d.caab_species_number, d.caab_family_number,
       o.the_geom, o.name AS area_name, o.pid, d.type, d.checklist_name, o.area_km, d.notes, d.geom_idx, d.group_name,
       d.genus_exemplar, d.family_exemplar, d.data_resource_uid, d.image_quality, d.bounding_box, d.endemic
FROM distributionshapes o
         JOIN distributiondata d ON d.geom_idx = o.id;

CREATE INDEX distributions_spcode_idx ON distributions(spcode);
CREATE INDEX distributions_scientific_idx ON distributions(scientific);
CREATE INDEX distributions_family_idx ON distributions(family);
CREATE INDEX distributions_genus_name_idx ON distributions(genus_name);
CREATE INDEX distributions_min_depth_idx ON distributions(min_depth);
CREATE INDEX distributions_max_depth_idx ON distributions(max_depth);
CREATE INDEX distributions_pelagic_fl_idx ON distributions(pelagic_fl);
CREATE INDEX distributions_estuarine_fl_idx ON distributions(estuarine_fl);
CREATE INDEX distributions_coastal_fl_idx ON distributions(coastal_fl);
CREATE INDEX distributions_desmersal_fl_idx ON distributions(desmersal_fl);
CREATE INDEX distributions_metadata_u_idx ON distributions(metadata_u);
CREATE INDEX distributions_lsid_idx ON distributions(lsid);
CREATE INDEX distributions_family_lsid_idx ON distributions(family_lsid);
CREATE INDEX distributions_genus_lsid_idx ON distributions(genus_lsid);
CREATE INDEX distributions_caab_species_number_idx ON distributions(caab_species_number);
CREATE INDEX distributions_caab_family_number_idx ON distributions(caab_family_number);
CREATE INDEX distributions_type_idx ON distributions(type);
CREATE INDEX distributions_checklist_name_idx ON distributions(checklist_name);
CREATE INDEX distributions_group_name_idx ON distributions(group_name);
CREATE INDEX distributions_genus_exemplar_idx ON distributions(genus_exemplar);
CREATE INDEX distributions_family_exemplar_idx ON distributions(family_exemplar);
CREATE INDEX distributions_data_resource_uid_idx ON distributions(data_resource_uid);
CREATE INDEX distributions_image_quality ON distributions(image_quality);
CREATE INDEX distributions_geom ON distributions USING GIST (the_geom);

INSERT INTO fields (name, id, type, "desc", enabled, spid, indb, defaultlayer, namesearch, "intersect", layerbranch, analysis, addtomap)
VALUES ('User Uploaded Objects', 'cl1083', 'e', '', true, '1083', false, false, false, false, false, false, false);
