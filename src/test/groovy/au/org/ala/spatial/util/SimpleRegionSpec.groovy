package au.org.ala.spatial.util

import au.org.ala.layers.intersect.SimpleRegion;
import au.org.ala.layers.intersect.SimpleShapeFile;



class SimpleRegionTest extends GroovyTestCase {

    void testInAustralia() {
        SimpleRegion region  = SimpleShapeFile.parseWKT("POLYGON((110.0 -45.0,165.0 -45.0,165.0 -10.0,110.0 -10.0,110.0 -45.0))");
        assertTrue(region.isWithin_EPSG900913(112.45, -43))
    }

    void testInWorld1() {
        SimpleRegion region  = SimpleShapeFile.parseWKT("POLYGON((-180 -89,180 -89,180 89,-180 89,-180 -89))");
        assertTrue(region.isWithin_EPSG900913(112.45, -43))
    }
    void testInWorld() {
        SimpleRegion region  = SimpleShapeFile.parseWKT("POLYGON((-180 -90,180 -90,180 90,-180 90,-180 -90))");
        assertTrue(region.isWithin_EPSG900913(112.45, -43))
    }


}