package au.org.ala.spatial

import au.org.ala.spatial.dto.ProcessSpecification
import au.org.ala.spatial.dto.SearchObject
import au.org.ala.spatial.dto.Tabulation
import au.org.ala.userdetails.UserDetailsClient
import au.org.ala.ws.security.ApiKeyClient
import au.org.ala.ws.security.authenticator.AlaApiKeyAuthenticator
import au.org.ala.ws.security.client.AlaApiKeyClient
import au.org.ala.ws.security.client.AlaDirectClient
import grails.converters.JSON
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection

@Slf4j
class BootStrap {

    def dataSource
    def messageSource
    SpatialConfig spatialConfig

    UserDetailsClient userDetailsClient
    def getAlaApiKeyClient

    def init = { servletContext ->
        messageSource.setBasenames(
                "file:///var/opt/atlas/i18n/spatial-service/messages",
                "file:///opt/atlas/i18n/spatial-service/messages",
                "WEB-INF/grails-app/i18n/messages",
                "classpath:messages"
        )

        [Geometry, GeometryCollection].each {
            JSON.registerObjectMarshaller(it) {
                it?.toString()
            }
        }

        // domain marshalling
        [Fields, Distributions, InputParameter, Layers, Log, OutputParameter, SpatialObjects, Task].each {
            JSON.registerObjectMarshaller(it) {
                def id = it.id ?: it.pid ?: it.spcode
                def result = it.properties.findAll { it.key != 'class' && it.key != 'version' && it.value != null }
                if (id) {
                    result += [id: id]
                }
                if (it instanceof Layers && result.containsKey("dt_added")) {
                    result += [dt_added: ((Layers) it).dt_added.time]
                }
                result
            }
        }

        [Tabulation, SearchObject].each {
            JSON.registerObjectMarshaller(it) {
                it.properties.findAll { it.key != 'class' && it.key != 'version' && it.value != null }
            }
        }

        // ProcessSpecification class marshalling
        [ProcessSpecification,
         ProcessSpecification.ConstraintSpecification,
         ProcessSpecification.InputSpecification,
         ProcessSpecification.OutputSpecification].each {
            JSON.registerObjectMarshaller(it) {
                it.properties.findAll { it.key != 'class' && it.key != 'version' &&
                        it.value != null && it.key != 'declaringClass' && it.key != 'privateSpecification'}
            }
        }

        // ProcessSpecification enum marshalling
        [ProcessSpecification.OutputType, ProcessSpecification.InputType,
        ProcessSpecification.SelectionType].each {
            JSON.registerObjectMarshaller(it) {
                it.toString().toLowerCase()
            }
        }

        //create database required by layers-store
        if (Fields.get(spatialConfig.userObjectsField) == null) {
            Fields field = new Fields()
            field.id = spatialConfig.userObjectsField
            field.name = 'user'
            field.desc = ''
            field.type = 'c'
            field.indb = false
            field.enabled = false
            field.namesearch = false
            Fields.withTransaction {
                if (!field.save()) {
                    field.errors {
                        log.error(it)
                    }
                }
            }
        }

//        //create missing azimuth function from st_azimuth
//        try {
//            groovySql.execute('CREATE OR REPLACE FUNCTION azimuth (anyelement, anyelement) returns double precision language sql as $$ select st_azimuth($1, $2) $$')
//        } catch (Exception e) {
//            log.error("Error creating missing azimuth function frmo st_azimuth", e)
//        }

        def groovySql = Sql.newInstance(dataSource)

        // manual db modification
        String [] dbModificationSql = [
                // tables only accessed with SQL
                createTabulationTable,
                "CREATE INDEX tabulation_fid1_idx ON tabulation USING btree (fid1);",
                "CREATE INDEX tabulation_fid2_idx ON tabulation USING btree (fid2);",
                "CREATE INDEX tabulation_pid1_idx ON tabulation USING btree (pid1);",
                "CREATE INDEX tabulation_pid2_idx ON tabulation USING btree (pid2);",

                // sequences for manual object creation
                "CREATE SEQUENCE objects_id_seq INCREMENT 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1;",
                "CREATE SEQUENCE uploaded_objects_metadata_id_seq INCREMENT 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1;",

                // index for fast searching of objects.name
                "CREATE EXTENSION IF NOT EXISTS pg_trgm;",
                "CREATE INDEX objects_name_idx ON objects USING gin (name gin_trgm_ops) WHERE namesearch is true;",

                // geometry column indexes
                "CREATE INDEX objects_geom_idx ON objects USING gist (the_geom);",
                "CREATE INDEX distributions_geom ON distributions USING GIST (the_geom);",
                "CREATE INDEX tabulation_geom ON tabulation USING GIST (the_geom);",

                // objects.spid sequence used in manual sql that creates objects
                "CREATE SEQUENCE objects_id_seq INCREMENT 1 MINVALUE 1 MAXVALUE 9223372036854775807 START 1 CACHE 1;",

                // compound constraint
                "ALTER TABLE ONLY ud_header ADD CONSTRAINT ud_header_unique UNIQUE (user_id, analysis_id);",
        ].each { String sql ->
            try {
                groovySql.execute(sql)
            } catch (Exception ignored) {}
        }
    }

    def destroy = {
    }

    final String createTabulationTable = "CREATE TABLE tabulation\n" +
            "(\n" +
            "    fid1        character varying,\n" +
            "    pid1        character varying,\n" +
            "    fid2        character varying,\n" +
            "    pid2        character varying,\n" +
            "    the_geom    geometry,\n" +
            "    area        double precision,\n" +
            "    occurrences integer DEFAULT 0,\n" +
            "    species     integer DEFAULT 0,\n" +
            "    speciest1   integer DEFAULT 0,\n" +
            "    speciest2   integer DEFAULT 0\n" +
            ");"
}
