package au.org.ala.spatial.service

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration

import org.springframework.context.EnvironmentAware
import org.springframework.context.annotation.ComponentScan
import org.springframework.core.env.Environment
import org.springframework.core.env.PropertiesPropertySource

@ComponentScan(['au.org.ala.layers', 'au.org.ala.spatial']) // @ComponentScan required for fieldDao usage in BootStrap.groovy
class Application extends GrailsAutoConfiguration implements EnvironmentAware {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }

    @Override
    void doWithApplicationContext() {
        // Ensure that layers-store will use the correct context
        au.org.ala.layers.client.Client.setContext(applicationContext)
    }

    @Override
    void setEnvironment(Environment environment) {
        def envName = environment.getProperty("ENV_NAME")

        //set CAS appServerName from grails.serverURL when it is not defined
        if (!environment.getProperty("security.cas.appServerName")) {
            def serverURL = environment.getProperty("grails.serverURL")
            if (!serverURL){
                serverURL = "http://devt.ala.org.au"
                println("WARNING: Unable to retrieve 'grails.serverURL' - using "+ serverURL)
            }
            def url = new URL(serverURL)
            StringBuilder result = new StringBuilder()
            result.append(url.protocol)
            result.append(":")
            if (url.authority != null && url.authority.length() > 0) {
                result.append("//")
                result.append(url.authority)
            }

            // ala-cas-client in ala-auth:3.1.1 needs appServerName to exclude 'url.file'

            Properties properties = new Properties()
            properties.put('security.cas.appServerName', result.toString())
            environment.propertySources.addFirst(new PropertiesPropertySource(envName + "cas", properties))
        }
    }
}