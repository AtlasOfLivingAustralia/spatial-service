package au.org.ala.spatial.service

class ReportController {
    def reportService

    def index() {
        def outputStream = response.outputStream
        response.setContentType("application/text")
        response.setHeader("Content-disposition", "filename=\"user_based_report.csv\"")
        String[][] reports = reportService.report()
        try {
            for (List report in reports) {
                if (report[0])
                    outputStream << report.join(',') + "\r\n"
            }
        } catch (IOException e) {
            null
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (IOException e) {
                    null
                }
            }
        }

        //render taskService.report() as JSON
    }

    def task() {
        boolean includeAll = false
        if (params.containsKey("includeAll") && !params.includeAll) {
            includeAll = true
        }
        def outputStream = response.outputStream
        response.setContentType("application/text")
        response.setHeader("Content-disposition", "filename=\"task_based_report.csv\"")
        String[][] reports = reportService.taskBasedReport(includeAll)
        try {
            for (List report in reports) {
                if (report[0])
                    outputStream << report.join(',') + "\r\n"
            }
        } catch (IOException e) {
            //Discard possible error reports
            null
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close()
                } catch (IOException e) {
                    log.error("Unexpected IO error in task reporting.")
                    null
                }
            }
        }

        //render taskService.report() as JSON
    }

}
