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
//@CompileStatic
class ScatterplotList extends ScatterplotCreate {

    void start() {
        super.start()

        File dir = new File(getTaskPath())
        for (File file : dir.listFiles()) {
            if (file.getName() == "scatterplot.html") addOutput("metadata", file.getName(), true)
            else if (file.getName().endsWith(".png")) addOutput('files', file.getName(), true)
        }
    }
}
