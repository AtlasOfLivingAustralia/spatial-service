/**
 * ************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia All Rights Reserved.
 * <p/>
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at http://www.mozilla.org/MPL/
 * <p/>
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License for
 * the specific language governing rights and limitations under the License.
 * *************************************************************************
 */
package au.org.ala.spatial.layers

import org.codehaus.jackson.map.annotate.JsonSerialize

import java.text.SimpleDateFormat

/**
 * Used to reference SitesBySpeciesTabulated requests.
 *
 * @author Adam
 */
@JsonSerialize(include = JsonSerialize.Inclusion.ALWAYS)
import groovy.transform.CompileStatic
//@CompileStatic
class SxS {

    String value
    String analysisId
    String status

    SxS(String value, String analysisId, String status) {
        this.value = value
        this.analysisId = analysisId
        this.status = status
    }

    String getAnalysisId() {
        return analysisId
    }

    void setAnalysisId(String analysisId) {
        this.analysisId = analysisId
    }

    String getValue() {
        return value
    }

    void setValue(String value) {
        this.value = value
    }

    String getStatus() {
        return status
    }

    void setStatus(String status) {
        this.status = status
    }

    String getDateTime() {
        String date = ""
        try {
            date = new SimpleDateFormat("dd/MM/yyyy hh:mm:SS").format(new Date(Long.valueOf(analysisId)))
        } catch (Exception e) {
        }
        return date
    }
}
