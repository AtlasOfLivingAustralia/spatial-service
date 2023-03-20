/**************************************************************************
 * Copyright (C) 2010 Atlas of Living Australia
 * All Rights Reserved.
 * <p>
 * The contents of this file are subject to the Mozilla Public
 * License Version 1.1 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of
 * the License at http://www.mozilla.org/MPL/
 * <p>
 * Software distributed under the License is distributed on an "AS
 * IS" basis, WITHOUT WARRANTY OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * rights and limitations under the License.
 ***************************************************************************/
package au.org.ala.spatial.grid

import groovy.util.logging.Slf4j

import java.util.concurrent.*

/**
 * @author Adam
 */
//@CompileStatic

@Slf4j
class GridCacheReader {

    ArrayList<GridGroup> groups

    GridCacheReader(String directory) {
        groups = new ArrayList<GridGroup>()

        if (directory != null) {
            File dir = new File(directory)
            if (dir != null && dir.exists() && dir.isDirectory()) {
                for (File f : dir.listFiles()) {
                    try {
                        if (f.getName().endsWith(".txt")) {
                            GridGroup g = new GridGroup(f.getPath())
                            groups.add(g)
                        }
                    } catch (Exception e) {
                        log.error(e.getMessage(), e)
                    }
                }
            }
        }
    }


    static long largerTest(int size, String diva_cache_path) {
        try {
            ArrayList<Double> points = new ArrayList<Double>(2000)
            Random r = new Random(System.currentTimeMillis())
            for (int i = 0; i < size; i++) {
                points.add(r.nextDouble() * 40 + 110) //longitude
                points.add(r.nextDouble() * 30 - 40) //latitude
            }

            int threadCount = 100
            final LinkedBlockingQueue<GridCacheReader> lbqReaders = new LinkedBlockingQueue<GridCacheReader>()
            final LinkedBlockingQueue<List<Double>> lbqPoints = new LinkedBlockingQueue<List<Double>>()
            Collection<Callable<ArrayList<HashMap<String, Float>>>> tasks = new ArrayList<Callable<ArrayList<HashMap<String, Float>>>>()

            int pos = 0
            int step =  (int)(points.size() / threadCount)
            if (step % 2 == 1) {
                step--
            }
            for (int i = 0; i < threadCount; i++) {
                lbqReaders.add(new GridCacheReader(diva_cache_path))
                if (i == threadCount - 1) {
                    step = points.size()
                }
                lbqPoints.add(points.subList(pos, Math.min(points.size(), pos + step)))
                pos += step

                tasks.add(new Callable<ArrayList<HashMap<String, Float>>>() {

                    ArrayList<HashMap<String, Float>> call() throws Exception {
                        GridCacheReader gcr = lbqReaders.take()
                        List<Double> points_ = lbqPoints.take()

                        ArrayList<HashMap<String, Float>> list = new ArrayList<HashMap<String, Float>>()

                        for (int j = 0; j < points_.size(); j += 2) {
                            HashMap<String, Float> map = gcr.sample(points_.get(j), points_.get(j + 1))
                            map.put("longitude", points_.get(j).floatValue())
                            map.put("latitude", points_.get(j + 1).floatValue())
                            list.add(map)
                        }

                        return list
                    }
                })
            }

            log.info("starting...")
            long start = System.currentTimeMillis()

            ExecutorService executorService = Executors.newFixedThreadPool(threadCount)
            List<Future<ArrayList<HashMap<String, Float>>>> output = executorService.invokeAll(tasks)

            long end = System.currentTimeMillis() - start
            log.info("sampling time " + end + "ms for " + points.size() / 2)

            return end
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
        return 0
    }

    static ArrayList<Double> loadPoints(String filename) {
        ArrayList<Double> points = new ArrayList<Double>()
        BufferedReader br = null
        try {
            br = new BufferedReader(new FileReader(filename))
            String line
            br.readLine()
            while ((line = br.readLine()) != null) {
                String[] s = line.split(",")
                if (line.length() > 0 && s.length == 2) {
                    try {
                        double latitude = Double.parseDouble(s[0])
                        double longitude = Double.parseDouble(s[1])
                        points.add(longitude)
                        points.add(latitude)
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        } finally {
            if (br != null) {
                try {
                    br.close()
                } catch (Exception e) {
                    log.error(e.getMessage(), e)
                }
            }
        }
        return points
    }

    HashMap<String, Float> sample(double longitude, double latitude) throws IOException {
        HashMap<String, Float> map = new HashMap<String, Float>()
        for (GridGroup g : groups) {
            map.putAll(g.sample(longitude, latitude))
        }
        return map
    }

    ArrayList<String> getFileNames() {
        ArrayList<String> fileNames = new ArrayList<String>()
        for (int i = 0; i < groups.size(); i++) {
            fileNames.addAll(groups.get(i).files)
        }
        return fileNames
    }

    void updateNames(String fileName, String name) {
        for (int i = 0; i < groups.size(); i++) {
            ArrayList<String> files = groups.get(i).files
            for (int j = 0; j < files.size(); j++) {
                if (files.get(j) == fileName) {
                    groups.get(i).names.set(j, name)
                    return
                }
            }
        }
    }

    int getGroupCount() {
        return groups.size()
    }
}
