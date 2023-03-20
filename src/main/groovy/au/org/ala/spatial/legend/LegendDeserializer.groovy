package au.org.ala.spatial.legend

import org.codehaus.jackson.JsonNode
import org.codehaus.jackson.JsonParser
import org.codehaus.jackson.ObjectCodec
import org.codehaus.jackson.map.DeserializationContext
import org.codehaus.jackson.map.JsonDeserializer
import org.codehaus.jackson.map.ObjectMapper

/**
 * Created by a on 24/03/2014.
 */
//@CompileStatic
class LegendDeserializer extends JsonDeserializer<Legend> {
    @Override
    Legend deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        ObjectMapper om = new ObjectMapper()

        ObjectCodec oc = jsonParser.getCodec()
        JsonNode node = oc.readTree(jsonParser)

        return null
    }
}
