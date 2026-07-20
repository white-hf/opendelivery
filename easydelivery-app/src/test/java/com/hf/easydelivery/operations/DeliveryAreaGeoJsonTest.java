package com.hf.easydelivery.operations;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hf.easydelivery.common.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DeliveryAreaGeoJsonTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void normalizesPolygonFeatureToMultiPolygon() throws Exception {
        var input=mapper.readTree("""
                {"type":"Feature","properties":{"name":"DT"},"geometry":{"type":"Polygon",
                "coordinates":[[[-63.61,44.63],[-63.55,44.63],[-63.55,44.68],[-63.61,44.63]]]}}
                """);
        var result=mapper.readTree(DeliveryAreaGeoJson.normalize(mapper,input));
        assertEquals("MultiPolygon",result.path("type").asText());
        assertEquals(1,result.path("coordinates").size());
    }

    @Test
    void preservesMultiPolygonCoordinates() throws Exception {
        var input=mapper.readTree("{\"type\":\"MultiPolygon\",\"coordinates\":[[[[-63,44],[-62,44],[-63,44]]]]}");
        var result=mapper.readTree(DeliveryAreaGeoJson.normalize(mapper,input));
        assertEquals(input.path("coordinates"),result.path("coordinates"));
    }

    @Test
    void rejectsLinesAndEmptyGeometry() throws Exception {
        BizException line=assertThrows(BizException.class,()->DeliveryAreaGeoJson.normalize(mapper,
                mapper.readTree("{\"type\":\"LineString\",\"coordinates\":[[-63,44],[-62,44]]}")));
        assertEquals("AREA.GEOJSON.INVALID",line.getBizCode());
        assertThrows(BizException.class,()->DeliveryAreaGeoJson.normalize(mapper,
                mapper.readTree("{\"type\":\"Polygon\",\"coordinates\":[]}")));
    }
}
