package au.org.ala.spatial


import au.org.ala.userdetails.UserDetailsFromIdListResponse
import au.org.ala.web.AuthService
import au.org.ala.web.UserDetails
import grails.gorm.transactions.Transactional
import groovy.sql.GroovyResultSet

@Transactional
//@CompileStatic
class ReportService {
    AuthService authService
    SpatialConfig spatialConfig

    //Generate User usage report
    def report() {
        String sql = "select userId, CONCAT(category1, '->', category2) as name, count(*) as count " +
                        "FROM Log " +
                        "GROUP BY CONCAT(category1, '->', category2), userId " +
                        "ORDER BY userId"
        List<GroovyResultSet> results = Log.executeQuery(sql)

        String userSql = "SELECT userId FROM Log WHERE userId !='null' AND userId !='-1' AND userId!='0' GROUP BY userId ORDER BY count(userId) DESC"
        //Order by frequency of user usage
        List<GroovyResultSet> userIds = Task.executeQuery(userSql)

        //Git a list of userId
        UserDetailsFromIdListResponse usersInfoResp = ((Optional<UserDetailsFromIdListResponse>) authService.getUserDetailsById(userIds))?.get()
        Map users = [:]
        if (usersInfoResp?.success) {
            users = usersInfoResp.users
        }


        List<Map> userReports = []
        List headers = ['userId', 'name', 'primaryRole', 'secondaryRole', 'email', 'org']

        //tranvertix
        //Result[0]: userId
        //Result[1]: Task
        //Result[2]: Count
        List taskHeaders = []
        for (GroovyResultSet result in (results as List<GroovyResultSet>)) {
            String userId = result[0]
            Map userReport = userReports.find { Map it -> it['userId'] == userId }
            if (userReport) {
                userReport[result[1]] = result[2]
            } else {
                userReport = [:]
                userReport['userId'] = userId

                UserDetails theUser = users.get(userId)
                if (theUser) {
                    userReport['name'] = theUser?.firstName + ' ' + theUser?.lastName
                    userReport['primaryRole'] = theUser?.props?.primaryUserType?.replaceAll(',', ' ')
                    userReport['secondaryRole'] = theUser?.props?.secondaryUserType?.replaceAll(',', ' ')
                    userReport['email'] = theUser?.userName?.replaceAll(',', ' ')
                    userReport['org'] = theUser?.props?.organisation?.replaceAll(',', ' ')
                }
                userReport[result[1]] = result[2]
                userReports.push(userReport)

            }
            taskHeaders.push(result[1])
        }

        //init Unique Task name
        headers += taskHeaders.unique().sort()
        int columns = headers.size()

        String[][] csvReport = new String[userReports.size() + 1][columns]
        csvReport[0] = headers

        for (int i = 0; i < userIds.size(); i++) {
            Map report = userReports.find { it.userId == userIds[i] }
            for (int j = 0; j < headers.size(); j++) {
                csvReport[i + 1][j] = report.getOrDefault(headers[j], 0)
            }
        }
        return csvReport
    }

    //Generate Task based on report
    def taskBasedReport(includeAll) {
        def excludedUsers = spatialConfig.reporting.excludedUsers

        String sql = "SELECT CONCAT(category1, '->', category2) as name, count(*) as count,extract(YEAR from created) AS year,  extract(MONTH from created) AS month  " +
                "FROM Log "
        //Exclude ALA internal users
        if (!includeAll) {
            String idSql = "'" + excludedUsers.join("','") + "'"
            sql += " WHERE user_id NOT IN (" + idSql + ")"
        }

        sql += "GROUP BY CONCAT(category1, '->', category2), extract(YEAR from created),  extract(MONTH from created)  " +
                "ORDER BY CONCAT(category1, '->', category2), extract(YEAR from created),  extract(MONTH from created)  DESC"

        List results = Log.executeQuery(sql)
        //init Unique Task name
        List headers = ['task', 'count', 'year', 'month']
        results.add(0, headers)
        return results
    }
}
