package au.org.ala.spatial


import au.org.ala.plugins.openapi.Path
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse

import javax.ws.rs.Produces

class ReportController {

    ReportService reportService

    @Operation(
            method = "GET",
            tags = "report",
            operationId = "getUserReport",
            summary = "Get a user report as CSV",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "Valid request",
                            responseCode = "200"
                    )
            ],
            security = []
    )
    @Path("/report")
    @Produces("text/csv")
    @RequireAdmin
    def index() {
        def outputStream = response.outputStream
        response.setContentType("application/text")
        response.setHeader("Content-disposition", "filename=\"user_based_report.csv\"")
        String[][] reports = reportService.report()
        try {
            for (String [] report in reports) {
                if (report[0])
                    outputStream << report.join(',') + "\r\n"
            }
        } catch (IOException ignored) {
            null
        } finally {
            outputStream.close()
        }
    }

    @Operation(
            method = "GET",
            tags = "report",
            operationId = "getTaskReport",
            summary = "Get a task report as CSV",
            parameters = [],
            responses = [
                    @ApiResponse(
                            description = "Valid request",
                            responseCode = "200"
                    )
            ],
            security = []
    )
    @Path("/report/task")
    @Produces("text/csv")
    @RequireAdmin
    def task() {
        boolean includeAll = false
        if (params.containsKey("includeAll") && !params.includeAll) {
            includeAll = true
        }
        def outputStream = response.outputStream
        response.setContentType("application/text")
        response.setHeader("Content-disposition", "filename=\"task_based_report.csv\"")
        String[][] reports = reportService.taskBasedReport(includeAll) as String[][]
        try {
            for (String [] report in reports) {
                if (report[0])
                    outputStream << report.join(',') + "\r\n"
            }
        } catch (IOException ignored) {
            //Discard possible error reports
            null
        } finally {
            outputStream.close()
        }
    }

}
