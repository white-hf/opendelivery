package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.hf.easydelivery.common.exception.BizException;

final class DeliveryAreaGeoJson {
    private DeliveryAreaGeoJson() {}

    static String normalize(ObjectMapper mapper, JsonNode input) {
        if (input == null || input.isNull()) invalid("GeoJSON is required");
        if ("FeatureCollection".equals(input.path("type").asText())) return normalizeFeatureCollection(mapper,input);
        JsonNode geometry = "Feature".equals(input.path("type").asText()) ? input.path("geometry") : input;
        String type = geometry.path("type").asText();
        JsonNode coordinates = geometry.path("coordinates");
        if ((!"Polygon".equals(type) && !"MultiPolygon".equals(type)) || !coordinates.isArray() || coordinates.isEmpty()) {
            invalid("GeoJSON must contain a non-empty Polygon or MultiPolygon");
        }
        ObjectNode normalized = mapper.createObjectNode();
        normalized.put("type", "MultiPolygon");
        if ("Polygon".equals(type)) normalized.putArray("coordinates").add(coordinates);
        else normalized.set("coordinates", coordinates.deepCopy());
        try { return mapper.writeValueAsString(normalized); }
        catch (Exception ex) { throw new BizException("AREA.GEOJSON.INVALID", "GeoJSON cannot be serialized"); }
    }

    private static String normalizeFeatureCollection(ObjectMapper mapper,JsonNode input) {
        JsonNode features=input.path("features");
        if(!features.isArray()||features.isEmpty()) invalid("FeatureCollection must contain at least one polygon feature");
        ArrayNode polygons=mapper.createArrayNode();
        for(JsonNode feature:features) {
            if(!"Feature".equals(feature.path("type").asText())) invalid("FeatureCollection contains an invalid feature");
            JsonNode geometry=feature.path("geometry");
            String type=geometry.path("type").asText();
            JsonNode coordinates=geometry.path("coordinates");
            if(!"Polygon".equals(type)&&!"MultiPolygon".equals(type)) continue;
            if(!coordinates.isArray()||coordinates.isEmpty()) invalid("FeatureCollection contains an empty polygon geometry");
            if("Polygon".equals(type)) polygons.add(coordinates);
            else coordinates.forEach(polygons::add);
        }
        if(polygons.isEmpty()) invalid("FeatureCollection must contain at least one Polygon or MultiPolygon feature");
        ObjectNode normalized=mapper.createObjectNode();
        normalized.put("type","MultiPolygon");
        normalized.set("coordinates",polygons);
        try{return mapper.writeValueAsString(normalized);}
        catch(Exception ex){throw new BizException("AREA.GEOJSON.INVALID","GeoJSON cannot be serialized");}
    }

    private static void invalid(String message) { throw new BizException("AREA.GEOJSON.INVALID", message); }
}
