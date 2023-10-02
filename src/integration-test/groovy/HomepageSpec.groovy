import geb.spock.GebSpec
import page.HomePage

/**
 *   ./gradlew :integrationTest -Ddriver=chrome
 *   ./gradlew :integrationTest -DbaseUrl=xxxx -Dusername=xxxx -Dpassword=xxxx
 */
class HomepageSpec extends GebSpec {
    int pause = 3000

    def "Spatial service is up"() {
        when:
        via HomePage

        if (title.startsWith("ALA | Login"))
            authModule.login()

        then:
        waitFor 10, { subTitle == "Spatial service" }

        when:
        clickLink("Background tasks")

        sleep(1000) //Assure redirection
        if (title.startsWith("ALA | Login"))
            authModule.login()

        then:
        waitFor 10, { subTitle.contains("Task List") }
        waitFor 10, { taskTable.displayed }
        numberOfTasks() > 1
    }
}
