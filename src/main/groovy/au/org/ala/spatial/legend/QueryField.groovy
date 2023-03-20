/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.spatial.legend

import org.codehaus.jackson.JsonParser
import org.codehaus.jackson.map.DeserializationContext
import org.codehaus.jackson.map.JsonDeserializer
import org.codehaus.jackson.map.annotate.JsonDeserialize

/**
 * @author Adam
 */
//@CompileStatic
class QueryField implements Serializable {
    /**
     * The sub group that the query field belongs to.  Allows items to be group under common headings
     */
    @JsonDeserialize(using = GroupTypeDeserializer.class)
    GroupType group = GroupType.CUSTOM
    String name
    String displayName
    boolean store
    ArrayList<String> tmpData = new ArrayList<String>()
    @JsonDeserialize(using = FieldTypeDeserializer.class)
    FieldType fieldType = FieldType.AUTO
    long[] longData = null
    int[] intData = null
    String[] stringData = null
    float[] floatData = null
    double[] doubleData = null
    int[] stringCounts = null
    LegendObject legend

    QueryField() {
        //for json deserializer
    }

    QueryField(String name) {
        this.name = name
        this.displayName = name
        store = false
        this.fieldType = FieldType.AUTO
    }


    QueryField(String name, FieldType fieldType) {
        this.name = name
        this.displayName = name
        store = false
        this.fieldType = fieldType
    }

    QueryField(String name, String displayName, FieldType fieldType) {
        this.name = name
        this.displayName = displayName
        store = false
        this.fieldType = fieldType
    }

    QueryField(String name, String displayName, GroupType group, FieldType fieldType) {
        this.name = name
        this.displayName = displayName
        this.group = group
        store = false
        this.fieldType = fieldType
    }

    QueryField(String name, String displayName, GroupType group, FieldType fieldType, boolean store) {
        this.name = name
        this.displayName = displayName
        this.group = group
        this.store = store
        this.fieldType = fieldType
    }

    boolean isStored() {
        return store
    }

