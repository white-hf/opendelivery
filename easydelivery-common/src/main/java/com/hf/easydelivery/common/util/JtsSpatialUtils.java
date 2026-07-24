package com.hf.easydelivery.common.util;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.locationtech.jts.io.geojson.GeoJsonReader;

/**
 * JTS Spatial Utility for performing in-memory Point-In-Polygon and geometry validation.
 * Offloads spatial computation from MySQL ST_Contains / ST_Intersects to JVM memory.
 */
public class JtsSpatialUtils {

    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory(new PrecisionModel(), 4326);

    /**
     * Checks if a GeoJSON polygon contains the given longitude and latitude coordinates.
     */
    public static boolean contains(String geoJsonSnapshot, double lng, double lat) {
        if (geoJsonSnapshot == null || geoJsonSnapshot.isBlank()) {
            return false;
        }
        try {
            GeoJsonReader reader = new GeoJsonReader(GEOMETRY_FACTORY);
            Geometry polygon = reader.read(geoJsonSnapshot);
            Point point = GEOMETRY_FACTORY.createPoint(new Coordinate(lng, lat));
            return polygon.contains(point) || polygon.intersects(point);
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Validates if the given GeoJSON geometry is a valid Polygon.
     */
    public static boolean isValidGeoJson(String geoJsonSnapshot) {
        if (geoJsonSnapshot == null || geoJsonSnapshot.isBlank()) {
            return false;
        }
        try {
            GeoJsonReader reader = new GeoJsonReader(GEOMETRY_FACTORY);
            Geometry geometry = reader.read(geoJsonSnapshot);
            return geometry != null && geometry.isValid();
        } catch (Exception ex) {
            return false;
        }
    }
}
