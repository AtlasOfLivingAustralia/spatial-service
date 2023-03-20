/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p/>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p/>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial.util


import au.org.ala.spatial.LayerIntersectService

import java.util.concurrent.LinkedBlockingQueue

/**
 * @author Adam
 */
//@CompileStatic
class BatchConsumer {
    static List<Thread> threads = new ArrayList<Thread>()
    static LinkedBlockingQueue<String> waitingBatchDirs

    synchronized static void start(LayerIntersectService layerIntersectDao, String batchDir, int numThreads) {
        if (threads.size() == 0) {
            waitingBatchDirs = new LinkedBlockingQueue<String>()

            int size = numThreads
            for (int i = 0; i < size; i++) {
                Thread t = new BatchConsumerThread(waitingBatchDirs, layerIntersectDao, batchDir)
                t.start()
                threads.add(t)
            }

            //get jobs that may have been interrupted but a shutdown
            File f = new File(batchDir)
            File[] files = f.listFiles()
            Arrays.sort(files)
            for (int i = 0; i < files.length; i++) {
                if (files[i].isDirectory()
                        && !(new File(files[i].getPath() + File.separator + "error.txt")).exists()
                        && !(new File(files[i].getPath() + File.separator + "finished.txt")).exists()) {
                    System.out.println("found incomplete batch_sampling: " + files[i].getPath())
                    try {
                        addBatch(files[i].getPath() + File.separator)
                    } catch (InterruptedException ignored) {

                    }
                }
            }
        }
    }

    static void addBatch(String batchDir) throws InterruptedException {
        if (!waitingBatchDirs.contains(batchDir)) {
            waitingBatchDirs.put(batchDir)
        }
    }

    static void end() {
        for (Thread t : threads) {
            t.interrupt()
        }
    }
}

//@CompileStatic


//@CompileStatic