    void setStored(boolean store) {
        this.store = store
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    String getDisplayName() {
        return displayName
    }

    void setDisplayName(String displayName) {
        this.displayName = displayName
    }

    void ensureCapacity(int size) {
        if (tmpData != null) {
            tmpData.ensureCapacity(size)
        }
    }

    void add(String s) {
        if (tmpData != null) {
            if (s == null) {
                s = ""
            } else {
                s = s.trim()
            }
            tmpData.add(s)
        }
    }

    void store() {
        if (tmpData == null) {
            return
        }
        if (fieldType == FieldType.AUTO) {
            updateFieldType()
        }

        switch (fieldType) {
            case FieldType.INT:
                storeAsInt()
                break
            case FieldType.LONG:
                storeAsLong()
                break
            case FieldType.FLOAT:
                storeAsFloat()
                break
            case FieldType.DOUBLE:
                storeAsDouble()
                break
            default: //string
                storeAsString()
                break
        }

        tmpData = null
    }

    void storeAsInt() {
        intData = new int[tmpData.size()]
        for (int i = 0; i < intData.length; i++) {
            try {
                intData[i] = Integer.parseInt(tmpData.get(i))
            } catch (Exception ignored) {
                try {
                    intData[i] = (int) Double.parseDouble(tmpData.get(i))
                } catch (Exception ex) {
                    intData[i] = Integer.MIN_VALUE
                }
            }
        }
    }

    void storeAsLong() {
        longData = new long[tmpData.size()]
        for (int i = 0; i < longData.length; i++) {
            try {
                longData[i] = Long.parseLong(tmpData.get(i))
            } catch (Exception ignored) {
                try {
                    longData[i] = (long) Double.parseDouble(tmpData.get(i))
                } catch (Exception ex) {
                    longData[i] = Long.MIN_VALUE
                }
            }
        }
    }

    void storeAsFloat() {
        floatData = new float[tmpData.size()]
        for (int i = 0; i < floatData.length; i++) {
            try {
                floatData[i] = Float.parseFloat(tmpData.get(i))
            } catch (Exception ignored) {
                floatData[i] = Float.NaN
            }
        }
    }

    void storeAsDouble() {
        doubleData = new double[tmpData.size()]
        for (int i = 0; i < doubleData.length; i++) {
            try {
                doubleData[i] = Double.parseDouble(tmpData.get(i))
            } catch (Exception ignored) {
                doubleData[i] = Double.NaN
            }
        }
    }

    void storeAsString() {
        TreeSet<String> uniqueStrings = new TreeSet<String>()
        for (int i = 0; i < tmpData.size(); i++) {
            uniqueStrings.add(tmpData.get(i))
        }
        stringData = new String[uniqueStrings.size()]
        uniqueStrings.toArray(stringData)
        Arrays.sort(stringData)

        stringCounts = new int[stringData.length]
        intData = new int[tmpData.size()]
        for (int i = 0; i < tmpData.size(); i++) {
            int pos = Arrays.binarySearch(stringData, tmpData.get(i))
            intData[i] = pos
            stringCounts[pos]++
        }
    }

    private void updateFieldType() {
        if (tmpData == null) {
            return
        }
        int intCount = 0
        int longCount = 0
        int floatCount = 0
        int doubleCount = 0
        int stringCount = 0
        for (int i = 0; i < tmpData.size(); i++) {
            String s = tmpData.get(i)

            //substitution for sampling nulls
            if (s == "n/a") {
                s = null
            }

            if (s != null && s.length() > 0) {
                try {
                    Long.parseLong(s)
                    longCount++

                    Integer.parseInt(s)
                    intCount++
                } catch (Exception ignored) {
                }

                try {
                    Double.parseDouble(s)
                    doubleCount++

                    Float.parseFloat(s)
                    floatCount++
                } catch (Exception ignored) {
                }

                stringCount++
            }
        }

        FieldType determinedType

        if (stringCount <= 1 || (stringCount > longCount && stringCount > doubleCount)) {
            determinedType = FieldType.STRING
        } else if (doubleCount > longCount) {
            if (floatCount == doubleCount) {
                determinedType = FieldType.FLOAT
            } else {
                determinedType = FieldType.DOUBLE
            }
        } else {
            if (intCount == longCount) {
                determinedType = FieldType.INT
            } else {
                determinedType = FieldType.LONG
            }
        }

        fieldType = determinedType
    }


    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */

    @Override
    String toString() {
        return "QueryField [group=" + group + ", name=" + name + ", displayName=" + displayName + ", store=" + store + ", fieldType=" + fieldType + ", legend=" + legend + "]"
    }

    /**
     * @return the group
     */
    GroupType getGroup() {
        return group
    }

    void setGroup(GroupType group) {
        this.group = group
    }

    FieldType getFieldType() {
        return fieldType
    }

    void setFieldType(FieldType fieldType) {
        this.fieldType = fieldType
    }

    int getInt(int pos) {
        return intData[pos]
    }

    long getLong(int pos) {
        return longData[pos]
    }

    double getDouble(int pos) {
        return doubleData[pos]
    }

    float getFloat(int pos) {
        return floatData[pos]
    }

    String getString(int pos) {
        return stringData[intData[pos]]
    }

    String getAsString(int pos) {
        switch (fieldType) {
            case FieldType.INT:
                return String.valueOf((intData[pos] == Integer.MIN_VALUE) ? "n/a" : intData[pos])
            case FieldType.LONG:
                return String.valueOf((longData[pos] == Long.MIN_VALUE) ? "n/a" : longData[pos])
            case FieldType.FLOAT:
                return String.valueOf((Float.isNaN(floatData[pos])) ? "n/a" : floatData[pos])
            case FieldType.DOUBLE:
                return String.valueOf((Double.isNaN(doubleData[pos])) ? "n/a" : doubleData[pos])
            case FieldType.STRING:
                return stringData[intData[pos]]
            default:
                if (tmpData != null) {
                    return tmpData.get(pos)
                } else {
                    return null
                }
        }
    }

    void copyData(QueryField src) {
        fieldType = src.fieldType
        longData = src.longData
        intData = src.intData
        floatData = src.floatData
        doubleData = src.doubleData
        stringData = src.stringData
        stringCounts = src.stringCounts
        legend = src.legend
    }

    LegendObject getLegend() {
        if (legend == null) {
            legend = LegendBuilder.build(this)
        }

        return legend
    }

    void setLegend(LegendObject legend) {
        this.legend = legend
    }

    int getColour(int i) {
        getLegend() //builds legend if not yet built

        switch (fieldType) {
            case FieldType.INT:
                return legend.getColour(intData[i] == Integer.MIN_VALUE ? Float.NaN : intData[i])
            case FieldType.LONG:
                return legend.getColour(longData[i] == Long.MIN_VALUE ? Float.NaN : longData[i])
            case FieldType.FLOAT:
                return legend.getColour(Float.isNaN(floatData[i]) ? Float.NaN : floatData[i])
            case FieldType.DOUBLE:
                return legend.getColour((float) doubleData[i])
            case FieldType.STRING:
                return legend.getColour(stringData[intData[i]])
            default:
                return LegendObject.DEFAULT_COLOUR
        }
    }

    int getColourForValue(float value) {
        if (fieldType == FieldType.STRING) {
            return legend.getColour(String.valueOf(value))
        } else if (legend.numericLegend != null) {
            return legend.getColour(value)
        } else {
            return LegendObject.DEFAULT_COLOUR
        }
    }

    boolean isStore() {
        return store
    }

    void setStore(boolean store) {
        this.store = store
    }

    ArrayList<String> getTmpData() {
        return tmpData
    }

    void setTmpData(ArrayList<String> tmpData) {
        this.tmpData = tmpData
    }

    long[] getLongData() {
        return longData
    }

    void setLongData(long[] longData) {
        this.longData = longData
    }

    int[] getIntData() {
        return intData
    }

    void setIntData(int[] intData) {
        this.intData = intData
    }

    String[] getStringData() {
        return stringData
    }

    void setStringData(String[] stringData) {
        this.stringData = stringData
    }

    float[] getFloatData() {
        return floatData
    }

    void setFloatData(float[] floatData) {
        this.floatData = floatData
    }

    double[] getDoubleData() {
        return doubleData
    }

    void setDoubleData(double[] doubleData) {
        this.doubleData = doubleData
    }

    int[] getStringCounts() {
        return stringCounts
    }

    void setStringCounts(int[] stringCounts) {
        this.stringCounts = stringCounts
    }

    enum FieldType {

        LONG, INT, STRING, FLOAT, DOUBLE, AUTO;

        static FieldType fromString(String value) {
            for (FieldType ft : values()) {
                if (ft.name().equalsIgnoreCase(value)) {
                    return ft
                }
            }

            return AUTO
        }
    }

    enum GroupType {
        TAXONOMIC("Taxonomic", 1),
        GEOSPATIAL("Geospatial", 2),
        TEMPORAL("Temporal", 3),
        RECORD_DETAILS("Record details", 4),
        ATTRIBUTION("Attribution", 5),
        RECORD_ASSERTIONS("Record assertions", 6),
        CUSTOM("Custom", 0);
        private static final Map<String, GroupType> nameLookup = new HashMap<String, GroupType>()

        static {
            for (GroupType mt : EnumSet.allOf(GroupType.class)) {
                nameLookup.put(mt.name, mt)
            }
        }

        private final String name
        private final Integer order

        GroupType(String name, Integer order) {
            this.name = name
            this.order = order
        }

        static GroupType getGroupType(String group) {
            return nameLookup.get(group)
        }

        static GroupType fromString(String value) {
            for (GroupType gt : values()) {
                if (gt.name().equalsIgnoreCase(value)) {
                    return gt
                }
            }

            return CUSTOM
        }

        Integer getOrder() {
            return order
        }

        String getName() {
            return name
        }

    }

    /**
     * Orders the Query field based on the group and then supplied order.
     *
     * @author Natasha Carter (natasha.carter@csiro.au)
     */
class QueryFieldComparator implements Comparator<QueryField> {

        @Override
        int compare(QueryField qf1, QueryField qf2) {
            if (qf1.group == null || qf2.group == null) {
                return 0
            }
            return qf1.group.getOrder() <=> qf2.group.getOrder()
        }

    }
}

class GroupTypeDeserializer extends JsonDeserializer<QueryField.GroupType> {
    @Override
    QueryField.GroupType deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        return QueryField.GroupType.fromString(parser.getText())
    }
}

class FieldTypeDeserializer extends JsonDeserializer<QueryField.FieldType> {
    @Override
    QueryField.FieldType deserialize(JsonParser parser, DeserializationContext context)
            throws IOException {
        return QueryField.FieldType.fromString(parser.getText())
    }
}
