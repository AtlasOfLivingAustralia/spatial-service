package au.org.ala.spatial

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate

class LogServiceSpec extends Specification {

    LogService service = new LogService(category1: ['Create', 'Tool'], category2: ['Area', 'Species'])

    static final String ALWAYS = "session_id IS NOT NULL AND category1 IS NOT NULL AND category1 <> 'httpService'"

    @Unroll
    void "users only see their own logs (admin: #isAdmin, admin param: #adminParam)"() {
        expect:
        service.where([admin: adminParam], 'u1', isAdmin) == "WHERE user_id = 'u1' AND ${ALWAYS}"

        where:
        isAdmin | adminParam
        false   | null
        false   | 'true'
        true    | null
        true    | 'false'
    }

    void "admins can ask for everybody's logs"() {
        expect:
        service.where([admin: 'true'], 'u1', true) == "WHERE ${ALWAYS}"
    }

    void "known categories are used as filters"() {
        expect:
        service.where([category1: 'Tool', category2: 'Area'], 'u1', false) ==
                "WHERE user_id = 'u1' AND category1 = 'Tool' AND category2 = 'Area' AND ${ALWAYS}"
    }

    void "unknown categories are ignored"() {
        expect:
        service.where([category1: "x' OR '1'='1", category2: 'Unknown'], 'u1', false) ==
                "WHERE user_id = 'u1' AND ${ALWAYS}"
    }

    void "a numeric session id is used as a filter"() {
        expect:
        service.where([sessionId: '123'], 'u1', false) == "WHERE user_id = 'u1' AND session_id = '123' AND ${ALWAYS}"
    }

    void "a date range is used as a filter"() {
        expect:
        service.where([startDate: '2026-01-01', endDate: '2026-02-01'], 'u1', false) ==
                "WHERE user_id = 'u1' AND created between '2026-01-01' and '2026-02-01' AND ${ALWAYS}"
    }

    void "the end date can be now"() {
        expect:
        service.where([startDate: '2026-01-01', endDate: 'now'], 'u1', false) ==
                "WHERE user_id = 'u1' AND created between '2026-01-01' and '${LocalDate.now()}' AND ${ALWAYS}"
    }

    @Unroll
    void "columnFormat('#column') is '#expected'"() {
        expect:
        service.columnFormat(column) == expected

        where:
        column      | expected
        'sessionId' | 'session_id'
        'userId'    | 'user_id'
        'category1' | 'category1'
        null        | null
    }
}
