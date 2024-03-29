/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package au.org.ala.spatial.legend
/**
 * @author Adam
 */
//@CompileStatic
class Facet implements Serializable {

    String field
    String value
    String[] valueArray
    String parameter
    double min
    double max
    boolean includeRange
    Facet[] orInAndTerms
    Facet[] andTerms
    Facet[] orTerms

    Facet(String field, String value, boolean includeRange) {
        this.field = field
        this.value = value
        this.includeRange = includeRange
        this.parameter = null
        this.min = Double.NaN
        this.max = Double.NaN
        this.valueArray = null
        if (this.value != null) {
            this.valueArray = new String[]{this.value}
        }
    }

    Facet(String field, double min, double max, boolean includeRange) {
        this.field = field
        this.min = min
        this.max = max
        this.includeRange = includeRange

        String strMin = Double.isInfinite(min) ? "*" : (min == (int) min) ? String.format("%d", (int) min) : String.valueOf(min)
        String strMax = Double.isInfinite(max) ? "*" : (max == (int) max) ? String.format("%d", (int) max) : String.valueOf(max)

        this.value = "[" + strMin + " TO " + strMax + "]"

        this.valueArray = null

        this.parameter = (includeRange ? "" : '-') + this.field + ':' + this.value
    }

    Facet(String field, String strMin, String strMax, boolean includeRange) {
        this.field = field

        if (field == "occurrence_year") {
            strMin = strMin.replace("-12-31T00:00:00Z", "").replace("-01-01T00:00:00Z", "")
            strMax = strMax.replace("-12-31T00:00:00Z", "").replace("-01-01T00:00:00Z", "")
        }
       double[] d = [
               strMin == "*" ? Double.NEGATIVE_INFINITY : Double.parseDouble(strMin),
               strMax == "*" ? Double.POSITIVE_INFINITY : Double.parseDouble(strMax)
        ]
        this.min = d[0]
        this.max = d[1]
        this.includeRange = includeRange

        if (field == "occurrence_year") {
            if (strMin != "*") {
                this.value = "[" + strMin + "-01-01T00:00:00Z TO "
            } else {
                this.value = "[" + strMin + " TO "
            }
            if (strMax != "*") {
                this.value += strMax + "-12-31T00:00:00Z]"
            } else {
                this.value += strMax + "]"
            }
        } else {
            this.value = "[" + strMin + " TO " + strMax + "]"
        }

        this.valueArray = null

        this.parameter = (includeRange ? "" : '-') + this.field + ':' + this.value
    }

    Facet(String fq, Facet[] orInAndTerms, Facet[] andTerms, Facet[] orTerms) {
        //make toString work
        parameter = fq

        //make isValid and getFields work
        this.orInAndTerms = orInAndTerms
        this.andTerms = andTerms
        this.orTerms = orTerms
    }

