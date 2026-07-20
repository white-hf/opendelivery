package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hf.easydelivery.common.exception.BizException;

final class DeliveryAreaGeoJson {
    private DeliveryAreaGeoJson() {}

    static String normalize(ObjectMapper mapper, JsonNode input) {
        if (input == null || input.isNull()) invalid("GeoJSON is required");
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

    private static void invalid(String message) { throw new BizException("AREA.GEOJSON.INVALID", message); }
}
