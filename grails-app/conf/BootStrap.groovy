class BootStrap {

    def monitorService
    def slaveService
    def grailsApplication

    def init = { servletContext ->
        if (grailsApplication.config.service.enable) {
            monitorService.init()
        }
        if (grailsApplication.config.slave.enable) {
            slaveService.monitor()
        }
    }
    def destroy = {
    }
}
