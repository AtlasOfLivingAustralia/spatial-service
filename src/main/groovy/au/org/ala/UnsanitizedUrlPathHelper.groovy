package au.org.ala

import org.springframework.web.util.UrlPathHelper
import org.springframework.web.util.WebUtils

import javax.servlet.http.HttpServletRequest

class UnsanitizedUrlPathHelper extends UrlPathHelper {
    /**
     * Change UrlPathHelper.getRequestUri behaviour by preventing the removal of `//` from the URI.
     *
     * @param request current HTTP request
     * @return
     */
    @Override
    String getRequestUri(HttpServletRequest request) {
        String uri = (String) request.getAttribute(WebUtils.INCLUDE_REQUEST_URI_ATTRIBUTE)
        if (uri == null) {
            uri = request.getRequestURI()
        }
        uri = removeSemicolonContent(uri)
        return decodeRequestString(request, uri)
    }
}