    /**
     * Parse a facet created by webportal:
     * <p/>
     * Classification legend queries
     * <p/>
     * (1) <field>:"<value>" OR <field>:"<value>"
     * (2) -<field>:*
     * (3) -(<field>:* AND -<field>:"<value>" AND -<field>:"<value>")
     * <p/>
     * (4) <field>:[<min> TO <max>] OR <field>:[<min> TO <max>]
     * (5) -<field>:[* TO *]
     * (6) -(<field>:[* TO *] AND -<field>:[<min> TO <max>] AND -<field>:[<min> TO <max>])
     * <p/>
     * <p/>
     * Environmental Envelope queries
     * <p/>
     * (7) <field>:[<min> TO <max>] AND <field>:[<min> TO <max>]
     * <p/>
     * <p/>
     * Scatterplot queries
     * <p/>
     * (8) <field>:[<min> TO <max>] AND <field>:[<min> TO <max>]
     * (9) -(<field>:[* TO *] AND <field>:[* TO *])
     * (10) -(-(<field>:[<min> TO <max>] AND <field>:[<min> TO <max>]) AND <field>:[* TO *] AND <field>:[* TO *])
     * <p/>
     * <p/>
     * (11) -(<field>:[<min> TO <max>] AND <field>:[<min> TO <max>])
     * (12) <field>:[* TO *] AND <field>:[* TO *]
     * (13) (-<field>:[<min> TO <max>] OR -<field>:[<min> TO <max>]) AND <field>:[* TO *] AND <field>:[* TO *]
     *
     * @param fq facet to parse as String
     * @return
     */
    static Facet parseFacet(String fq) {
        if (fq == null || fq.length() < 3) {
            return null

        }

        //tests
        boolean hasAnd = fq.contains(" AND ")
        boolean hasOr = fq.contains(" OR ")

        if (fq.startsWith("-(-(")) {
            // (10) -(-(<field>:[<min> TO <max>] AND <field>:[<min> TO <max>]) AND <field>:[* TO *] AND <field>:[* TO *])
            int p = fq.indexOf(')')

            //reverse sign to convert first inner AND into OR
            Facet[] orPart = parseTerms(" AND ", fq.substring(4, p), true)
            Facet[] andPart = parseTerms(" AND ", fq.substring(p + 6, fq.length() - 1), false)

            return new Facet(fq, orPart, andPart, null)
        } else if (fq.startsWith("-(") && !fq.endsWith(")") && !hasOr) {
            //(13) -(<field>:[<min> TO <max>] AND <field>:[<min> TO <max>]) AND <field>:[* TO *] AND <field>:[* TO *]
            int p = fq.indexOf(')')

            //reverse sign to convert first inner AND into OR
            Facet[] orPart = parseTerms(" AND ", fq.substring(2, p), true)
            Facet[] andPart = parseTerms(" AND ", fq.substring(p + 6, fq.length() - 1), false)
            return new Facet(fq, orPart, andPart, null)
        } else {//if((hasAnd != hasOr) || (!hasAnd && !hasOr)) {
            //(1) (2) (3) (4) (5) (6) (7) (8) (9) (11) (12)
            boolean invert = fq.charAt(0) == '-' as char && fq.charAt(1) == '(' as char
            String s = invert ? fq.substring(2, fq.length() - 1) : fq
            Facet[] f = parseTerms((hasAnd ? " AND " : " OR "), s, invert)

            if (f.length == 1) {
                return f[0]
            } else {
                if (invert) {
                    return new Facet(fq, (hasAnd ? f : null), (hasOr ? f : null), null)
                } else {
                    return new Facet(fq, (hasOr ? f : null), (hasAnd ? f : null), null)
                }
            }
        }

        //return null;
    }

    static Facet[] parseTerms(String separator, String fq, boolean invert) {
        String[] terms = fq.split(separator)
        Facet[] facets = new Facet[terms.length]
        for (int i = 0; i < terms.length; i++) {
            String ff = terms[i]
            int offset = ff.startsWith('-') ? 1 : 0
            String f = ff.substring(offset, ff.indexOf(':'))
            String v = ff.substring(ff.indexOf(':') + 1)
            if (v.charAt(0) == '\"' as char || v.charAt(0) == '*' as char || !v.toUpperCase().contains(" TO ")) {
                //value
                if (v.charAt(0) == '\"' as char ) {
                    v = v.substring(1, v.length() - 1)
                }
                facets[i] = new Facet(f, v, invert != (offset == 0))
            } else {
                //range
                String[] n = v.toUpperCase().substring(1, v.length() - 1).split(" TO ")
                facets[i] = new Facet(f, n[0], n[1], invert != (offset == 0))
            }
        }

        return facets
    }

