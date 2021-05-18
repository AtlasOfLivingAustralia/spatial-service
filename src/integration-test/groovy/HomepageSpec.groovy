import geb.spock.GebSpec
import page.SpatialServiceHomePage

class HomepageSpec extends GebSpec {
    int pause = 3000

//    def setup() {
//        when:
//        via SpatialServiceHomePage
//
//        if (title.startsWith("ALA | Login"))
//            authModule.login()
//
//        then:
//        Thread.sleep(pause)
//    }

    def "Spatial service is up"() {
        when:
        via SpatialServiceHomePage

        if (title.startsWith("ALA | Login"))
            authModule.login()

        then:
        waitFor 10, { $("h1").text() == "Spatial service"}

    }

  }