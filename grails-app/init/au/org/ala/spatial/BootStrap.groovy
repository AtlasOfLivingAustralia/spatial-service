package au.org.ala.spatial


import grails.converters.JSON
import groovy.sql.Sql
import groovy.util.logging.Slf4j
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.geom.GeometryCollection

@Slf4j
//@CompileStatic
class BootStrap {

    Sql groovySql
    def messageSource
    SpatialConfig spatialConfig

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

        [Fields, Distributions, InputParameter, Layers, Log, OutputParameter, SpatialObjects, Tabulation, Task].each {
            JSON.registerObjectMarshaller(it) {
                it.properties.findAll { it.key != 'class' && it.key != 'version' && it.value != null } + [id: it.id]
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
//
        //create objects name idx if it is missing
        try {
            groovySql.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;")
            groovySql.execute("CREATE INDEX objects_name_idx ON objects USING gin (name gin_trgm_ops) WHERE namesearch is true;")
        } catch (Exception ignored) {}
        try {
            groovySql.execute("CREATE SEQUENCE objects_id_seq\n" +
                    "    INCREMENT 1\n" +
                    "    MINVALUE 1\n" +
                    "    MAXVALUE 9223372036854775807\n" +
                    "    START 1\n" +
                    "    CACHE 1;")
        } catch (Exception ignored) {}
    }

    def destroy = {
    }
}
