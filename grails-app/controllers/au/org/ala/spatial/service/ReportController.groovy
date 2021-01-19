package au.org.ala.spatial.service

class ReportController {
    def reportService

    def index(){
        def outputStream = response.outputStream
        response.setContentType("application/text")
        response.setHeader("Content-disposition", "filename=\"user_based_report.csv\"")
        String[][] reports =  reportService.report()
        try {
            for(List report in reports)
                outputStream << report.join(',') + "\r\n"
        } catch (IOException e){
            null
        } finally {
            if (outputStream != null){
                try {
                    outputStream.close()
                } catch (IOException e) {
                    null
                }
            }
        }

        //render taskService.report() as JSON
    }
    def task(){
        def outputStream = response.outputStream
        response.setContentType("application/text")
        response.setHeader("Content-disposition", "filename=\"task_based_report.csv\"")
        String[][] reports =  reportService.taskBasedReport()
        try {
            for(List report in reports)
                outputStream << report.join(',') + "\r\n"
        } catch (IOException e){
            null
        } finally {
            if (outputStream != null){
                try {
                    outputStream.close()
                } catch (IOException e) {
                    null
                }
            }
        }

        //render taskService.report() as JSON
    }

}
