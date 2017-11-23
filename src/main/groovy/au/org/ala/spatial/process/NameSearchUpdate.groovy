/*
 * Copyright (C) 2016 Atlas of Living Australia
 * All Rights Reserved.
 *
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 */

package au.org.ala.spatial.process

import groovy.util.logging.Slf4j
import org.apache.commons.io.FileUtils

@Slf4j
class NameSearchUpdate extends SlaveProcess {

    void start() {
        //run namesearch update query
        String sql = "DELETE FROM obj_names; INSERT INTO obj_names (name)" +
                "  SELECT lower(objects.name) FROM fields INNER JOIN objects ON fields.id = objects.fid " +
                "  LEFT OUTER JOIN obj_names ON lower(objects.name)=obj_names.name" +
                "  WHERE obj_names.name IS NULL AND fields.namesearch = true" +
                "  GROUP BY lower(objects.name);" +
                "  UPDATE objects SET name_id=obj_names.id FROM obj_names WHERE " +
                "  lower(objects.name)=obj_names.name;";

        FileUtils.writeStringToFile(new File(getTaskPath() + 'update.sql'), sql)

        addOutput('sql', 'update.sql')
    }
}