    @Override
    String toString() {
        String facet = ""
        if (parameter == null) {
            if ((value.startsWith("\"") && value.endsWith("\"")) || value == "*") {
                facet = (includeRange ? "" : '-') + field + ':' + value
            } else {
                try {
                    facet = (includeRange ? "" : '-') + field + ':\"' + value + '\"'
                } catch (Exception ignored) {
                }
            }

            if (field == "occurrence_year") {
                if (facet.contains("* TO *")) {
                    //don't need to change anything
                } else if (facet.contains("* TO ")) {
                    facet = facet.replace("]", "-12-31T00:00:00Z]")
                } else if (facet.contains(" TO *")) {
                    facet = facet.replace(" TO ", "-01-01T00:00:00Z TO ")
                } else {
                    facet = facet.replace(" TO ", "-01-01T00:00:00Z TO ").replace("]", "-12-31T00:00:00Z]")
                }

            } else if (field == "occurrence_year_decade" || field == "decade") {
                if (value.contains("before")) {
                    facet = (includeRange ? "" : '-') + field + ":[* TO 1849-12-31T00:00:00Z]"
                } else {
                    String yr = value.replace("\"", "")
                    yr = yr.substring(0, yr.length() - 1)
                    facet = (includeRange ? "" : '-') + field + ":[" + yr + "0-01-01T00:00:00Z TO " + yr + "9-12-31T00:00:00Z]"
                }
                facet = facet.replace("_decade", "")
            }
        } else {
            facet = parameter

            if (!facet.contains("-01-01T00:00:00Z")
                    && !facet.contains("-12-31T00:00:00Z")
                    && facet.contains("occurrence_year")) {
                if (field == "occurrence_year") {
                    if (parameter.contains("* TO *")) {
                        //dont' need to change anything
                    } else if (parameter.contains("* TO ")) {
                        parameter = parameter.replace("]", "-12-31T00:00:00Z]")
                    } else if (parameter.contains(" TO *")) {
                        parameter = parameter.replace(" TO ", "-01-01T00:00:00Z TO ")
                    } else {
                        parameter = parameter.replace(" TO ", "-01-01T00:00:00Z TO ").replace("]", "-12-31T00:00:00Z]")
                    }
                } else if (field == "occurrence_year_decade" || field == "decade") {
                    //TODO: make this work
                }
            }
        }

        return facet
    }

    String[] getFields() {
        Set<String> fieldSet = new HashSet<String>()
        if (field != null) {
            fieldSet.add(field)
        }
        if (orInAndTerms != null) {
            for (Facet f : orInAndTerms) {
                Collections.addAll(fieldSet, f.getFields())
            }
        }
        if (andTerms != null) {
            for (Facet f : andTerms) {
                Collections.addAll(fieldSet, f.getFields())
            }
        }
        if (orTerms != null) {
            for (Facet f : orTerms) {
                Collections.addAll(fieldSet, f.getFields())
            }
        }

        String[] fields = new String[fieldSet.size()]
        fieldSet.toArray(fields)
        return fields
    }

    boolean isValid(String v) {
        if (getType() == 1) {
            for (int i = 0; i < valueArray.length; i++) {
                if (valueArray[i] == v || (v.length() != 0 && valueArray[i] == "*")) {
                    return includeRange
                }
            }
            return !includeRange
        } else if (getType() == 0) {
            try {
                double d = Double.parseDouble(v)
                boolean inside = d >= min && d <= max
                return includeRange == inside
            } finally {
                return false
            }
        } else {
            boolean state = true
            if (orInAndTerms != null) {
                state = sumTermTests(orInAndTerms, v) > 0
            }
            if (andTerms != null) {
                if (!state) {
                    //state = false;
                } else {
                    state = sumTermTests(andTerms, v) == andTerms.length
                }
            }
            if (orTerms != null) {
                if (state) {
                    return true
                } else {
                    return sumTermTests(orTerms, v) > 0
                }
            } else {
                return state
            }
        }
    }

    boolean isValid(double d) {
        if (getType() == 1) {
            String v = String.valueOf(d)
            if (Double.isNaN(d)) {
                v = ""
            }
            for (int i = 0; i < valueArray.length; i++) {
                if (valueArray[i] == v || (v.length() != 0 && valueArray[i] == "*")) {
                    return includeRange
                }
            }

            return !includeRange
        } else if (getType() == 0) {
            try {
                boolean inside = d >= min && d <= max
                return includeRange == inside
            } finally {
                return false
            }
        } else {
            boolean state = true
            if (orInAndTerms != null) {
                state = sumTermTests(orInAndTerms, d) > 0
            }
            if (andTerms != null) {
                if (state) {
                    state = sumTermTests(andTerms, d) == andTerms.length
                }
            }
            if (orTerms != null) {
                if (state) {
                    return true
                } else {
                    return sumTermTests(orTerms, d) > 0
                }
            } else {
                return state
            }
        }
    }

    /**
     * Facet type
     * 0 = numeric
     * 1 = string
     * 2 = group
     *
     * @return
     */
    int getType() {
        if (orInAndTerms != null || andTerms != null || orTerms != null) {
            return 2
        } else if (valueArray != null) {
            return 1
        } else {
            return 0
        }
    }

