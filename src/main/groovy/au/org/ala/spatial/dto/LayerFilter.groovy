package au.org.ala.spatial.dto
/**
 * container for layer filter;
 * <p/>
 * - includes minimum and maximum for environmental layers
 * - includes catagory names and indexes of selected for contextual layers
 *
 * @author adam
 */
//@CompileStatic
class LayerFilter implements Serializable {

    static final long serialVersionUID = -2733856402542621244L
    /**
     * layer name
     */
    String layername = ""
    /**
     * for environmental layers
     * <p/>
     * filter minimum
     */
    double minimum_value = 0
    /**
     * for environmental layers
     * <p/>
     * filter maximum
     */
    double maximum_value = 0
    /**
     * for contextual layers
     * <p/>
     * list of layer pids
     */
    String[] ids = null

    /**
     * Construct a new filter for environmental layers
     *
     * @param layername name of this layer as String.  See layersdb.fields.id
     * @param min minimum bound as double.
     * @param max maximum bound as double.
     */
    LayerFilter(String layername, double min, double max) {
        this.layername = layername
        this.minimum_value = min
        this.maximum_value = max
    }

    /**
     * Construct a new filter for contextual layers
     *
     * @param layername name of this layer as String.  See layersdb.fields.id
     * @param ids String array containing layersdb.objects.id values for shapes in this layer
     */
    LayerFilter(String layername, String[] ids) {
        this.layername = layername
        this.ids = ids
    }

    /**
     * parse a string into an array of LayerFilters.
     * <p/>
     * e.g. "ENVELOPE(el600,1,4:el801,9.6,20.3)" or "el600,1,4:el801,9.6,20.3"
     * and for contextual layers "ENVELOPE(cl904,pid1,pid2,pid3)"
     *
     * @param s String to parse.
     * @return array of filters as LayerFilter[].
     */
    static LayerFilter[] parseLayerFilters(String s) {
        if (s.toUpperCase().startsWith("ENVELOPE(")) {
            s = s.substring("ENVELOPE(".length(), s.length() - 1) //remove 'envelope(..)' wrapper
        }

        String[] terms = s.split(':')

        LayerFilter[] lf = new LayerFilter[terms.length]

        int i = 0
        for (String t : terms) {
            lf[i] = parseLayerFilter(t)
            i++
        }

        return lf
    }

    /**
     * Parse a single filter term into a new LayerFilter.
     * <p/>
     * e.g. "el600,1,4" or "ENVELOPE(el600,1,4)"
     *
     * @param s term to parse.
     * @return filter as LayerFilter.
     */
    static LayerFilter parseLayerFilter(String s) {
        if (s.toUpperCase().startsWith("ENVELOPE(")) {
            s = s.substring("ENVELOPE(".length(), s.length() - 1) //remove 'envelope(..)' wrapper
        }

        String[] tokens = s.split(",")

        if (tokens[0].startsWith("el")) {
            return new LayerFilter(tokens[0], Double.parseDouble(tokens[1]), Double.parseDouble(tokens[2]))
        } else {
            //contextual, get ids
            String[] ids = new String[tokens.length - 1]
            System.arraycopy(tokens, 1, ids, 0, ids.length)
            return new LayerFilter(tokens[0], ids)
        }
    }

    /**
     * gets layer name
     *
     * @return String
     */
    String getLayername() {
        return layername
    }

    /**
     * for environmental
     * <p/>
     * gets minimum applied
     *
     * @return
     */
    double getMinimum_value() {
        return minimum_value
    }

    /**
     * for environmental
     * <p/>
     * gets maximum applied
     *
     * @return
     */
    double getMaximum_value() {
        return maximum_value
    }

    /**
     * for contextual
     * <p/>
     * get layer objects pids.  see layersdb.objects.id
     *
     * @return
     */
    String[] getIds() {
        return ids
    }

    /**
     * Environmental ONLY
     * <p/>
     * test if a layer value is within the LayerFilter range.
     *
     * @param value number to test as double.
     * @return true if the value is in the filter range.
     */
    boolean isValid(double value) {
        //correct for data type conversions
        double correction = 0.0000001

        return !Double.isNaN(value) && value + correction >= minimum_value && value - correction <= maximum_value
    }

    boolean isContextual() {
        return layername.startsWith("cl")
    }
}
