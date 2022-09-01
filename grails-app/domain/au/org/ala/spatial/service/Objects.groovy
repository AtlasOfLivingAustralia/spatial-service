package au.org.ala.spatial.service

class Objects {
     String id;
     String pid;
     String desc;
     String name;
     String fid;
     String fieldname;
     String geometry;
     int name_id;
     String bbox;
     Double area_km;

     static transients = [ "degrees", "distance", "wmsurl", "featureType", "centroid" ]
     Double degrees;
     Double distance;
     String wmsurl;
     String featureType;
     String centroid;
}
