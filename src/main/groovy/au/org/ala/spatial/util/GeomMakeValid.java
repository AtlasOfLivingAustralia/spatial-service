/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.util;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.operation.polygonize.Polygonizer;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.FeatureReader;
import org.geotools.data.Transaction;
import org.geotools.data.collection.ListFeatureCollection;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import java.io.File;
import java.io.Serializable;
import java.util.*;

public class GeomMakeValid {

    public static void makeValidShapefile(String inputShapefile, String outputShapefile) {
        try {
            File file = new File(inputShapefile);
            ShapefileDataStore sds = new ShapefileDataStore(file.toURI().toURL());
            FeatureReader reader = sds.getFeatureReader();

            List<SimpleFeature> features = new ArrayList();

            while (reader.hasNext()) {
                SimpleFeature f = (SimpleFeature) reader.next();
                Geometry g = (Geometry) f.getDefaultGeometry();
                if (g != null && !g.isValid() &&
                        (g instanceof Polygon || g instanceof MultiPolygon)) {
                    g = makeValid((Geometry) g.clone());
                    if (g != null && g.isValid()) {
                        f.setDefaultGeometry(g);
                    } else {
                        //error
                        System.out.println("ERROR: unable to make geometry valid, " + inputShapefile + ", " + features.size());
                    }
                }

                features.add(f);
            }
            final SimpleFeatureType TYPE = sds.getSchema();

            reader.close();
            sds.dispose();
            
            /*
         * Get an output file name and create the new shapefile
         */
            File newFile = new File(outputShapefile);
            ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();
            Map<String, Serializable> params = new HashMap<String, Serializable>();
            params.put("url", newFile.toURI().toURL());
            params.put("create spatial index", Boolean.TRUE);
            ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory.createDataStore(params);
            
        /*
         * TYPE is used as a template to describe the file contents
         */

            newDataStore.createSchema(TYPE);
            
            /*
         * Write the features to the shapefile
         */
            Transaction transaction = new DefaultTransaction("create");

            String typeName = newDataStore.getTypeNames()[0];
            SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);
            SimpleFeatureType SHAPE_TYPE = featureSource.getSchema();
        /*
         * The Shapefile format has a couple limitations:
         * - "the_geom" is always first, and used for the geometry attribute name
         * - "the_geom" must be of type Point, MultiPoint, MuiltiLineString, MultiPolygon
         * - Attribute names are limited in length 
         * - Not all data types are supported (example Timestamp represented as Date)
         * 
         * Each data store has different limitations so check the resulting SimpleFeatureType.
         */
            System.out.println("SHAPE:" + SHAPE_TYPE);

            if (featureSource instanceof SimpleFeatureStore) {
                SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
            /*
             * SimpleFeatureStore has a method to add features from a
             * SimpleFeatureCollection object, so we use the ListFeatureCollection
             * class to wrap our list of features.
             */
                SimpleFeatureCollection collection = new ListFeatureCollection(TYPE, features);
                featureStore.setTransaction(transaction);
                try {
                    featureStore.addFeatures(collection);
                    transaction.commit();
                } catch (Exception problem) {
                    problem.printStackTrace();
                    transaction.rollback();
                } finally {
                    transaction.close();
                }
            } else {
                System.out.println(typeName + " does not support read/write access");
            }


        } catch (Exception e) {
            e.printStackTrace(System.out);
        }
    }

    /**********************************************************************
     * $Id: lwgeom_geos.c 5258 2010-02-17 21:02:49Z strk $
     * <p/>
     * PostGIS - Spatial Types for PostgreSQL
     * http://postgis.net
     * <p/>
     * Copyright 2009-2010 Sandro Santilli <strk@keybit.net>
     * <p/>
     * This is free software; you can redistribute and/or modify it under
     * the terms of the GNU General Public Licence. See the COPYING file.
     * <p/>
     * *********************************************************************
     * <p/>
     * ST_MakeValid
     * <p/>
     * Attempts to make an invalid geometries valid w/out losing
     * points.
     * <p/>
     * Polygons may become lines or points or a collection of
     * polygons lines and points (collapsed ring cases).
     * <p/>
     * Author: Sandro Santilli <strk@keybit.net>
     * <p/>
     * Work done for Faunalia (http://www.faunalia.it) with fundings
     * from Regione Toscana - Sistema Informativo per il Governo
     * del Territorio e dell'Ambiente (RT-SIGTA).
     * <p/>
     * Thanks to Dr. Horst Duester for previous work on a plpgsql version
     * of the cleanup logic [1]
     * <p/>
     * Thanks to Andrea Peri for recommandations on constraints.
     * <p/>
     * [1] http://www.sogis1.so.ch/sogis/dl/postgis/cleanGeometry.sql
     **********************************************************************/


    /*
     * We expect initGEOS being called already.
     * Will return NULL on error (expect error handler being called by then)
     *
     */
    public static Geometry makeValid(Geometry gin) {
        Geometry gout = null;
        Geometry geos_bound;
        Geometry geos_cut_edges, geos_area, collapse_points;
        Geometry[] vgeoms = new Geometry[3]; /* One for area, one for cut-edges */
        int nvgeoms = 0;

        geos_bound = gin.getBoundary();
        if (geos_bound == null) {
            return null;
        }

        /* Use noded boundaries as initial "cut" edges */

        geos_cut_edges = LWGEOM_GEOS_nodeLines(geos_bound);
        if (geos_cut_edges == null) {
            return null;
        }

        /* NOTE: the noding process may drop lines collapsing to points.
         *       We want to retrive any of those */
        {
            Geometry pi;
            Geometry po;

            pi = GEOSGeom_extractUniquePoints(geos_bound);

            po = GEOSGeom_extractUniquePoints(geos_cut_edges);

            collapse_points = pi.difference(po);
        }

	    /* And use an empty geometry as initial "area" */
        geos_area = null;

	/*
     * See if an area can be build with the remaining edges
	 * and if it can, symdifference with the original area.
	 * Iterate this until no more polygons can be created
	 * with left-over edges.
	 */
        while (geos_cut_edges.getNumPoints() > 0) {
            Geometry new_area;
            Geometry new_area_bound;
            Geometry symdif;
            Geometry new_cut_edges;
		/*
		 * ASSUMPTION: cut_edges should already be fully noded
		 */

            new_area = LWGEOM_GEOS_buildArea(geos_cut_edges);
            if (new_area == null)   /* must be an exception */ {
                //use what is already built
                break;
                //return null;
            }

            if (new_area.getArea() == 0) {
			    /* no more rings can be build with thes edges */
                break;
            }

		/*
		 * We succeeded in building a ring !
		 */

		/*
		 * Save the new ring boundaries first (to compute
		 * further cut edges later)
		 */
            new_area_bound = new_area.getBoundary();
            //Geometry new_area_bound_polygon = JTSFactoryFinder.getGeometryFactory().createPolygon(new_area_bound.getCoordinates());
            if (new_area_bound == null || new_area.getArea() == 0) {
			/* We did check for empty area already so
			 * this must be some other error */
                return null;
            }

		/*
		 * Now symdif new and old area
		 */
            if (geos_area == null) {
                symdif = new_area;
            } else {
                symdif = geos_area.symDifference(new_area);
            }
            if (symdif == null)   /* must be an exception */ {
                return null;
            }

            geos_area = symdif;
            symdif = null;

		/*
		 * Now let's re-set geos_cut_edges with what's left
		 * from the original boundary.
		 * ASSUMPTION: only the previous cut-edges can be
		 *             left, so we don't need to reconsider
		 *             the whole original boundaries
		 *
		 * NOTE: this is an expensive operation.
		 *
		 */
            //new_cut_edges = GEOSDifference(geos_cut_edges, new_area_bound);
            new_cut_edges = ((Geometry) geos_cut_edges.clone()).difference(new_area_bound);
            if (new_cut_edges == null)   /* an exception ? */ {
                return null;
            }
            geos_cut_edges = new_cut_edges;
        }

        if (geos_area == null) {
            return null;
        }
        if (geos_area.getNumPoints() > 0) {
            vgeoms[nvgeoms++] = geos_area;
        }

        if (nvgeoms == 1) {
		/* Return cut edges */
            gout = vgeoms[0];
        } else {
		/* Collect areas and lines (if any line) */
            gout = JTSFactoryFinder.getGeometryFactory().createGeometryCollection(new Geometry[]{vgeoms[0]});
            //gout = createMultipolygon(vgeoms, nvgeoms);
        }

        return gout;

    }

    static Geometry LWGEOM_GEOS_nodeLines(Geometry lines) {
        Geometry noded;
        Geometry point;

         /*
         * Union with first geometry point, obtaining full noding
         * and dissolving of duplicated repeated points
         *
         * TODO: substitute this with UnaryUnion?
         */

        Coordinate c = lines.getCoordinates()[0];
        point = JTSFactoryFinder.getGeometryFactory().createPoint(c);
        if (point == null) {
            return null;
        }

        noded = lines.union(point);

        return noded;
    }

    static Geometry GEOSGeom_extractUniquePoints(Geometry g) {
        Set<Coordinate> filtered = new HashSet();

        Coordinate[] coords = g.getCoordinates();
        for (int i = 0; i < coords.length; i++) {
            filtered.add(coords[i]);
        }

        Coordinate[] array = new Coordinate[filtered.size()];
        filtered.toArray(array);
        return JTSFactoryFinder.getGeometryFactory().createMultiPoint(array);
    }

    static Geometry GEOSDifference(Geometry g1, Geometry g2) {
        return g1.difference(g2);
    }

    static Geometry LWGEOM_GEOS_buildArea(Geometry geom_in) {
        Geometry tmp;
        Geometry geos_result;
        Geometry shp;
        int i;
        int ngeoms;
        int srid = geom_in.getSRID();

        Face[] geoms;

        Polygonizer polygonizer = new Polygonizer();
        polygonizer.add((Geometry) geom_in.clone());
        Collection c = polygonizer.getPolygons();
        Geometry[] gc = new Geometry[c.size()];
        i = 0;
        for (Object o : c) {
            gc[i] = (Geometry) o;
            i++;
        }
        geos_result = JTSFactoryFinder.getGeometryFactory().createGeometryCollection(gc);

        if (geos_result.getNumGeometries() == 0) return null;

  /*
   * We should now have a collection
   */

        ngeoms = geos_result.getNumGeometries();

  /*
   * No geometries in collection, early out
   */
        if (ngeoms == 0) {
            geos_result.setSRID(srid);
            return geos_result;
        }

  /*
   * Return first geometry if we only have one in collection,
   * to avoid the unnecessary Geometry clone below.
   */
        if (ngeoms == 1) {
            tmp = geos_result.getGeometryN(0);
            if (tmp.getNumPoints() == 0) {
                return null;
            }
            shp = (Geometry) tmp.clone();
            shp.setSRID(srid);
            return shp;
        }

  /*
   * Polygonizer returns a polygon for each face in the built topology.
   *
   * This means that for any face with holes we'll have other faces
   * representing each hole. We can imagine a parent-child relationship
   * between these faces.
   *
   * In order to maximize the number of visible rings in output we
   * only use those faces which have an even number of parents.
   *
   * Example:
   *
   *   +---------------+
   *   |     L0        |  L0 has no parents
   *   |  +---------+  |
   *   |  |   L1    |  |  L1 is an hole of L0
   *   |  |  +---+  |  |
   *   |  |  |L2 |  |  |  L2 is an hole of L1 (which is an hole of L0)
   *   |  |  |   |  |  |
   *   |  |  +---+  |  |
   *   |  +---------+  |
   *   |               |
   *   +---------------+
   *
   * See http://trac.osgeo.org/postgis/ticket/1806
   *
   */


        geoms = new Face[ngeoms];
        for (i = 0; i < ngeoms; ++i) {
            geoms[i] = newFace(geos_result.getGeometryN(i));
        }


  /* Find faces representing other faces holes */
        findFaceHoles(geoms);


  /* Build a MultiPolygon composed only by faces with an
   * even number of ancestors */
        tmp = collectFacesWithEvenAncestors(geoms);

  /* Run a single overlay operation to dissolve shared edges */
        shp = tmp.union();
        if (shp.getNumGeometries() == 0) {
            return null;
        }

        shp.setSRID(srid);

        return shp;
    }

    static Face newFace(Geometry g) {
        Face f = new Face();
        f.geom = g;
        f.env = f.geom.getEnvelope();
        f.envarea = f.geom.getArea();
        f.parent = null;
        return f;
    }

    static class Face {
        Geometry geom;
        Geometry env;
        double envarea;
        Face parent;

        public Face() {
        }
    }

    static void findFaceHoles(Face[] faces) {
        int i, j, h;

  /* We sort by envelope area so that we know holes are only
   * after their shells */
        Arrays.sort(faces, new Comparator<Face>() {

            @Override
            public int compare(Face f1, Face f2) {
                if (f1.envarea > f2.envarea) {
                    return -1;
                } else if (f1.envarea < f2.envarea) {
                    return 1;
                }
                return 0;
            }
        });

        for (i = 0; i < faces.length; ++i) {
            Face f = faces[i];
            int nholes = -1;
            if (f.geom instanceof Polygon) {
                nholes = ((Polygon) f.geom).getNumInteriorRing();
            }

            for (h = 0; h < nholes; ++h) {
                Geometry hole = ((Polygon) f.geom).getInteriorRingN(h);

                for (j = i + 1; j < faces.length; ++j) {
                    Geometry f2er;
                    Face f2 = faces[j];

                    if (f2.parent != null) continue; /* hole already assigned */

                    f2er = ((Polygon) f2.geom).getExteriorRing();

        /* TODO: can be optimized as the ring would have the
         *       same vertices, possibly in different order.
         *       maybe comparing number of points could already be
         *       useful.
         */
                    if (f2er.equals(hole)) {
                        f2.parent = f;
                        break;
                    }
                }
            }
        }
    }

    static Geometry collectFacesWithEvenAncestors(Face[] faces) {
        Geometry[] geoms = new Geometry[faces.length];
        Geometry ret = null;
        int ngeoms = 0;
        int i;

        for (i = 0; i < faces.length; ++i) {
            Face f = faces[i];
            if (countParens(f) % 2 != 0) continue; /* we skip odd parents geoms */
            geoms[ngeoms++] = (Geometry) f.geom.clone();
        }

        if (ngeoms > 0) {
            Geometry[] newgeoms = new Geometry[ngeoms];
            System.arraycopy(geoms, 0, newgeoms, 0, ngeoms);

            ret = JTSFactoryFinder.getGeometryFactory().createGeometryCollection(newgeoms);
        }

        return ret;
    }

    static int countParens(Face f) {
        int pcount = 0;
        while (f.parent != null) {
            ++pcount;
            f = f.parent;
        }
        return pcount;
    }

}
