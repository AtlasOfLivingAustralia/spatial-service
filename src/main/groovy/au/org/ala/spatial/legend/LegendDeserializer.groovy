package au.org.ala.spatial.legend

import groovy.transform.CompileStatic
import tools.jackson.core.JsonParser
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.ValueDeserializer

/**
 * Created by a on 24/03/2014.
 */
@CompileStatic
class LegendDeserializer extends ValueDeserializer<Legend> {
    @Override
    Legend deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        ObjectMapper om = new ObjectMapper()

        JsonNode node = om.readTree(jsonParser)

        return null
    }
}
