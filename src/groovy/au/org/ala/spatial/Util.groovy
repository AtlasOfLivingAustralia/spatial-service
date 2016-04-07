package au.org.ala.spatial

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
}
