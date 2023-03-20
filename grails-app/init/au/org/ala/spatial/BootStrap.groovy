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

        //create database required by layers-store
        try {
            def rs = groovySql.rows("SELECT * FROM fields WHERE id = ?", [spatialConfig.userObjectsField] as List<Object>)
            if (rs.size() == 0) {
                groovySql.execute("INSERT INTO fields (id, name, \"desc\", type, indb, enabled, namesearch) VALUES " +
                        "('${spatialConfig.userObjectsField}', 'user', '', 'c', false, false, false);")
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("duplicate key value")) {
                log.error("Error ", e)
            }
        }

        //create missing azimuth function from st_azimuth
        try {
            groovySql.execute('CREATE OR REPLACE FUNCTION azimuth (anyelement, anyelement) returns double precision language sql as $$ select st_azimuth($1, $2) $$')
        } catch (Exception e) {
            log.error("Error creating missing azimuth function frmo st_azimuth", e)
        }

        //create objects name idx if it is missing
        try {
            def rs = groovySql.rows("SELECT * FROM pg_class WHERE relname = 'objects_name_idx';")
            if (rs.size() == 0) {
                groovySql.execute("CREATE EXTENSION IF NOT EXISTS pg_trgm;")
                groovySql.execute("CREATE INDEX objects_name_idx ON objects USING gin (name gin_trgm_ops) WHERE namesearch is true;")
            }
        } catch (Exception e) {
            if (!e.getMessage().contains("already exists")) {
                log.error("Error ", e)
            }
        }
    }

    def destroy = {
    }
}
