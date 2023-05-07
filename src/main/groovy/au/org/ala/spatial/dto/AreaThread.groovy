package au.org.ala.spatial.dto

import au.org.ala.spatial.util.SpatialUtils
import groovy.util.logging.Slf4j

import java.sql.Statement
import java.util.concurrent.ConcurrentLinkedQueue

@Slf4j
class AreaThread extends Thread {

    Statement s
    ConcurrentLinkedQueue<String[]> queue

    AreaThread(ConcurrentLinkedQueue<String[]> queue, Statement s) {
        this.s = s
        this.queue = queue
    }

    @Override
    void run() {
        try {
            String[] data
            while ((data = queue.poll()) != null) {
                double area = SpatialUtils.calculateArea(data[2])

                String sql = "UPDATE tabulation SET area = " + area + " WHERE pid1='" + data[0] + "' AND pid2='" + data[1] + "';"

                int update = s.executeUpdate(sql)
            }
            s.close()
        } catch (Exception e) {
            log.error(e.getMessage(), e)
        }
    }
}
