package au.org.ala.spatial.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
class SandboxIngress {
    String id; // id of the uploaded file that is one of DwCA, CSV, TSV
    String description; // user provided description of the file
    String [] headers; // when isDwCA==false this corresponds to DwCA meta.xml for occurrences.txt
    String userId; // user id of the person who uploaded the file
    Boolean isDwCA; // true if the file is a DwCA
    String dataResourceUid; // dataResourceUid as it is loaded into SOLR
    String status; // status of the import process
    String message; // status message of the import process
    String statusUrl; // url to check the status of the import process
    Integer requestId; // id of the request
}
