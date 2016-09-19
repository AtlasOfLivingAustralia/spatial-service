package au.org.ala.spatial

import grails.converters.JSON
import org.apache.commons.httpclient.HttpClient
import org.apache.commons.httpclient.NameValuePair
import org.apache.commons.httpclient.methods.PostMethod
import org.apache.commons.io.IOUtils
import org.apache.http.HttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.DefaultHttpClient
import org.apache.http.params.HttpConnectionParams
import org.apache.http.params.HttpParams


class Util {

    public static String getUrl(String url) {
        DefaultHttpClient client = new DefaultHttpClient();

        HttpParams params = client.getParams();
        HttpConnectionParams.setConnectionTimeout(params, 10000)
        HttpConnectionParams.setSoTimeout(params, 600000)

        HttpGet get = new HttpGet(url)
        HttpResponse response = client.execute(get)
        String out = IOUtils.toString(response.getEntity().getContent())
        get.releaseConnection()

        out
    }

    public static String postUrl(String url, NameValuePair[] params) throws Exception {

        HttpClient client = new HttpClient();
        PostMethod post = new PostMethod(url);

        post.setRequestBody(params);

        int result = client.executeMethod(post);

        // Get the response
        if (result == 200) {
            return post.getResponseBodyAsString();
        }

        return null;
    }

    static def makeQid(query) {
        PostMethod post = new PostMethod(query.bs + "/webportal/params")

        if (query.q instanceof List) {
            post.addParameter('q', query.q[0])
            if (query.q.size() > 1) query.q.subList(1, query.q.size()).each { post.addParameter('fq', it) }
        } else {
            post.addParameter('q', query.q)
            if (query.fq) query.fq.each {
                if (it instanceof String)
                    post.addParameter('fq', it)
            }
        }

        if (query.wkt) post.addParameter('wkt', query.wkt)

        post.addParameter('bbox', 'true');

        HttpClient client = new HttpClient()
        client.setConnectionTimeout(10000)
        client.setTimeout(600000)
        client.executeMethod(post)

        post.getResponseBodyAsString()
    }

    static def getQid(bs, qid) {
        JSON.parse(getUrl(bs + '/webportal/params/details/' + qid))
    }
}