    boolean isValid(List<QueryField> fields, int record) {
        if (getType() == 2) {
            boolean state = true
            if (orInAndTerms != null) {
                state = sumTermTests(orInAndTerms, fields, record) > 0
            }
            if (andTerms != null) {
                if (!state) {
                    //state = false;
                } else {
                    state = sumTermTests(andTerms, fields, record) == andTerms.length
                }
            }
            if (orTerms != null) {
                if (state) {
                    return true
                } else {
                    return sumTermTests(orTerms, fields, record) > 0
                }
            } else {
                return state
            }
        } else {
            for (QueryField qf : fields) {
                if (qf.getName() == field) {
                    if (getType() == 1) {
                        return isValid(qf.getAsString(record))
                    } else {    //type == 0
                        switch (qf.getFieldType()) {
                            case QueryField.FieldType.DOUBLE:
                                return isValid(qf.getDouble(record))
                            case QueryField.FieldType.FLOAT:
                                return isValid(qf.getFloat(record))
                            case QueryField.FieldType.LONG:
                                return isValid((double) qf.getLong(record))
                            case QueryField.FieldType.INT:
                                return isValid(qf.getInt(record))
                            default:
                                return isValid(qf.getAsString(record))
                        }
                    }
                }
            }
            //if field not found, treat as outside of any specified range
            return !includeRange
        }
    }

    private static int sumTermTests(Facet[] andTerms, List<QueryField> fields, int record) {
        int sum = 0
        for (int i = 0; andTerms != null && i < andTerms.length; i++) {
            if (andTerms[i].isValid(fields, record)) {
                sum++
            }
        }
        return sum
    }

    private static int sumTermTests(Facet[] andTerms, String value) {
        int sum = 0
        for (int i = 0; andTerms != null && i < andTerms.length; i++) {
            if (andTerms[i].isValid(value)) {
                sum++
            }
        }
        return sum
    }

    private static int sumTermTests(Facet[] andTerms, double value) {
        int sum = 0
        for (int i = 0; andTerms != null && i < andTerms.length; i++) {
            if (andTerms[i].isValid(value)) {
                sum++
            }
        }
        return sum
    }

    double getMin() {
        if (getType() == 0) {
            return min
        } else if (getType() == 1) {
            return Float.NaN
        } else {
            double min = Double.POSITIVE_INFINITY
            if (orInAndTerms != null) {
                for (int i = 0; i < orInAndTerms.length; i++) {
                    double newMin = orInAndTerms[i].getMin()
                    if (orInAndTerms[i].includeRange && newMin < min) {
                        min = newMin
                    }
                }
            }
            if (andTerms != null) {
                for (int i = 0; i < andTerms.length; i++) {
                    double newMin = andTerms[i].getMin()
                    if (andTerms[i].includeRange && newMin < min) {
                        min = newMin
                    }
                }
            }
            if (orTerms != null) {
                for (int i = 0; i < orTerms.length; i++) {
                    double newMin = orTerms[i].getMin()
                    if (orTerms[i].includeRange && newMin < min) {
                        min = newMin
                    }
                }
            }
            return min
        }
    }

    double getMax() {
        if (getType() == 0) {
            return max
        } else if (getType() == 1) {
            return Float.NaN
        } else {
            double max = Double.NEGATIVE_INFINITY
            if (orInAndTerms != null) {
                for (int i = 0; i < orInAndTerms.length; i++) {
                    double newMax = orInAndTerms[i].getMax()
                    if (orInAndTerms[i].includeRange && newMax > max) {
                        max = newMax
                    }
                }
            }
            if (andTerms != null) {
                for (int i = 0; i < andTerms.length; i++) {
                    double newMax = andTerms[i].getMax()
                    if (andTerms[i].includeRange && newMax > max) {
                        max = newMax
                    }
                }
            }
            if (orTerms != null) {
                for (int i = 0; i < orTerms.length; i++) {
                    double newMax = orTerms[i].getMax()
                    if (orTerms[i].includeRange && newMax > max) {
                        max = newMax
                    }
                }
            }
            return max
        }
    }
}
