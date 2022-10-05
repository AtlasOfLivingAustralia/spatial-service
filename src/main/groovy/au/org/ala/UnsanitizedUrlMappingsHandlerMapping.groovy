package au.org.ala

import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingInfo
import grails.web.mapping.UrlMappingsHolder
import org.grails.exceptions.ExceptionUtils
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.grails.web.servlet.mvc.GrailsWebRequest
import org.grails.web.util.GrailsApplicationAttributes
import org.grails.web.util.WebUtils
import org.springframework.util.Assert

import javax.servlet.http.HttpServletRequest

class UnsanitizedUrlMappingsHandlerMapping extends UrlMappingsHandlerMapping {

    def urlHelper = new UnsanitizedUrlPathHelper()

    UnsanitizedUrlMappingsHandlerMapping(UrlMappingsHolder urlMappingsHolder) {
        super(urlMappingsHolder)
    }

    /**
     * Change UrlMappingsHandlerMapping.getHandlerInternal to use `urlHelper = new UnsanitizedUrlPathHelper`.
     *
     * @param request current HTTP request
     * @return
     */
    @Override
    protected Object getHandlerInternal(HttpServletRequest request) throws Exception {

        def matchedInfo = request.getAttribute(MATCHED_REQUEST)
        def errorStatus = request.getAttribute(WebUtils.ERROR_STATUS_CODE_ATTRIBUTE)
        if(matchedInfo != null && errorStatus == null) return matchedInfo

        String uri = urlHelper.getPathWithinApplication(request);
        def webRequest = GrailsWebRequest.lookup(request)

        Assert.notNull(webRequest, "HandlerMapping requires a Grails web request")

        String version = findRequestedVersion(webRequest)


        if(errorStatus && !WebUtils.isInclude(request)) {
            def exception = request.getAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE)
            UrlMappingInfo info
            if(exception instanceof Throwable) {
                exception = ExceptionUtils.getRootCause(exception)
                def exceptionSpecificMatch = urlMappingsHolder.matchStatusCode(errorStatus.toString().toInteger(), (Throwable) exception)
                if(exceptionSpecificMatch) {
                    info = exceptionSpecificMatch
                }
                else {
                    info = urlMappingsHolder.matchStatusCode(errorStatus.toString().toInteger())
                }
            }
            else {
                info = urlMappingsHolder.matchStatusCode(errorStatus.toString().toInteger())
            }

            request.setAttribute(MATCHED_REQUEST, info)
            return info
        }
        else {

            def infos = urlMappingsHolder.matchAll(uri, request.getMethod(), version != null ? version : UrlMapping.ANY_VERSION)

            for(UrlMappingInfo info in infos) {
                if(info) {
                    if(info.redirectInfo) return info

                    webRequest.resetParams()
                    info.configure(webRequest)
                    if(info instanceof GrailsControllerUrlMappingInfo) {
                        request.setAttribute(MATCHED_REQUEST, info)
                        request.setAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS, ((GrailsControllerUrlMappingInfo)info).controllerClass)
                        return info
                    }
                    else if(info.viewName || info.URI) {
                        return info
                    }
                }
            }

            return null
        }

    }

}
