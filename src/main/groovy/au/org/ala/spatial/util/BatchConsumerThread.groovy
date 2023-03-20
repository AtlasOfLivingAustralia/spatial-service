package au.org.ala.spatial.util

import au.org.ala.spatial.intersect.IntersectCallback
import au.org.ala.spatial.intersect.IntersectUtil
import au.org.ala.spatial.LayerIntersectService

import java.text.SimpleDateFormat
import java.util.concurrent.LinkedBlockingQueue

class BatchConsumerThread extends Thread {
    LinkedBlockingQueue<String> waitingBatchDirs
    LayerIntersectService layerIntersectDao
    String batchDir

    BatchConsumerThread(LinkedBlockingQueue<String> waitingBatchDirs, LayerIntersectService layerIntersectDao
                        , String batchDir) {
        this.waitingBatchDirs = waitingBatchDirs
        this.layerIntersectDao = layerIntersectDao
        this.batchDir = batchDir
    }

    @Override
    void run() {
        boolean repeat = true
        String id = ""

        while (repeat) {
            String currentBatch = null
            try {
                currentBatch = waitingBatchDirs.take()

                id = new File(currentBatch).getName()

                SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yy hh:mm:ss:SSS")
                String str = sdf.format(new Date())
                BatchProducer.logUpdateEntry(id, "started", "started", str, null)
                new File(currentBatch + "status.txt").append("started at " + str)
                new File(currentBatch + "started.txt").append(str)

                String fids = new File(currentBatch + "fids.txt").text
                String points = new File(currentBatch + "points.txt").text
                String gridcache = new File(currentBatch + "gridcache.txt").text

                ArrayList<String> sample = null

                HashMap[] pointSamples = null
                if ("1" == gridcache) {
                    pointSamples = layerIntersectDao.sampling(points, 1)
                } else if ("2" == gridcache) {
                    pointSamples = layerIntersectDao.sampling(points, 2)
                } else {
                    IntersectCallback callback = new ConsumerCallback(id)
                    sample = layerIntersectDao.sampling(fids.split(","), splitStringToDoublesArray(points, ',' as char), callback)
                }

                //convert pointSamples to string array
                if (pointSamples != null) {
                    Set columns = new LinkedHashSet()
                    for (int i = 0; i < pointSamples.length; i++) {
                        columns.addAll(pointSamples[i].keySet() as Iterable)
                    }

                    //fids
                    fids = ""
                    for (Object o : columns) {
                        if (!fids.isEmpty()) {
                            fids += ","
                        }
                        fids += o
                    }

                    //columns
                    ArrayList<StringBuilder> sb = new ArrayList<StringBuilder>()
                    for (int i = 0; i < columns.size(); i++) {
                        sb.add(new StringBuilder())
                    }
                    for (int i = 0; i < pointSamples.length; i++) {
                        int pos = 0
                        for (Object o : columns) {
                            sb.get(pos).append("\n").append(pointSamples[i].get(o))
                            pos++
                        }
                    }

                    //format
                    sample = new ArrayList<String>()
                    for (int i = 0; i < sb.size(); i++) {
                        sample.add(sb.get(i).toString())
                    }
                }

                System.out.println("start csv output at " + sdf.format(new Date()))
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(currentBatch + "sample.csv"))
                IntersectUtil.writeSampleToStream(splitString(fids, ',' as char), splitString(points, ',' as char), sample, bos)
                bos.flush()
                bos.close()
                System.out.println("finish csv output at " + sdf.format(new Date()))

                str = sdf.format(new Date())
                BatchProducer.logUpdateEntry(id, "finished", "finished", str, fids.split(",").length + 1)
                new File(currentBatch + "status.txt").append("finished at " + str)
                new File(currentBatch + "finished.txt").append(str)

            } catch (InterruptedException ignored) {
                //thread stop request
                repeat = false
                break
            } catch (Exception e) {
                if (currentBatch != null) {
                    try {
                        BatchProducer.logUpdateEntry(id, "error", "error", e.getMessage(), null)
                        new File(currentBatch + "status.txt").append("error " + e.getMessage())
                        new File(currentBatch + "error.txt").append(e.getMessage())

                    } catch (Exception ex) {
                        ex.printStackTrace()
                    }
                }
                e.printStackTrace()
            }

            currentBatch = null
        }
    }


    private static String[] splitString(String string, char delim) {
        //count
        int count = 1
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == delim) {
                count++
            }
        }

        String[] split = new String[count]
        int idx = 0

        //position of last comma
        int lastI = -1
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == delim) {
                //write this one, lastI to i
                if (lastI + 1 == i) {
                    split[idx] = ""
                } else {
                    split[idx] = string.substring(lastI + 1, i)
                }
                lastI = i
                idx++
            }
        }
        //last string
        if (lastI + 1 == string.length()) {
            split[idx] = ""
        } else {
            split[idx] = string.substring(lastI + 1)
        }

        return split
    }

    private static double[][] splitStringToDoublesArray(String string, char delim) {
        //count
        int count = 1
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == delim) {
                count++
            }
        }

        //parse points
        double[][] pointsD = new double[(int)((count + 1) / 2)][2]
        int idx = 0

        //position of last comma
        int lastI = -1
        for (int i = 0; i < string.length(); i++) {
            if (string.charAt(i) == delim) {
                //write this one, lastI to i
                if (lastI + 1 == i) {
                    pointsD[(int) (idx / 2)][idx % 2] = Double.NaN
                } else {
                    try {
                        pointsD[(int) (idx / 2)][(idx + 1) % 2] = Double.parseDouble(string.substring(lastI + 1, i))
                    } catch (Exception ignored) {
                        pointsD[(int) (idx / 2)][(idx + 1) % 2] = Double.NaN
                    }
                }
                lastI = i
                idx++
            }
        }
        //last string
        if (lastI + 1 == string.length()) {
            pointsD[((int) (idx + 1))][idx % 2] = Double.NaN
        } else {
            try {
                pointsD[((int) (idx / 2))][(idx + 1) % 2] = Double.parseDouble(string.substring(lastI + 1))
            } catch (Exception ignored) {
                pointsD[((int) (idx + 1))][(idx + 1) % 2] = Double.NaN
            }
        }

        return pointsD
    }

}
