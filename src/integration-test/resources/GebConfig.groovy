/**
 *
 * ./gradlew integrationTest -DbaseUrl="https://spatial-test.ala.org.au" --tests="AddFacetSpec" -Dusername=xxxx -Dpassword=xxxxx
 *
 * Username and password can be stored in default config file: /data/spatial-hub/test/default.properties
 * We can also point to another property file by passing --DconfigFile=xxxxxx
 *
 */

import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxDriver

waiting {
    timeout = 2
}

if (!System.getProperty("webdriver.chrome.driver")) {
    System.setProperty("webdriver.chrome.driver", "node_modules/chromedriver/bin/chromedriver")
}

environments {

    //  ./gradlew :integrationTest -Ddriver=chrome
    // See: http://code.google.com/p/selenium/wiki/ChromeDriver
    chrome {
        driver = {
            def chrome = new ChromeDriver()
            chrome.manage().window().fullscreen()
            chrome
        }
    }

    chromeHeadless {
        driver = {
            ChromeOptions o = new ChromeOptions()
            o.addArguments('headless')
            def chrome = new ChromeDriver(o)
            chrome.manage().window().fullscreen()
            chrome
        }
    }

    // run via ./gradlew :integrationTest
    firefox {
        atCheckWaiting = 1
        driver = {
            def firefox = new FirefoxDriver()
            firefox.manage().window().fullscreen() //run full screen
            firefox
        }
    }
}



