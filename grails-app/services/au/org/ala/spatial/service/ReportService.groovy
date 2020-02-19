package au.org.ala.spatial.service

import au.org.ala.web.UserDetails
import grails.gorm.transactions.Transactional

@Transactional
class ReportService {
    def authService

    //Generate User usage report
    def report(){
        String sql = "select userId, CONCAT(category1, '->', category2) as name, count(*) as count " +
                        "FROM Log " +
                        "GROUP BY CONCAT(category1, '->', category2), userId " +
                        "ORDER BY userId"
        List results = Log.executeQuery(sql)

        String userSql = "SELECT userId FROM Log WHERE userId !='null' AND userId !='-1' AND userId!='0' GROUP BY userId ORDER BY count(userId) DESC"
        //Order by frequency of user usage
        List userIds = Task.executeQuery(userSql)

        //Git a list of userId
        def usersInfoResp = authService.getUserDetailsById(userIds)
        Map users = [:]
        if(usersInfoResp.success){
            users = usersInfoResp.users
        }


        List userReports= []
        List headers = ['userId', 'name', 'primaryRole','secondaryRole', 'email','org']

        //tranvertix
        //Result[0]: userId
        //Result[1]: Task
        //Result[2]: Count
        List taskHeaders = []
        for(List result in results){
            String userId = result[0]
            Map userReport = userReports.find{ it.userId == userId}
            if ( userReport ){
                userReport[result[1]]= result[2]
            }else{
                userReport = [:]
                userReport['userId']= userId

                UserDetails theUser = users.get(userId)
                if(theUser) {
                    userReport['name'] = theUser?.firstName + ' ' + theUser?.lastName
                    userReport['primaryRole'] = theUser?.props?.primaryUserType?.replaceAll(',',' ')
                    userReport['secondaryRole'] = theUser?.props?.secondaryUserType?.replaceAll(',',' ')
                    userReport['email'] = theUser?.userName?.replaceAll(',',' ')
                    userReport['org'] = theUser?.props.organisation?.replaceAll(',',' ')
                }
                userReport[result[1]]= result[2]
                userReports.push(userReport)

            }
            taskHeaders.push(result[1])
        }

        //init Unique Task name
        headers += taskHeaders.unique().sort()
        int columns = headers.size()

        String[][] csvReport = new String[userReports.size()+1][columns]
        csvReport[0] = headers

        for(int i=0; i<userIds.size() ; i++){
            Map report = userReports.find{it.userId == userIds[i]}
            for(int j=0; j<headers.size();j++){
                csvReport[i+1][j] = report.getOrDefault(headers[j],0)
            }
        }
        return csvReport
    }

    //Generate Task based on report
    def taskBasedReport(){

        String[] idsInALA = "1901,92092,71707,1493,53099,34,9965,47075,27078,44,9200,82292,10549,9048,11359,61,35022,13,28057,19807,27583,27190,35,48604,47326,35774,8443,8516,46293,4228,89299,89300,28712".split(',').collect{ '\'' + it + '\''}
        String idSql = idsInALA.join(',')

        String sql = "SELECT CONCAT(category1, '->', category2) as name, count(*) as count, YEAR(created) AS year,  MONTH(created) AS month " +
                        "FROM Log " +
                        "WHERE user_id NOT IN ("+ idSql+") "+
                        "GROUP BY CONCAT(category1, '->', category2), YEAR(created), MONTH(created) " +
                        "ORDER BY CONCAT(category1, '->', category2), YEAR(created), MONTH(created) DESC"

        List results = Log.executeQuery(sql)

        //init Unique Task name
        List headers = ['task','count','year', 'month']

        results.add(0, headers)
        return results
    }
}
